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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    public Optional<PrescriptionRecord> findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        requireId(id, "id");
        return prescriptionRepository.findByTenantIdAndId(tenantId, id).map(entity -> toRecord(entity, tenantData(tenantId)));
    }

    @Transactional(readOnly = true)
    public Optional<PrescriptionRecord> findByConsultationId(UUID tenantId, UUID consultationId) {
        requireTenant(tenantId);
        requireId(consultationId, "consultationId");
        return prescriptionRepository.findByTenantIdAndConsultationId(tenantId, consultationId).map(entity -> toRecord(entity, tenantData(tenantId)));
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
        if (prescriptionRepository.findByTenantIdAndConsultationId(tenantId, command.consultationId()).isPresent()) {
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
        ensureDraft(entity);
        ConsultationRecord consultation = consultationService.findById(tenantId, command.consultationId())
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
        ensurePatientAndDoctorMatch(tenantId, command, consultation);
        entity.update(normalizeNullable(command.diagnosisSnapshot()), normalizeNullable(command.advice()), command.followUpDate());
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        replaceLines(tenantId, saved.getId(), command.medicines(), command.recommendedTests());
        audit(tenantId, saved, "prescription.updated", actorAppUserId, "Updated prescription draft");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord finalizePrescription(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        ensureDraft(entity);
        entity.finalizePrescription();
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        audit(tenantId, saved, "prescription.finalized", actorAppUserId, "Finalized prescription");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord markPrinted(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        ensurePrintable(entity);
        entity.markPrinted();
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        audit(tenantId, saved, "prescription.printed", actorAppUserId, "Marked prescription printed");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord markSent(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        ensureSendable(entity);
        entity.markSent();
        PrescriptionEntity saved = prescriptionRepository.save(entity);
        audit(tenantId, saved, "prescription.sent", actorAppUserId, "Marked prescription sent");
        return toRecord(saved, tenantData(tenantId));
    }

    @Transactional
    public PrescriptionRecord cancel(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        PrescriptionEntity entity = findEntity(tenantId, id);
        if (entity.getStatus() != PrescriptionStatus.DRAFT) {
            throw new IllegalArgumentException("Only draft prescriptions can be cancelled in this release");
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
        return createPdf(data.tenantName(), entity, data);
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
                entity.getDiagnosisSnapshot(),
                entity.getAdvice(),
                entity.getFollowUpDate(),
                entity.getStatus(),
                entity.getFinalizedAt(),
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
        String tenantName = StringUtils.hasText(displayName) ? displayName : (StringUtils.hasText(clinicName) ? clinicName : "Clinic");
        return new PrescriptionData(patients, users, tenantName, clinicName, displayName, address);
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

    private void ensureDraft(PrescriptionEntity entity) {
        if (entity.getStatus() != PrescriptionStatus.DRAFT) {
            throw new IllegalArgumentException("Prescription can only be modified while in draft");
        }
    }

    private void ensurePrintable(PrescriptionEntity entity) {
        if (entity.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled prescription cannot be printed");
        }
    }

    private void ensureSendable(PrescriptionEntity entity) {
        if (entity.getStatus() == PrescriptionStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled prescription cannot be marked sent");
        }
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
        details.put("status", entity.getStatus());
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

    private PrescriptionPdf createPdf(String tenantName, PrescriptionEntity entity, PrescriptionData data) {
        PrescriptionRecord record = toRecord(entity, data);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 40;
                float y = page.getMediaBox().getHeight() - margin;
                writeLine(content, tenantName, 16, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 18;
                if (StringUtils.hasText(data.clinicDisplayName())) {
                    writeLine(content, data.clinicDisplayName(), 11, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                    y -= 14;
                }
                if (StringUtils.hasText(data.clinicAddress())) {
                    y = writeWrapped(content, data.clinicAddress(), 9, margin, y, 520);
                }
                y -= 8;
                writeLine(content, "Prescription: " + record.prescriptionNumber(), 13, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 14;
                writeLine(content, "Patient: " + safe(record.patientName()) + " | " + patientSummary(record, data), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                y -= 12;
                writeLine(content, "Doctor: " + safe(record.doctorName()), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                y -= 12;
                writeLine(content, "Date: " + OffsetDateTime.now().toLocalDate(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
                y -= 16;
                if (StringUtils.hasText(record.diagnosisSnapshot())) {
                    writeLine(content, "Diagnosis", 11, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                    y -= 12;
                    y = writeWrapped(content, record.diagnosisSnapshot(), 9, margin, y, 520);
                    y -= 8;
                }
                writeLine(content, "Medicines", 11, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                y -= 12;
                for (PrescriptionMedicineRecord medicine : record.medicines()) {
                    String text = String.format(
                            "%s%s | %s | %s | %s | %s | %s",
                            safe(medicine.medicineName()),
                            medicine.strength() == null ? "" : " " + medicine.strength(),
                            safe(medicine.dosage()),
                            safe(medicine.frequency()),
                            safe(medicine.duration()),
                            medicine.timing() == null ? "ANYTIME" : medicine.timing().name(),
                            medicine.instructions() == null ? "" : medicine.instructions()
                    );
                    y = writeWrapped(content, text, 9, margin, y, 520);
                }
                if (!record.recommendedTests().isEmpty()) {
                    y -= 4;
                    writeLine(content, "Recommended Tests", 11, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                    y -= 12;
                    for (PrescriptionTestRecord test : record.recommendedTests()) {
                        y = writeWrapped(content, safe(test.testName()) + (test.instructions() == null ? "" : " - " + test.instructions()), 9, margin, y, 520);
                    }
                }
                if (StringUtils.hasText(record.advice())) {
                    y -= 4;
                    writeLine(content, "Advice", 11, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                    y -= 12;
                    y = writeWrapped(content, record.advice(), 9, margin, y, 520);
                }
                if (record.followUpDate() != null) {
                    y -= 4;
                    writeLine(content, "Follow up: " + record.followUpDate(), 10, margin, y, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
                }
            }
            document.save(output);
            return new PrescriptionPdf(safeFilename(record.prescriptionNumber()) + ".pdf", output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate prescription PDF", ex);
        }
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
            String clinicAddressValue
    ) {
        String clinicDisplayName() {
            return StringUtils.hasText(clinicDisplayNameValue) ? clinicDisplayNameValue : clinicName;
        }

        String clinicAddress() {
            return clinicAddressValue;
        }
    }
}
