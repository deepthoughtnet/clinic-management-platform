package com.deepthoughtnet.clinic.prescription.service;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionMedicineRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionRepository;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestEntity;
import com.deepthoughtnet.clinic.prescription.db.PrescriptionTestRepository;
import com.deepthoughtnet.clinic.prescription.service.model.MedicineType;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineCommand;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTemplateConfig;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestCommand;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionUpsertCommand;
import com.deepthoughtnet.clinic.prescription.service.model.Timing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PrescriptionService {
    private static final String ENTITY_TYPE = "PRESCRIPTION";

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionMedicineRepository medicineRepository;
    private final PrescriptionTestRepository testRepository;
    private final ConsultationService consultationService;
    private final PatientRepository patientRepository;
    private final TenantUserManagementService tenantUserManagementService;
    private final ClinicProfileService clinicProfileService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PrescriptionMedicineRepository medicineRepository,
            PrescriptionTestRepository testRepository,
            ConsultationService consultationService,
            PatientRepository patientRepository,
            TenantUserManagementService tenantUserManagementService,
            ClinicProfileService clinicProfileService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.prescriptionRepository = prescriptionRepository;
        this.medicineRepository = medicineRepository;
        this.testRepository = testRepository;
        this.consultationService = consultationService;
        this.patientRepository = patientRepository;
        this.tenantUserManagementService = tenantUserManagementService;
        this.clinicProfileService = clinicProfileService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PrescriptionRecord> list(UUID tenantId) {
        requireTenant(tenantId);
        return mapRecords(tenantId, prescriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionRecord> listByDoctor(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireId(doctorUserId, "doctorUserId");
        return mapRecords(tenantId, prescriptionRepository.findByTenantIdAndDoctorUserIdOrderByCreatedAtDesc(tenantId, doctorUserId));
    }

    @Transactional(readOnly = true)
    public Optional<PrescriptionRecord> findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return prescriptionRepository.findByTenantIdAndId(tenantId, id).map(entity -> toRecord(entity, tenantData(tenantId)));
    }

    @Transactional(readOnly = true)
    public Optional<PrescriptionRecord> findByConsultationId(UUID tenantId, UUID consultationId) {
        requireTenant(tenantId);
        requireId(consultationId, "consultationId");
        return prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId).map(entity -> toRecord(entity, tenantData(tenantId)));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionRecord> listByPatient(UUID tenantId, UUID patientId) {
        requireTenant(tenantId);
        requireId(patientId, "patientId");
        return mapRecords(tenantId, prescriptionRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId));
    }

    @Transactional
    public PrescriptionRecord createDraft(UUID tenantId, PrescriptionUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validate(command);
        ConsultationRecord consultation = consultationService.findById(tenantId, command.consultationId())
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        ensurePatientAndDoctorMatch(tenantId, command, consultation);
        if (prescriptionRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, command.consultationId()).isPresent()) {
            throw new IllegalArgumentException("Prescription already exists for this consultation");
        }

        PrescriptionEntity entity = PrescriptionEntity.create(
                tenantId,
                command.patientId(),
                command.doctorUserId(),
                command.consultationId(),
                command.appointmentId() != null ? command.appointmentId() : consultation.appointmentId(),
                generatePrescriptionNumber(tenantId)
        );
        applyCommand(entity, command);
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        replaceLines(tenantId, saved.getId(), command.medicines(), command.recommendedTests());
        audit(tenantId, saved, "prescription.created", actorAppUserId, "Created prescription draft");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord updateDraft(UUID tenantId, UUID id, PrescriptionUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        requireId(id, "id");
        validate(command);
        PrescriptionEntity entity = prescriptionRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        ConsultationRecord consultation = consultationService.findById(tenantId, command.consultationId())
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        ensurePatientAndDoctorMatch(tenantId, command, consultation);
        if (!isEditable(entity)) {
            PrescriptionEntity openCorrection = openCorrectionDraft(tenantId, entity.getId());
            if (openCorrection != null) {
                openCorrection.update(normalizeNullable(command.diagnosisSnapshot()), normalizeNullable(command.advice()), command.followUpDate());
                PrescriptionEntity saved = prescriptionRepository.save(openCorrection);
                replaceLines(tenantId, saved.getId(), command.medicines(), command.recommendedTests());
                audit(tenantId, saved, "prescription.updated", actorAppUserId, "Updated prescription correction draft");
                return toRecord(saved, tenantData(tenantId));
            }
            return createCorrectionVersion(tenantId, entity, command, actorAppUserId, "SAME_DAY_CORRECTION", "Correction after finalization");
        }
        entity.update(normalizeNullable(command.diagnosisSnapshot()), normalizeNullable(command.advice()), command.followUpDate());
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        replaceLines(tenantId, saved.getId(), command.medicines(), command.recommendedTests());
        audit(tenantId, saved, "prescription.updated", actorAppUserId, "Updated prescription draft");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord createCorrectionVersion(UUID tenantId, UUID id, PrescriptionUpsertCommand command, UUID actorAppUserId, String flowType, String correctionReason) {
        requireTenant(tenantId);
        PrescriptionEntity parent = findEntity(tenantId, id);
        return createCorrectionVersion(tenantId, parent, command, actorAppUserId, flowType, correctionReason);
    }

    @Transactional(readOnly = true)
    public List<PrescriptionRecord> history(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        return mapRecords(tenantId, prescriptionRepository.findByTenantIdAndConsultationIdOrderByVersionNumberAsc(tenantId, entity.getConsultationId()));
    }

    @Transactional
    public PrescriptionRecord finalizePrescription(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        if (!isEditable(entity)) {
            PrescriptionEntity openCorrection = openCorrectionDraft(tenantId, entity.getId());
            if (openCorrection == null) {
                throw new IllegalArgumentException("Finalized prescriptions are immutable; create a correction or follow-up version");
            }
            entity = openCorrection;
        }
        ensureEditable(entity);
        entity.finalizePrescription(actorAppUserId);
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        supersedeParentIfNeeded(tenantId, saved, actorAppUserId);
        audit(tenantId, saved, "prescription.finalized", actorAppUserId, "Finalized prescription");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord previewPrescription(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        if (!isEditable(entity)) {
            PrescriptionEntity openCorrection = openCorrectionDraft(tenantId, entity.getId());
            if (openCorrection == null) {
                throw new IllegalArgumentException("Finalized prescriptions are immutable; create a correction or follow-up version");
            }
            entity = openCorrection;
        }
        ensureEditable(entity);
        entity.preview();
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        audit(tenantId, saved, "prescription.previewed", actorAppUserId, "Previewed prescription");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord markPrinted(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        if (!isPrintable(entity)) {
            throw new IllegalArgumentException("Prescription cannot be printed in its current status");
        }
        entity.markPrinted();
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        audit(tenantId, saved, "prescription.printed", actorAppUserId, "Marked prescription printed");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord markSent(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        if (!isSendable(entity)) {
            throw new IllegalArgumentException("Prescription cannot be sent in its current status");
        }
        entity.markSent();
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        audit(tenantId, saved, "prescription.sent", actorAppUserId, "Marked prescription sent");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord cancel(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        if (entity.getStatus() != PrescriptionStatus.DRAFT && entity.getStatus() != PrescriptionStatus.PREVIEWED) {
            throw new IllegalArgumentException("Only editable prescriptions can be cancelled in this release");
        }
        entity.cancel();
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        audit(tenantId, saved, "prescription.cancelled", actorAppUserId, "Cancelled prescription");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionPdf generatePdf(UUID tenantId, UUID id, UUID actorAppUserId) {
        PrescriptionEntity entity = findEntity(tenantId, id);
        PrescriptionData data = tenantData(tenantId);
        audit(tenantId, entity, "prescription.pdf_generated", actorAppUserId, "Generated prescription PDF");
        return createPdf(data.tenantName(), entity, data, PrescriptionTemplateConfig.defaults(), false);
    }

    @Transactional
    public PrescriptionPdf generatePdf(UUID tenantId, UUID id, UUID actorAppUserId, PrescriptionTemplateConfig templateConfig) {
        PrescriptionEntity entity = findEntity(tenantId, id);
        PrescriptionData data = tenantData(tenantId);
        audit(tenantId, entity, "prescription.pdf_generated", actorAppUserId, "Generated branded prescription PDF");
        return createPdf(data.tenantName(), entity, data, templateConfig == null ? PrescriptionTemplateConfig.defaults() : templateConfig, false);
    }

    @Transactional(readOnly = true)
    public PrescriptionPdf generateTemplatePreviewPdf(UUID tenantId, UUID prescriptionId, UUID actorAppUserId, PrescriptionTemplateConfig templateConfig) {
        PrescriptionEntity entity = prescriptionId == null
                ? null
                : prescriptionRepository.findByTenantIdAndId(tenantId, prescriptionId).orElse(null);
        PrescriptionData data = tenantData(tenantId);
        if (entity != null) {
            return createPdf(data.tenantName(), entity, data, templateConfig == null ? PrescriptionTemplateConfig.defaults() : templateConfig, true);
        }
        return createSamplePdf(data.tenantName(), data, templateConfig == null ? PrescriptionTemplateConfig.defaults() : templateConfig);
    }

    private PrescriptionEntity findEntity(UUID tenantId, UUID id) {
        return prescriptionRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
    }

    private void applyCommand(PrescriptionEntity entity, PrescriptionUpsertCommand command) {
        entity.update(normalizeNullable(command.diagnosisSnapshot()), normalizeNullable(command.advice()), command.followUpDate());
    }

    private void replaceLines(UUID tenantId, UUID prescriptionId, List<PrescriptionMedicineCommand> medicines, List<PrescriptionTestCommand> tests) {
        medicineRepository.deleteByTenantIdAndPrescriptionId(tenantId, prescriptionId);
        testRepository.deleteByTenantIdAndPrescriptionId(tenantId, prescriptionId);
        if (medicines != null) {
            for (PrescriptionMedicineCommand line : medicines) {
                validateMedicine(line);
                medicineRepository.save(PrescriptionMedicineEntity.create(
                        tenantId,
                        prescriptionId,
                        line.medicineName().trim(),
                        line.medicineType(),
                        normalizeNullable(line.strength()),
                        normalizeNullable(line.dosage()),
                        normalizeNullable(line.frequency()),
                        normalizeNullable(line.duration()),
                        line.timing() == null ? Timing.ANYTIME : line.timing(),
                        normalizeNullable(line.instructions()),
                        line.sortOrder()
                ));
            }
        }
        if (tests != null) {
            for (PrescriptionTestCommand test : tests) {
                if (test == null || !StringUtils.hasText(test.testName())) {
                    throw new IllegalArgumentException("testName is required");
                }
                testRepository.save(PrescriptionTestEntity.create(
                        tenantId,
                        prescriptionId,
                        test.testName().trim(),
                        normalizeNullable(test.instructions()),
                        test.sortOrder()
                ));
            }
        }
    }

    private PrescriptionRecord toRecord(PrescriptionEntity entity, PrescriptionData data) {
        PatientEntity patient = data.patients().get(entity.getPatientId());
        TenantUserRecord doctor = data.users().get(entity.getDoctorUserId());
        return new PrescriptionRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                patient == null ? null : patient.getPatientNumber(),
                patient == null ? null : patient.getFirstName() + " " + patient.getLastName(),
                entity.getDoctorUserId(),
                doctor == null ? null : doctor.displayName(),
                entity.getConsultationId(),
                entity.getAppointmentId(),
                entity.getPrescriptionNumber(),
                entity.getVersionNumber(),
                entity.getParentPrescriptionId(),
                entity.getCorrectionReason(),
                entity.getFlowType(),
                entity.getCorrectedAt(),
                entity.getSupersededByPrescriptionId(),
                entity.getSupersededAt(),
                entity.getDiagnosisSnapshot(),
                entity.getAdvice(),
                entity.getFollowUpDate(),
                entity.getStatus(),
                entity.getFinalizedAt(),
                entity.getFinalizedByDoctorUserId(),
                entity.getPrintedAt(),
                entity.getSentAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                medicineRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(entity.getTenantId(), entity.getId())
                        .stream()
                        .map(line -> new PrescriptionMedicineRecord(
                                line.getMedicineName(),
                                line.getMedicineType(),
                                line.getStrength(),
                                line.getDosage(),
                                line.getFrequency(),
                                line.getDuration(),
                                line.getTiming(),
                                line.getInstructions(),
                                line.getSortOrder()
                        ))
                        .toList(),
                testRepository.findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(entity.getTenantId(), entity.getId())
                        .stream()
                        .map(test -> new PrescriptionTestRecord(test.getTestName(), test.getInstructions(), test.getSortOrder()))
                        .toList()
        );
    }

    private List<PrescriptionRecord> mapRecords(UUID tenantId, List<PrescriptionEntity> entities) {
        PrescriptionData data = tenantData(tenantId);
        return entities.stream().map(entity -> toRecord(entity, data)).toList();
    }

    private PrescriptionData tenantData(UUID tenantId) {
        Map<UUID, PatientEntity> patients = patientRepository.findByTenantIdAndIdIn(tenantId, patientIdsForTenant(tenantId))
                .stream()
                .collect(Collectors.toMap(PatientEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, TenantUserRecord> users = tenantUserManagementService.list(tenantId).stream()
                .collect(Collectors.toMap(TenantUserRecord::appUserId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        String clinicName = clinic == null ? null : clinic.clinicName();
        String displayName = clinic == null ? null : clinic.displayName();
        String address = clinic == null ? null : formatAddress(clinic);
        String phone = clinic == null ? null : clinic.phone();
        String email = clinic == null ? null : clinic.email();
        String tenantName = StringUtils.hasText(displayName) ? displayName : (StringUtils.hasText(clinicName) ? clinicName : "Clinic");
        return new PrescriptionData(patients, users, tenantName, clinicName, displayName, address, phone, email);
    }

    private List<UUID> patientIdsForTenant(UUID tenantId) {
        return prescriptionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(PrescriptionEntity::getPatientId)
                .distinct()
                .toList();
    }

    private void ensurePatientAndDoctorMatch(UUID tenantId, PrescriptionUpsertCommand command, ConsultationRecord consultation) {
        if (!consultation.patientId().equals(command.patientId())) {
            throw new IllegalArgumentException("Prescription patient must match consultation patient");
        }
        if (!consultation.doctorUserId().equals(command.doctorUserId())) {
            throw new IllegalArgumentException("Prescription doctor must match consultation doctor");
        }
        if (patientRepository.findByTenantIdAndId(tenantId, command.patientId()).isEmpty()) {
            throw new IllegalArgumentException("Patient not found for tenant");
        }
        TenantUserRecord doctor = tenantUserManagementService.list(tenantId).stream()
                .filter(record -> record.appUserId() != null && record.appUserId().equals(command.doctorUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found for tenant"));
        if (!"DOCTOR".equalsIgnoreCase(doctor.membershipRole())) {
            throw new IllegalArgumentException("Selected user is not a doctor");
        }
    }

    private PrescriptionRecord createCorrectionVersion(UUID tenantId, PrescriptionEntity parent, PrescriptionUpsertCommand command, UUID actorAppUserId, String flowType, String correctionReason) {
        if (parent.getStatus() == PrescriptionStatus.DRAFT || parent.getStatus() == PrescriptionStatus.PREVIEWED) {
            throw new IllegalArgumentException("Editable prescriptions do not need a correction version");
        }
        PrescriptionEntity openCorrection = openCorrectionDraft(tenantId, parent.getId());
        if (openCorrection != null) {
            openCorrection.update(normalizeNullable(command.diagnosisSnapshot()), normalizeNullable(command.advice()), command.followUpDate());
            PrescriptionEntity saved = prescriptionRepository.save(openCorrection);
            replaceLines(tenantId, saved.getId(), command.medicines(), command.recommendedTests());
            audit(tenantId, saved, "prescription.updated", actorAppUserId, "Updated prescription correction draft");
            return toRecord(saved, tenantData(tenantId));
        }
        parent.markCorrected();
        prescriptionRepository.save(parent);
        PrescriptionEntity entity = PrescriptionEntity.create(
                tenantId,
                command.patientId(),
                command.doctorUserId(),
                command.consultationId(),
                command.appointmentId() != null ? command.appointmentId() : parent.getAppointmentId(),
                generatePrescriptionNumber(tenantId)
        );
        int nextVersion = prescriptionRepository.findByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, parent.getConsultationId())
                .stream()
                .map(PrescriptionEntity::getVersionNumber)
                .filter(version -> version != null)
                .findFirst()
                .orElse(1) + 1;
        entity.makeCorrectionVersion(parent.getId(), nextVersion, normalizeNullable(correctionReason), normalizeNullable(flowType));
        applyCommand(entity, command);
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        replaceLines(tenantId, saved.getId(), command.medicines(), command.recommendedTests());
        audit(tenantId, parent, "prescription.corrected", actorAppUserId, "Marked prescription as corrected");
        audit(tenantId, saved, "prescription.version.created", actorAppUserId, "Created prescription correction version");
        return toRecord(saved, tenantData(tenantId));
    }

    private boolean isEditable(PrescriptionEntity entity) {
        return entity.getStatus() == PrescriptionStatus.DRAFT || entity.getStatus() == PrescriptionStatus.PREVIEWED;
    }

    private void ensureEditable(PrescriptionEntity entity) {
        if (!isEditable(entity)) {
            throw new IllegalArgumentException("Finalized prescriptions are immutable; create a correction or follow-up version");
        }
    }

    private boolean isPrintable(PrescriptionEntity entity) {
        return entity.getStatus() != PrescriptionStatus.CANCELLED
                && entity.getStatus() != PrescriptionStatus.CORRECTED
                && entity.getStatus() != PrescriptionStatus.SUPERSEDED;
    }

    private boolean isSendable(PrescriptionEntity entity) {
        return entity.getStatus() != PrescriptionStatus.CANCELLED
                && entity.getStatus() != PrescriptionStatus.CORRECTED
                && entity.getStatus() != PrescriptionStatus.SUPERSEDED;
    }

    private PrescriptionEntity openCorrectionDraft(UUID tenantId, UUID parentPrescriptionId) {
        return prescriptionRepository.findFirstByTenantIdAndParentPrescriptionIdOrderByVersionNumberDesc(tenantId, parentPrescriptionId)
                .filter(this::isEditable)
                .orElse(null);
    }

    private void supersedeParentIfNeeded(UUID tenantId, PrescriptionEntity child, UUID actorAppUserId) {
        UUID parentPrescriptionId = child.getParentPrescriptionId();
        if (parentPrescriptionId == null) {
            return;
        }
        PrescriptionEntity parent = prescriptionRepository.findByTenantIdAndId(tenantId, parentPrescriptionId).orElse(null);
        if (parent == null || parent.getStatus() == PrescriptionStatus.SUPERSEDED) {
            return;
        }
        parent.markSuperseded(child.getId());
        prescriptionRepository.save(parent);
        audit(tenantId, parent, "prescription.superseded", actorAppUserId, "Superseded by prescription correction");
    }

    private void validate(PrescriptionUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireId(command.patientId(), "patientId");
        requireId(command.doctorUserId(), "doctorUserId");
        requireId(command.consultationId(), "consultationId");
        if (command.medicines() == null || command.medicines().isEmpty()) {
            throw new IllegalArgumentException("At least one medicine is required");
        }
    }

    private void validateMedicine(PrescriptionMedicineCommand line) {
        if (line == null || !StringUtils.hasText(line.medicineName())) {
            throw new IllegalArgumentException("medicineName is required");
        }
        if (!StringUtils.hasText(line.dosage())) {
            throw new IllegalArgumentException("dosage is required");
        }
        if (!StringUtils.hasText(line.frequency())) {
            throw new IllegalArgumentException("frequency is required");
        }
        if (!StringUtils.hasText(line.duration())) {
            throw new IllegalArgumentException("duration is required");
        }
    }

    private void audit(UUID tenantId, PrescriptionEntity entity, String action, UUID actorAppUserId, String message) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                entity.getId(),
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                message,
                detailsJson(entity)
        ));
    }

    private String detailsJson(PrescriptionEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("patientId", entity.getPatientId());
        details.put("doctorUserId", entity.getDoctorUserId());
        details.put("consultationId", entity.getConsultationId());
        details.put("prescriptionNumber", entity.getPrescriptionNumber());
        details.put("versionNumber", entity.getVersionNumber());
        details.put("status", entity.getStatus());
        details.put("parentPrescriptionId", entity.getParentPrescriptionId());
        details.put("correctedAt", entity.getCorrectedAt());
        details.put("supersededByPrescriptionId", entity.getSupersededByPrescriptionId());
        details.put("supersededAt", entity.getSupersededAt());
        details.put("finalizedAt", entity.getFinalizedAt());
        details.put("printedAt", entity.getPrintedAt());
        details.put("sentAt", entity.getSentAt());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private String generatePrescriptionNumber(UUID tenantId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = "RX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            if (prescriptionRepository.findByTenantIdAndPrescriptionNumber(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique prescription number");
    }

    private PrescriptionPdf createPdf(String tenantName, PrescriptionEntity entity, PrescriptionData data, PrescriptionTemplateConfig template, boolean preview) {
        PrescriptionRecord record = toRecord(entity, data);
        ConsultationRecord consultation = consultationService.findById(entity.getTenantId(), entity.getConsultationId()).orElse(null);
        ConsultationRecord previousConsultation = consultationService.listByPatient(entity.getTenantId(), entity.getPatientId()).stream()
                .filter(item -> !item.id().equals(entity.getConsultationId()))
                .findFirst()
                .orElse(null);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 36;
                float y = page.getMediaBox().getHeight() - margin;
                float[] primary = parseRgb(template.primaryColor(), 15, 118, 110);
                float[] accent = parseRgb(template.accentColor(), 224, 242, 241);
                drawHeaderBand(content, page, primary);
                if (StringUtils.hasText(template.watermarkText())) {
                    writeLine(content, template.watermarkText(), 42, 170, 430, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                }
                y = drawPremiumHeader(content, page, margin, y, tenantName, data, record, template, primary);
                y = drawPatientInfoGrid(content, margin, y, 523, record, data, accent);
                y -= 6;
                y = drawSection(content, "History & Previous Records", margin, y, primary);
                y = writeWrapped(content, historySummary(record, data, previousConsultation), 8.8f, margin, y, 523);
                y -= 6;
                y = drawSection(content, "Visit Summary", margin, y, primary);
                y = writeWrapped(content, visitSummary(record, consultation), 8.8f, margin, y, 523);
                y -= 6;
                y = drawSection(content, "Prescription", margin, y, primary);
                y = drawMedicineTable(content, margin, y, 523, record.medicines(), primary, accent);
                y -= 4;
                if (!record.recommendedTests().isEmpty()) {
                    y = drawSection(content, "Investigations / Tests Recommended", margin, y, primary);
                    for (PrescriptionTestRecord test : record.recommendedTests()) {
                        y = writeWrapped(content, "• " + safe(test.testName()) + (test.instructions() == null ? "" : " - " + test.instructions()), 8.8f, margin, y, 523);
                    }
                }
                y -= 6;
                y = drawSection(content, "Advice & Follow-up", margin, y, primary);
                y = writeWrapped(content, "• " + (StringUtils.hasText(record.advice()) ? record.advice() : "No additional advice recorded."), 8.8f, margin, y, 523);
                if (record.followUpDate() != null) {
                    y = writeWrapped(content, "• Follow-up Date: " + record.followUpDate(), 8.8f, margin, y, 523);
                }
                y -= 30;
                writeLine(content, "__________________________", 9, 390, Math.max(y, 96), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                writeLine(content, signatureText(template, record), 9, 390, Math.max(y, 84), new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                if (template.showQrCode()) {
                    drawQrPlaceholder(content, 472, 70);
                }
                if (StringUtils.hasText(template.disclaimer())) {
                    writeWrapped(content, "Disclaimer: " + template.disclaimer(), 7, margin, 54, 420);
                }
                if (StringUtils.hasText(template.footerText())) {
                    writeLine(content, template.footerText(), 8, margin, 32, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                }
                writeLine(content, "Generated: " + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), 8, margin, 20, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            }
            document.save(output);
            return new PrescriptionPdf((preview ? "preview-" : "") + safeFilename(record.prescriptionNumber()) + ".pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate prescription PDF", ex);
        }
    }

    private PrescriptionPdf createSamplePdf(String tenantName, PrescriptionData data, PrescriptionTemplateConfig template) {
        PrescriptionEntity sample = PrescriptionEntity.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "RX-PREVIEW");
        sample.update("Sample diagnosis", "Hydration, rest, and follow-up if symptoms worsen.", LocalDate.now().plusDays(7));
        PrescriptionData emptyData = new PrescriptionData(Map.of(), Map.of(), data.tenantName(), data.clinicDisplayName(), data.clinicDisplayName(), data.clinicAddress(), data.clinicPhone(), data.clinicEmail());
        return createPdf(tenantName, sample, emptyData, template, true);
    }

    private void drawHeaderBand(PDPageContentStream content, PDPage page, float[] rgb) throws IOException {
        content.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        content.addRect(0, page.getMediaBox().getHeight() - 16, page.getMediaBox().getWidth(), 16);
        content.fill();
        content.setNonStrokingColor(0, 0, 0);
    }

    private void drawQrPlaceholder(PDPageContentStream content, float x, float y) throws IOException {
        content.setStrokingColor(0, 0, 0);
        content.addRect(x, y, 58, 58);
        content.stroke();
        writeLine(content, "QR", 16, x + 17, y + 23, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
    }

    private float[] parseRgb(String hex, int fallbackR, int fallbackG, int fallbackB) {
        if (!StringUtils.hasText(hex) || !hex.matches("^#[0-9A-Fa-f]{6}$")) {
            return new float[]{fallbackR / 255f, fallbackG / 255f, fallbackB / 255f};
        }
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    private float writeWrapped(PDPageContentStream content, String text, float fontSize, float x, float y, float maxWidth) throws IOException {
        if (!StringUtils.hasText(text)) {
            return y;
        }
        for (String line : wrap(text, 92)) {
            writeLine(content, line, fontSize, x, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            y -= fontSize + 3;
        }
        return y;
    }

    private void writeLine(PDPageContentStream content, String text, float fontSize, float x, float y, PDType1Font font) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text == null ? "" : text);
        content.endText();
    }

    private List<String> wrap(String text, int maxChars) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxChars) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String patientSummary(PrescriptionRecord record, PrescriptionData data) {
        PatientEntity patient = data.patients().get(record.patientId());
        if (patient == null) {
            return "Age/Gender/Mobile not available";
        }
        String age = patient.getAgeYears() == null ? "N/A" : String.valueOf(patient.getAgeYears());
        String gender = patient.getGender() == null ? "UNKNOWN" : patient.getGender().name();
        return "Age: " + age + " | Gender: " + gender + " | Mobile: " + safe(patient.getMobile());
    }

    private float drawPremiumHeader(PDPageContentStream content, PDPage page, float margin, float y, String tenantName, PrescriptionData data, PrescriptionRecord record, PrescriptionTemplateConfig template, float[] primary) throws IOException {
        float iconX = margin;
        float iconY = y - 38;
        content.setStrokingColor(primary[0], primary[1], primary[2]);
        content.addRect(iconX, iconY, 34, 34);
        content.stroke();
        writeLine(content, "+", 17, iconX + 12, iconY + 8, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        float textX = iconX + 42;
        writeLine(content, StringUtils.hasText(data.clinicDisplayName()) ? data.clinicDisplayName() : tenantName, 15, textX, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        y -= 14;
        writeLine(content, safe(record.doctorName()), 9.5f, textX, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        y -= 12;
        if (StringUtils.hasText(template.doctorSignatureText())) {
            writeLine(content, template.doctorSignatureText(), 8.8f, textX, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            y -= 12;
        }
        String contactLine = Stream.of(data.clinicPhone(), data.clinicEmail(), data.clinicAddress())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" | "));
        writeLine(content, contactLine, 8.5f, textX, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        y -= 8;
        content.setStrokingColor(primary[0], primary[1], primary[2]);
        content.moveTo(margin, y);
        content.lineTo(page.getMediaBox().getWidth() - margin, y);
        content.stroke();
        content.setStrokingColor(0, 0, 0);
        return y - 12;
    }

    private float drawPatientInfoGrid(PDPageContentStream content, float x, float y, float width, PrescriptionRecord record, PrescriptionData data, float[] accent) throws IOException {
        float rowHeight = 16;
        float[] col = new float[]{0.28f, 0.22f, 0.25f, 0.25f};
        String[] headers = new String[]{"Patient Name", "Age / Gender", "Patient ID", "Visit Date"};
        String[] values = new String[]{safe(record.patientName()), ageGender(record, data), safe(record.patientNumber()), OffsetDateTime.now().toLocalDate().toString()};
        float currentX = x;
        content.setNonStrokingColor(accent[0], accent[1], accent[2]);
        content.addRect(x, y - rowHeight, width, rowHeight);
        content.fill();
        content.setNonStrokingColor(0, 0, 0);
        for (int i = 0; i < headers.length; i++) {
            float cellWidth = width * col[i];
            content.addRect(currentX, y - rowHeight * 2, cellWidth, rowHeight * 2);
            content.stroke();
            writeLine(content, headers[i], 8.2f, currentX + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            writeLine(content, values[i], 8.3f, currentX + 4, y - 27, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            currentX += cellWidth;
        }
        return y - (rowHeight * 2) - 6;
    }

    private float drawSection(PDPageContentStream content, String title, float x, float y, float[] primary) throws IOException {
        content.setNonStrokingColor(primary[0], primary[1], primary[2]);
        content.addRect(x, y - 10, 523, 10);
        content.fill();
        content.setNonStrokingColor(1f, 1f, 1f);
        writeLine(content, title, 8.5f, x + 4, y - 8, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        content.setNonStrokingColor(0, 0, 0);
        return y - 15;
    }

    private float drawMedicineTable(PDPageContentStream content, float x, float y, float width, List<PrescriptionMedicineRecord> medicines, float[] primary, float[] accent) throws IOException {
        String[] headers = new String[]{"Medicine", "Dosage", "Instructions", "Duration"};
        float[] col = new float[]{0.27f, 0.15f, 0.43f, 0.15f};
        float rowHeight = 15;
        content.setNonStrokingColor(primary[0], primary[1], primary[2]);
        content.addRect(x, y - rowHeight, width, rowHeight);
        content.fill();
        content.setNonStrokingColor(1f, 1f, 1f);
        float cx = x;
        for (int i = 0; i < headers.length; i++) {
            writeLine(content, headers[i], 8.2f, cx + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            cx += width * col[i];
        }
        content.setNonStrokingColor(0, 0, 0);
        y -= rowHeight;
        if (medicines.isEmpty()) {
            content.addRect(x, y - rowHeight, width, rowHeight);
            content.stroke();
            writeLine(content, "No medicines prescribed.", 8.5f, x + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
            return y - rowHeight - 2;
        }
        for (int i = 0; i < medicines.size(); i++) {
            PrescriptionMedicineRecord medicine = medicines.get(i);
            if (i % 2 == 1) {
                content.setNonStrokingColor(accent[0], accent[1], accent[2]);
                content.addRect(x, y - rowHeight, width, rowHeight);
                content.fill();
                content.setNonStrokingColor(0, 0, 0);
            }
            content.addRect(x, y - rowHeight, width, rowHeight);
            content.stroke();
            String medicineName = Stream.of(safe(medicine.medicineName()), safe(medicine.strength())).filter(StringUtils::hasText).collect(Collectors.joining(" "));
            String instruction = Stream.of(safe(medicine.frequency()), safe(medicine.instructions()), medicine.timing() == null ? null : medicine.timing().name().replace('_', ' ')).filter(StringUtils::hasText).collect(Collectors.joining(" | "));
            cx = x;
            writeLine(content, medicineName, 8f, cx + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            cx += width * col[0];
            writeLine(content, safe(medicine.dosage()), 8f, cx + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            cx += width * col[1];
            writeLine(content, instruction, 8f, cx + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            cx += width * col[2];
            writeLine(content, safe(medicine.duration()), 8f, cx + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            y -= rowHeight;
        }
        return y - 2;
    }

    private String ageGender(PrescriptionRecord record, PrescriptionData data) {
        PatientEntity patient = data.patients().get(record.patientId());
        if (patient == null) {
            return "N/A";
        }
        String age = patient.getAgeYears() == null ? "N/A" : String.valueOf(patient.getAgeYears());
        String gender = patient.getGender() == null ? "UNKNOWN" : patient.getGender().name();
        return age + " / " + gender;
    }

    private String historySummary(PrescriptionRecord record, PrescriptionData data, ConsultationRecord previousConsultation) {
        PatientEntity patient = data.patients().get(record.patientId());
        String summary = Stream.of(
                previousConsultation == null ? null : "Last visit: " + previousConsultation.createdAt().toLocalDate(),
                safeLine("Known allergies", patient == null ? null : patient.getAllergies()),
                safeLine("Chronic conditions", patient == null ? null : patient.getExistingConditions()),
                safeLine("Uploaded reports summary", patient == null ? null : patient.getNotes()),
                safeLine("Previous diagnosis", previousConsultation == null ? null : previousConsultation.diagnosis()),
                safeLine("Previous medications", patient == null ? null : patient.getLongTermMedications())
        ).filter(StringUtils::hasText).collect(Collectors.joining(" | "));
        return StringUtils.hasText(summary) ? summary : "No significant previous records available.";
    }

    private String visitSummary(PrescriptionRecord record, ConsultationRecord consultation) {
        if (consultation == null) {
            return safeLine("Diagnosis", record.diagnosisSnapshot());
        }
        String vitals = Stream.of(
                consultation.bloodPressureSystolic() == null || consultation.bloodPressureDiastolic() == null ? null : "BP " + consultation.bloodPressureSystolic() + "/" + consultation.bloodPressureDiastolic(),
                consultation.pulseRate() == null ? null : "Pulse " + consultation.pulseRate(),
                consultation.temperature() == null ? null : "Temp " + consultation.temperature() + (consultation.temperatureUnit() == null ? "" : " " + consultation.temperatureUnit().name()),
                consultation.spo2() == null ? null : "SpO2 " + consultation.spo2() + "%",
                consultation.weightKg() == null ? null : "Weight " + consultation.weightKg() + "kg"
        ).filter(StringUtils::hasText).collect(Collectors.joining(", "));
        return Stream.of(
                safeLine("Chief complaint", consultation.chiefComplaints()),
                safeLine("Symptoms", consultation.symptoms()),
                safeLine("Diagnosis", consultation.diagnosis()),
                safeLine("Consultation notes", consultation.clinicalNotes()),
                StringUtils.hasText(vitals) ? "Vitals: " + vitals : null
        ).filter(StringUtils::hasText).collect(Collectors.joining(" | "));
    }

    private String signatureText(PrescriptionTemplateConfig template, PrescriptionRecord record) {
        return StringUtils.hasText(template.doctorSignatureText()) ? template.doctorSignatureText() : safe(record.doctorName());
    }

    private String safeLine(String label, String value) {
        return StringUtils.hasText(value) ? label + ": " + value.trim() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeFilename(String value) {
        return value == null ? "prescription" : value.replaceAll("[^a-zA-Z0-9-_]+", "-").replaceAll("-+", "-");
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireId(UUID id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String formatAddress(ClinicProfileRecord clinic) {
        if (clinic == null) {
            return null;
        }
        List<String> parts = java.util.stream.Stream.of(
                clinic.addressLine1(),
                clinic.addressLine2(),
                clinic.city(),
                clinic.state(),
                clinic.country(),
                clinic.postalCode()
        ).filter(StringUtils::hasText).toList();
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private record PrescriptionData(
            Map<UUID, PatientEntity> patients,
            Map<UUID, TenantUserRecord> users,
            String tenantName,
            String clinicName,
            String clinicDisplayNameValue,
            String clinicAddressValue,
            String clinicPhone,
            String clinicEmail
    ) {
        String clinicDisplayName() {
            return StringUtils.hasText(clinicDisplayNameValue) ? clinicDisplayNameValue : clinicName;
        }

        String clinicAddress() {
            return clinicAddressValue;
        }
    }
}
