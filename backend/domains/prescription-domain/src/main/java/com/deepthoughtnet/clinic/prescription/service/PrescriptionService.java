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
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.apache.pdfbox.pdmodel.font.PDFont;
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
        PrescriptionPdfViewModel vm = buildPdfViewModel(tenantName, record, consultation, previousConsultation, data, template);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            float margin = 42f;
            float contentWidth = PDRectangle.A4.getWidth() - (margin * 2);
            float[] primary = parseRgb(template.primaryColor(), 15, 118, 110);
            float[] accent = parseRgb(template.accentColor(), 224, 242, 241);
            PdfRenderState state = new PdfRenderState(document, margin, primary, accent);
            try {
                state.startPage();
                state.y = drawPremiumHeader(state, vm, template);
                state.y = drawPatientInfoGrid(state, vm, contentWidth);

                state.y = drawSectionHeader(state, "History & Previous Records", contentWidth);
                List<String> historyLines = vm.historyItems();
                if (historyLines.isEmpty()) {
                    historyLines = List.of("No significant previous records available.");
                }
                state.y = drawBullets(state, historyLines, contentWidth);

                state.y = drawSectionHeader(state, "Visit Summary", contentWidth);
                state.y = drawVisitSummary(state, vm, contentWidth);

                state.y = drawSectionHeader(state, "Prescription Medicines", contentWidth);
                state.y = drawMedicineTable(state, vm.medicines(), contentWidth);

                if (!vm.investigations().isEmpty()) {
                    state.y = drawSectionHeader(state, "Investigations / Tests Recommended", contentWidth);
                    state.y = drawBullets(state, vm.investigations(), contentWidth);
                }

                state.y = drawSectionHeader(state, "Advice & Follow-up", contentWidth);
                List<String> advice = vm.adviceItems();
                if (advice.isEmpty()) {
                    advice = List.of("No additional advice recorded.");
                }
                state.y = writeWrapped(state, "Advice:", 9.2f, margin, contentWidth, true);
                state.y = drawBullets(state, advice, contentWidth);
                if (StringUtils.hasText(vm.followUpDate())) {
                    state.y = writeWrapped(state, "Follow-up:", 9.2f, margin, contentWidth, true);
                    state.y = drawBullets(state, List.of(vm.followUpDate()), contentWidth);
                }
                if (!vm.emergencyWarnings().isEmpty()) {
                    state.y = writeWrapped(state, "Emergency warning:", 9.2f, margin, contentWidth, true);
                    state.y = drawBullets(state, vm.emergencyWarnings(), contentWidth);
                }

                drawFooter(state, contentWidth, template, vm.signatureLine(), vm.doctorName());
            } finally {
                state.close();
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

    private float[] parseRgb(String hex, int fallbackR, int fallbackG, int fallbackB) {
        if (!StringUtils.hasText(hex) || !hex.matches("^#[0-9A-Fa-f]{6}$")) {
            return new float[]{fallbackR / 255f, fallbackG / 255f, fallbackB / 255f};
        }
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    private float writeWrapped(PdfRenderState state, String text, float fontSize, float x, float maxWidth, boolean bold) throws IOException {
        if (!StringUtils.hasText(text)) {
            return state.y;
        }
        PDFont font = bold
                ? new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
                : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (String line : wrap(text, font, fontSize, maxWidth)) {
            state.ensureSpace(fontSize + 4);
            writeLine(state.content, line, fontSize, x, state.y, font);
            state.y -= fontSize + 3;
        }
        return state.y;
    }

    private void writeLine(PDPageContentStream content, String text, float fontSize, float x, float y, PDFont font) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text == null ? "" : text);
        content.endText();
    }

    private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (textWidth(font, fontSize, word) > maxWidth) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.addAll(splitLongWord(word, font, fontSize, maxWidth));
                continue;
            }
            if (current.length() == 0) {
                current.append(word);
            } else if (textWidth(font, fontSize, current + " " + word) <= maxWidth) {
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

    private List<String> splitLongWord(String word, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            current.append(word.charAt(i));
            if (textWidth(font, fontSize, current.toString()) > maxWidth) {
                if (current.length() == 1) {
                    pieces.add(current.toString());
                    current.setLength(0);
                } else {
                    char last = current.charAt(current.length() - 1);
                    current.setLength(current.length() - 1);
                    pieces.add(current.toString());
                    current.setLength(0);
                    current.append(last);
                }
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }
    private float drawPremiumHeader(PdfRenderState state, PrescriptionPdfViewModel vm, PrescriptionTemplateConfig template) throws IOException {
        state.ensureSpace(108);
        PDPageContentStream content = state.content;
        float y = state.y;
        float x = state.margin;
        content.setStrokingColor(state.primary[0], state.primary[1], state.primary[2]);
        content.addRect(x, y - 36, 36, 36);
        content.stroke();
        writeLine(content, "+", 16, x + 13, y - 24, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        float textX = x + 48;
        writeLine(content, cleanText(vm.clinicName()), 17, textX, y - 2, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        y -= 18;
        String doctorName = cleanText(vm.doctorName());
        String doctorLine = StringUtils.hasText(doctorName) ? "Dr. " + doctorName : "Doctor";
        writeLine(content, doctorLine, 10.6f, textX, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        y -= 12;
        String signature = cleanText(template.doctorSignatureText());
        if (StringUtils.hasText(signature)) {
            writeLine(content, signature, 9f, textX, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            y -= 10;
        }
        String contactLine = Stream.of(cleanText(vm.clinicPhone()), cleanText(vm.clinicEmail()), cleanText(vm.clinicAddress()))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" | "));
        if (StringUtils.hasText(contactLine)) {
            state.y = y;
            y = writeWrapped(state, contactLine, 8.8f, textX, 460, false);
        }
        y -= 6;
        content.setStrokingColor(state.primary[0], state.primary[1], state.primary[2]);
        content.moveTo(state.margin, y);
        content.lineTo(state.page.getMediaBox().getWidth() - state.margin, y);
        content.stroke();
        content.setStrokingColor(0, 0, 0);
        return y - 14;
    }

    private float drawPatientInfoGrid(PdfRenderState state, PrescriptionPdfViewModel vm, float width) throws IOException {
        state.ensureSpace(102);
        float y = state.y;
        float headerHeight = 15f;
        float[] col = new float[]{0.28f, 0.22f, 0.24f, 0.26f};
        String[] headers = new String[]{"Patient Name", "Age / Gender", "Patient ID", "Visit Date"};
        String[] values = new String[]{
                cleanText(vm.patientName()),
                cleanText(vm.patientAgeGender()),
                cleanText(vm.patientId()),
                cleanText(vm.visitDate())
        };
        PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        List<List<String>> valueLines = new ArrayList<>();
        int maxValueLines = 1;
        for (int i = 0; i < values.length; i++) {
            List<String> wrapped = wrap(cleanText(values[i]), bodyFont, 8.8f, (width * col[i]) - 10);
            if (wrapped.isEmpty()) {
                wrapped = List.of("");
            }
            valueLines.add(wrapped);
            maxValueLines = Math.max(maxValueLines, wrapped.size());
        }
        float valueHeight = Math.max(18f, 8f + (maxValueLines * 10f));
        float totalRowHeight = headerHeight + valueHeight;

        state.content.setNonStrokingColor(state.accent[0], state.accent[1], state.accent[2]);
        state.content.addRect(state.margin, y - headerHeight, width, headerHeight);
        state.content.fill();
        state.content.setNonStrokingColor(0, 0, 0);
        float cx = state.margin;
        for (int i = 0; i < headers.length; i++) {
            float cell = width * col[i];
            state.content.addRect(cx, y - totalRowHeight, cell, totalRowHeight);
            state.content.stroke();
            writeLine(state.content, headers[i], 8.8f, cx + 5, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            float textY = y - headerHeight - 10;
            for (String line : valueLines.get(i)) {
                writeLine(state.content, line, 8.8f, cx + 5, textY, bodyFont);
                textY -= 10f;
            }
            cx += cell;
        }
        y -= totalRowHeight + 6;
        state.y = y;

        String[] moreHeaders = new String[]{"Mobile", "Appointment ID", "Consultation ID", "Doctor"};
        String[] moreValues = new String[]{
                cleanText(vm.patientMobile()),
                cleanText(vm.appointmentId()),
                cleanText(vm.consultationId()),
                cleanText(vm.doctorName())
        };
        boolean hasSecondaryRow = Stream.of(moreValues).anyMatch(StringUtils::hasText);
        if (!hasSecondaryRow) {
            return y - 4;
        }
        List<List<String>> moreLines = new ArrayList<>();
        int maxMoreLines = 1;
        for (int i = 0; i < moreValues.length; i++) {
            List<String> wrapped = wrap(cleanText(moreValues[i]), bodyFont, 8.8f, (width * col[i]) - 10);
            if (wrapped.isEmpty()) {
                wrapped = List.of("");
            }
            moreLines.add(wrapped);
            maxMoreLines = Math.max(maxMoreLines, wrapped.size());
        }
        float moreValueHeight = Math.max(18f, 8f + (maxMoreLines * 10f));
        float moreTotalHeight = headerHeight + moreValueHeight;
        state.ensureSpace(moreTotalHeight + 4);
        y = state.y;
        state.content.setNonStrokingColor(state.accent[0], state.accent[1], state.accent[2]);
        state.content.addRect(state.margin, y - headerHeight, width, headerHeight);
        state.content.fill();
        state.content.setNonStrokingColor(0, 0, 0);
        cx = state.margin;
        for (int i = 0; i < moreHeaders.length; i++) {
            float cell = width * col[i];
            state.content.addRect(cx, y - moreTotalHeight, cell, moreTotalHeight);
            state.content.stroke();
            writeLine(state.content, moreHeaders[i], 8.8f, cx + 5, y - 10, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            float textY = y - headerHeight - 10;
            for (String line : moreLines.get(i)) {
                writeLine(state.content, line, 8.8f, cx + 5, textY, bodyFont);
                textY -= 10f;
            }
            cx += cell;
        }
        state.y = y - moreTotalHeight - 12;
        return state.y;
    }

    private float drawSectionHeader(PdfRenderState state, String title, float width) throws IOException {
        state.y -= 10;
        state.ensureSpace(26);
        float y = state.y;
        state.content.setNonStrokingColor(state.primary[0], state.primary[1], state.primary[2]);
        state.content.addRect(state.margin, y - 14, width, 14);
        state.content.fill();
        state.content.setNonStrokingColor(1f, 1f, 1f);
        writeLine(state.content, title, 9.4f, state.margin + 6, y - 10.5f, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        state.content.setNonStrokingColor(0, 0, 0);
        state.y = y - 22;
        return state.y;
    }

    private float drawBullets(PdfRenderState state, List<String> lines, float width) throws IOException {
        for (String line : lines) {
            String text = cleanText(line);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            writeWrapped(state, "• " + text, 9f, state.margin + 10, width - 10, false);
        }
        state.y -= 3;
        return state.y;
    }

    private float drawVisitSummary(PdfRenderState state, PrescriptionPdfViewModel vm, float width) throws IOException {
        writeWrapped(state, "Chief Complaint:", 9.2f, state.margin, width, true);
        drawBullets(state, vm.chiefComplaint().isEmpty() ? List.of("Not recorded.") : vm.chiefComplaint(), width);
        writeWrapped(state, "Symptoms:", 9.2f, state.margin, width, true);
        drawBullets(state, vm.symptoms().isEmpty() ? List.of("Not recorded.") : vm.symptoms(), width);
        writeWrapped(state, "Vitals:", 9.2f, state.margin, width, true);
        drawBullets(state, vm.vitals().isEmpty() ? List.of("No vitals recorded.") : vm.vitals(), width);
        writeWrapped(state, "Diagnosis:", 9.2f, state.margin, width, true);
        drawBullets(state, vm.diagnoses().isEmpty() ? List.of("Not recorded.") : vm.diagnoses(), width);
        writeWrapped(state, "Clinical Notes:", 9.2f, state.margin, width, true);
        drawBullets(state, vm.clinicalNotes().isEmpty() ? List.of("No clinical notes recorded.") : vm.clinicalNotes(), width);
        state.y -= 4;
        return state.y;
    }

    private float drawMedicineTable(PdfRenderState state, List<PrescriptionMedicineRecord> medicines, float width) throws IOException {
        String[] headers = new String[]{"Medicine", "Dosage", "Frequency / Timing", "Instructions", "Duration"};
        float[] columns = new float[]{0.28f, 0.14f, 0.2f, 0.25f, 0.13f};
        float headerHeight = 18f;
        state.ensureSpace(headerHeight + 14);
        float y = state.y;

        drawMedicineTableHeader(state, headers, columns, width, y, headerHeight);
        y -= headerHeight;

        if (medicines.isEmpty()) {
            state.ensureSpace(18);
            state.content.addRect(state.margin, y - 16, width, 16);
            state.content.stroke();
            writeLine(state.content, "No medicines prescribed.", 8.8f, state.margin + 4, y - 11, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE));
            return y - 20;
        }

        for (int i = 0; i < medicines.size(); i++) {
            PrescriptionMedicineRecord medicine = medicines.get(i);
            List<List<String>> cells = List.of(
                    wrap(cleanText(mergeWithSpace(medicine.medicineName(), medicine.strength())), new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, width * columns[0] - 10),
                    wrap(cleanText(medicine.dosage()), new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, width * columns[1] - 10),
                    wrap(cleanText(mergeWithPipe(medicine.frequency(), medicine.timing() == null ? null : medicine.timing().name().replace('_', ' '))), new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, width * columns[2] - 10),
                    wrap(cleanText(medicine.instructions()), new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, width * columns[3] - 10),
                    wrap(cleanText(medicine.duration()), new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8.8f, width * columns[4] - 10)
            );
            int lines = cells.stream().mapToInt(List::size).max().orElse(1);
            float rowHeight = Math.max(20f, 8f + (lines * 10f));
            state.ensureSpace(rowHeight + 2);
            if (state.y != y) {
                y = state.y;
                drawMedicineTableHeader(state, headers, columns, width, y, headerHeight);
                y -= headerHeight;
            }

            if (i % 2 == 1) {
                state.content.setNonStrokingColor(state.accent[0], state.accent[1], state.accent[2]);
                state.content.addRect(state.margin, y - rowHeight, width, rowHeight);
                state.content.fill();
                state.content.setNonStrokingColor(0, 0, 0);
            }
            drawRowBorders(state, columns, width, y, rowHeight);
            float cx = state.margin;
            for (int col = 0; col < cells.size(); col++) {
                float textY = y - 12;
                for (String line : cells.get(col)) {
                    writeLine(state.content, line, 8.8f, cx + 5, textY, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                    textY -= 10f;
                }
                cx += width * columns[col];
            }
            y -= rowHeight;
            state.y = y;
        }
        return y - 4;
    }

    private void drawMedicineTableHeader(PdfRenderState state, String[] headers, float[] columns, float width, float y, float rowHeight) throws IOException {
        state.content.setNonStrokingColor(state.primary[0], state.primary[1], state.primary[2]);
        state.content.addRect(state.margin, y - rowHeight, width, rowHeight);
        state.content.fill();
        state.content.setNonStrokingColor(1f, 1f, 1f);
        float cx = state.margin;
        for (int i = 0; i < headers.length; i++) {
            writeLine(state.content, headers[i], 8.8f, cx + 5, y - 12, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            cx += width * columns[i];
        }
        state.content.setNonStrokingColor(0, 0, 0);
        drawRowBorders(state, columns, width, y, rowHeight);
        state.y = y - rowHeight;
    }

    private void drawRowBorders(PdfRenderState state, float[] columns, float width, float y, float rowHeight) throws IOException {
        state.content.addRect(state.margin, y - rowHeight, width, rowHeight);
        state.content.stroke();
        float cx = state.margin;
        for (int i = 0; i < columns.length - 1; i++) {
            cx += width * columns[i];
            state.content.moveTo(cx, y);
            state.content.lineTo(cx, y - rowHeight);
            state.content.stroke();
        }
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

    private String patientMobile(PrescriptionRecord record, PrescriptionData data) {
        PatientEntity patient = data.patients().get(record.patientId());
        return patient == null ? "" : cleanText(patient.getMobile());
    }

    private PrescriptionPdfViewModel buildPdfViewModel(
            String tenantName,
            PrescriptionRecord record,
            ConsultationRecord consultation,
            ConsultationRecord previousConsultation,
            PrescriptionData data,
            PrescriptionTemplateConfig template
    ) {
        List<String> investigations = record.recommendedTests().stream()
                .map(test -> mergeWithDash(cleanText(test.testName()), cleanText(test.instructions())))
                .filter(StringUtils::hasText)
                .toList();
        return new PrescriptionPdfViewModel(
                StringUtils.hasText(data.clinicDisplayName()) ? cleanText(data.clinicDisplayName()) : cleanText(tenantName),
                cleanText(data.clinicAddress()),
                cleanText(data.clinicPhone()),
                cleanText(data.clinicEmail()),
                cleanText(record.doctorName()),
                cleanText(record.patientName()),
                ageGender(record, data),
                cleanText(record.patientNumber()),
                consultation == null ? LocalDate.now().toString() : consultation.createdAt().toLocalDate().toString(),
                patientMobile(record, data),
                record.appointmentId() == null ? "" : trimId(record.appointmentId()),
                trimId(record.consultationId()),
                historySummaryLines(record, data, previousConsultation),
                splitToList(consultation == null ? null : consultation.chiefComplaints()),
                splitToList(consultation == null ? null : consultation.symptoms()),
                vitalsList(consultation),
                splitToList(consultation == null ? record.diagnosisSnapshot() : consultation.diagnosis()),
                splitToList(consultation == null ? null : consultation.clinicalNotes()),
                record.medicines(),
                investigations,
                splitToList(record.advice()),
                record.followUpDate() == null ? "" : record.followUpDate().toString(),
                List.of("Seek immediate care for severe dehydration, high fever, blood in vomit/stool, or severe abdominal pain."),
                signatureText(template, record)
        );
    }

    private List<String> historySummaryLines(PrescriptionRecord record, PrescriptionData data, ConsultationRecord previousConsultation) {
        PatientEntity patient = data.patients().get(record.patientId());
        List<String> lines = new ArrayList<>();
        if (previousConsultation != null && previousConsultation.createdAt() != null) {
            lines.add("Last visit: " + previousConsultation.createdAt().toLocalDate());
        }
        addLine(lines, "Referred by", previousConsultation == null ? null : previousConsultation.doctorName());
        addLine(lines, "Known allergies", patient == null ? null : patient.getAllergies());
        addLine(lines, "Chronic conditions", patient == null ? null : patient.getExistingConditions());
        addLine(lines, "Long-term medications", patient == null ? null : patient.getLongTermMedications());
        addLine(lines, "Uploaded reports summary", patient == null ? null : patient.getNotes());
        addLine(lines, "Previous diagnosis", previousConsultation == null ? null : previousConsultation.diagnosis());
        return lines;
    }

    private String signatureText(PrescriptionTemplateConfig template, PrescriptionRecord record) {
        return StringUtils.hasText(template.doctorSignatureText()) ? template.doctorSignatureText() : safe(record.doctorName());
    }

    private void drawFooter(PdfRenderState state, float width, PrescriptionTemplateConfig template, String signatureText, String doctorName) throws IOException {
        float footerY = 50f;
        float signatureX = state.margin + width - 170;
        state.content.moveTo(signatureX, footerY + 42);
        state.content.lineTo(signatureX + 140, footerY + 42);
        state.content.stroke();
        writeLine(state.content, "Doctor Signature", 8.8f, signatureX, footerY + 31, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        writeLine(state.content, cleanText(signatureText), 9.2f, signatureX, footerY + 20, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
        if (StringUtils.hasText(doctorName)) {
            writeLine(state.content, "Dr. " + doctorName, 8.8f, signatureX, footerY + 9, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        }

        if (template.showQrCode()) {
            drawQrPlaceholder(state.content, state.margin + width - 66, footerY + 2);
            writeLine(state.content, "Scan to verify", 7.2f, state.margin + width - 74, footerY - 8, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        }
        if (StringUtils.hasText(template.disclaimer())) {
            writeLine(state.content, "Disclaimer: " + cleanText(template.disclaimer()), 7.4f, state.margin, footerY + 8, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        }
        if (StringUtils.hasText(template.footerText())) {
            writeLine(state.content, cleanText(template.footerText()), 7.8f, state.margin, footerY - 2, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        }
        writeLine(state.content, "Generated: " + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " | Page " + state.pageNumber, 7.8f, state.margin, footerY - 13, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
    }

    private void drawQrPlaceholder(PDPageContentStream content, float x, float y) throws IOException {
        content.setStrokingColor(0, 0, 0);
        content.addRect(x, y, 54, 54);
        content.stroke();
        writeLine(content, "QR", 15, x + 16, y + 20, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
    }

    private float textWidth(PDFont font, float fontSize, String text) throws IOException {
        if (!StringUtils.hasText(text)) {
            return 0f;
        }
        return (font.getStringWidth(text) / 1000f) * fontSize;
    }

    private String trimId(UUID id) {
        if (id == null) {
            return "";
        }
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private List<String> splitToList(String value) {
        String cleaned = cleanText(value);
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }
        return Stream.of(cleaned.split("[\\n,;]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> vitalsList(ConsultationRecord consultation) {
        if (consultation == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (consultation.bloodPressureSystolic() != null && consultation.bloodPressureDiastolic() != null) {
            values.add("BP: " + consultation.bloodPressureSystolic() + "/" + consultation.bloodPressureDiastolic());
        }
        if (consultation.pulseRate() != null) {
            values.add("Pulse: " + consultation.pulseRate());
        }
        if (consultation.temperature() != null) {
            values.add("Temp: " + consultation.temperature() + (consultation.temperatureUnit() == null ? "" : " " + consultation.temperatureUnit().name()));
        }
        if (consultation.spo2() != null) {
            values.add("SpO2: " + consultation.spo2() + "%");
        }
        if (consultation.respiratoryRate() != null) {
            values.add("Resp: " + consultation.respiratoryRate());
        }
        if (consultation.weightKg() != null) {
            values.add("Weight: " + consultation.weightKg() + " kg");
        }
        return values;
    }

    private void addLine(List<String> lines, String label, String value) {
        String cleaned = cleanText(value);
        if (StringUtils.hasText(cleaned)) {
            lines.add(label + ": " + cleaned);
        }
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim().replaceAll("[\\r\\n\\t]+", " ");
        if ("unknown".equalsIgnoreCase(trimmed) || "n/a".equalsIgnoreCase(trimmed) || "-".equals(trimmed)) {
            return "";
        }
        if (looksStructured(trimmed)) {
            return summarizeStructured(trimmed);
        }
        return trimmed;
    }

    private boolean looksStructured(String value) {
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private String summarizeStructured(String value) {
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isTextual()) {
                return cleanText(node.asText());
            }
            if (node.isArray()) {
                List<String> entries = new ArrayList<>();
                for (JsonNode item : node) {
                    String summary = summarizeNode(item);
                    if (StringUtils.hasText(summary)) {
                        entries.add(summary);
                    }
                    if (entries.size() >= 5) {
                        break;
                    }
                }
                return String.join("; ", entries);
            }
            if (node.isObject()) {
                return summarizeNode(node);
            }
            return "";
        } catch (Exception ex) {
            return "";
        }
    }

    private String summarizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            String diagnosis = text(node, "diagnosis");
            String condition = text(node, "condition");
            String name = text(node, "name");
            String reason = text(node, "reason");
            String summary = text(node, "summary");
            return firstNonBlank(diagnosis, condition, name, reason, summary, "");
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return null;
        }
        String text = node.path(field).asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String mergeWithPipe(String a, String b) {
        return Stream.of(cleanText(a), cleanText(b))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" | "));
    }

    private String mergeWithDash(String a, String b) {
        return Stream.of(cleanText(a), cleanText(b))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" - "));
    }

    private String mergeWithSpace(String a, String b) {
        return Stream.of(cleanText(a), cleanText(b))
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
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

    private static final class PdfRenderState {
        private final PDDocument document;
        private final float margin;
        private final float[] primary;
        private final float[] accent;
        private PDPage page;
        private PDPageContentStream content;
        private float y;
        private int pageNumber = 0;

        private PdfRenderState(PDDocument document, float margin, float[] primary, float[] accent) {
            this.document = document;
            this.margin = margin;
            this.primary = primary;
            this.accent = accent;
        }

        private void startPage() throws IOException {
            closeContent();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            pageNumber += 1;
            content.setNonStrokingColor(primary[0], primary[1], primary[2]);
            content.addRect(0, page.getMediaBox().getHeight() - 16, page.getMediaBox().getWidth(), 16);
            content.fill();
            content.setNonStrokingColor(0, 0, 0);
            y = page.getMediaBox().getHeight() - margin;
        }

        private void ensureSpace(float neededHeight) throws IOException {
            if (y - neededHeight < 160) {
                startPage();
            }
        }

        private void close() throws IOException {
            closeContent();
        }

        private void closeContent() throws IOException {
            if (content != null) {
                content.close();
                content = null;
            }
        }
    }

    private record PrescriptionPdfViewModel(
            String clinicName,
            String clinicAddress,
            String clinicPhone,
            String clinicEmail,
            String doctorName,
            String patientName,
            String patientAgeGender,
            String patientId,
            String visitDate,
            String patientMobile,
            String appointmentId,
            String consultationId,
            List<String> historyItems,
            List<String> chiefComplaint,
            List<String> symptoms,
            List<String> vitals,
            List<String> diagnoses,
            List<String> clinicalNotes,
            List<PrescriptionMedicineRecord> medicines,
            List<String> investigations,
            List<String> adviceItems,
            String followUpDate,
            List<String> emergencyWarnings,
            String signatureLine
    ) {
    }
}
