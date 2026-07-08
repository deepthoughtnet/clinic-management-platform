package com.deepthoughtnet.clinic.api.vaccination.document;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentUploadCommand;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notify.NotificationAttachment;
import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationRecommendationService;
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccinationRecommendationRecord;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccinationRecommendationSummary;
import com.deepthoughtnet.clinic.vaccination.service.model.VaccineMasterRecord;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class VaccinationDocumentService {
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);
    private static final String SOURCE_MODULE = "VACCINATION";

    private final VaccinationDocumentRenderer renderer;
    private final VaccinationService vaccinationService;
    private final VaccinationRecommendationService vaccinationRecommendationService;
    private final PatientRepository patientRepository;
    private final ClinicProfileService clinicProfileService;
    private final ClinicalDocumentService clinicalDocumentService;
    private final TenantUserManagementService tenantUserManagementService;
    private final NotificationHistoryService notificationHistoryService;
    private final NotificationProvider notificationProvider;
    private final AuditEventPublisher auditEventPublisher;

    public VaccinationDocumentService(
            VaccinationDocumentRenderer renderer,
            VaccinationService vaccinationService,
            VaccinationRecommendationService vaccinationRecommendationService,
            PatientRepository patientRepository,
            ClinicProfileService clinicProfileService,
            ClinicalDocumentService clinicalDocumentService,
            TenantUserManagementService tenantUserManagementService,
            NotificationHistoryService notificationHistoryService,
            NotificationProvider notificationProvider,
            AuditEventPublisher auditEventPublisher
    ) {
        this.renderer = renderer;
        this.vaccinationService = vaccinationService;
        this.vaccinationRecommendationService = vaccinationRecommendationService;
        this.patientRepository = patientRepository;
        this.clinicProfileService = clinicProfileService;
        this.clinicalDocumentService = clinicalDocumentService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.notificationHistoryService = notificationHistoryService;
        this.notificationProvider = notificationProvider;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public GeneratedVaccinationDocumentResponse generatePassport(UUID tenantId, UUID patientId, UUID actorAppUserId) {
        PatientEntity patient = patient(tenantId, patientId);
        ClinicProfileRecord clinic = clinic(tenantId);
        List<PatientVaccinationRecord> history = vaccinationService.listByPatient(tenantId, patientId);
        VaccinationRecommendationSummary recommendations = vaccinationRecommendationService.recommend(tenantId, patientId, null);
        VaccinationDocumentRenderer.PassportSummary summary = toPassportSummary(recommendations, history);
        String documentNumber = buildDocumentNumber("PASS", patient.getPatientNumber(), patientId);
        byte[] pdf = renderer.renderPassport(
                clinic,
                patientContext(patient, recommendations, summary.completionPercent()),
                history,
                summary,
                documentNumber,
                actorAppUserId,
                generatedByName(tenantId, actorAppUserId)
        );
        String title = "Immunization Passport";
        String filename = safeFilename(title) + ".pdf";
        ClinicalDocumentRecord record = clinicalDocumentService.upload(new ClinicalDocumentUploadCommand(
                tenantId,
                patientId,
                null,
                actorAppUserId,
                ClinicalDocumentType.VACCINATION,
                title,
                LocalDate.now(ZoneOffset.UTC),
                "RECEPTION",
                SOURCE_MODULE,
                documentNumber,
                "PATIENT_VISIBLE",
                filename,
                "application/pdf",
                pdf,
                "Generated vaccination passport"
        ));
        audit(tenantId, record.id(), actorAppUserId, "vaccination.passport_generated", "Generated vaccination passport");
        return toResponse(record, title, documentNumber, generatedByName(tenantId, actorAppUserId));
    }

    @Transactional
    public GeneratedVaccinationDocumentResponse generateCertificate(UUID tenantId, UUID patientId, VaccinationCertificateRequest request, UUID actorAppUserId) {
        PatientEntity patient = patient(tenantId, patientId);
        ClinicProfileRecord clinic = clinic(tenantId);
        String certificateType = normalizeCertificateType(request.certificateType());
        PatientVaccinationRecord vaccination = resolveVaccination(patientId, tenantId, request.vaccinationId());
        List<PatientVaccinationRecord> history = vaccinationService.listByPatient(tenantId, patientId);
        VaccinationRecommendationSummary recommendations = vaccinationRecommendationService.recommend(tenantId, patientId, null);
        int completionPercent = completionPercent(recommendations, history);
        String route = resolveRoute(vaccination);
        String administrationSite = resolveAdministrationSite(vaccination);
        String title = switch (certificateType) {
            case "CHILD_IMMUNIZATION" -> "Child Immunization Certificate";
            case "SCHOOL_VACCINATION" -> "School Vaccination Certificate";
            case "TRAVEL_VACCINATION" -> "Travel Vaccination Certificate";
            case "SINGLE_VACCINATION" -> "Single Vaccination Certificate";
            default -> "Vaccination Certificate";
        };
        String documentNumber = buildDocumentNumber("CERT", patient.getPatientNumber(), patientId);
        byte[] pdf = renderer.renderCertificate(
                clinic,
                patientContext(patient, recommendations, completionPercent),
                vaccination,
                history,
                title,
                documentNumber,
                route,
                administrationSite,
                actorAppUserId,
                generatedByName(tenantId, actorAppUserId)
        );
        String filename = safeFilename(title) + ".pdf";
        ClinicalDocumentRecord record = clinicalDocumentService.upload(new ClinicalDocumentUploadCommand(
                tenantId,
                patientId,
                null,
                actorAppUserId,
                ClinicalDocumentType.VACCINATION,
                title,
                LocalDate.now(ZoneOffset.UTC),
                "RECEPTION",
                SOURCE_MODULE,
                documentNumber,
                "PATIENT_VISIBLE",
                filename,
                "application/pdf",
                pdf,
                "Generated vaccination certificate"
        ));
        audit(tenantId, record.id(), actorAppUserId, "vaccination.certificate_generated", "Generated vaccination certificate");
        return toResponse(record, title, documentNumber, generatedByName(tenantId, actorAppUserId));
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(UUID tenantId, UUID documentId) {
        return clinicalDocumentService.downloadBytes(tenantId, documentId);
    }

    @Transactional
    public void sendDocument(UUID tenantId, UUID documentId, String channel, UUID actorAppUserId) {
        ClinicalDocumentRecord record = clinicalDocumentService.get(tenantId, documentId);
        PatientEntity patient = patient(tenantId, record.patientId());
        String normalizedChannel = normalizeChannel(channel);
        String recipient = resolveRecipient(patient, normalizedChannel);
        String subject = safe(record.title(), "Vaccination document");
        byte[] pdf = clinicalDocumentService.downloadBytes(tenantId, documentId);
        if ("email".equals(normalizedChannel)) {
            notificationProvider.send(new NotificationMessage(
                    tenantId,
                    "EMAIL",
                    recipient,
                    subject,
                    "Your vaccination document is attached.",
                    "{\"sourceType\":\"VACCINATION_DOCUMENT\",\"sourceId\":\"" + documentId + "\"}",
                    null,
                    List.of(new NotificationAttachment(record.originalFilename(), "application/pdf", pdf))
            ));
        }
        notificationHistoryService.queueDetailed(
                tenantId,
                patient.getId(),
                "VACCINATION_DOCUMENT_SENT",
                normalizedChannel,
                recipient,
                subject,
                "Vaccination document " + record.title() + " sent via " + normalizedChannel,
                "VACCINATION_DOCUMENT",
                documentId,
                actorAppUserId
        );
        audit(tenantId, documentId, actorAppUserId, "vaccination.document_sent", "Sent vaccination document");
    }

    private VaccinationDocumentRenderer.PatientVaccinationDocumentContext patientContext(PatientEntity patient, VaccinationRecommendationSummary recommendations, int completionPercent) {
        return new VaccinationDocumentRenderer.PatientVaccinationDocumentContext(
                patient.getId(),
                (patient.getFirstName() + " " + patient.getLastName()).trim(),
                patient.getPatientNumber(),
                patient.getDateOfBirth(),
                ageLabel(patient),
                patient.getGender() == null ? null : patient.getGender().name(),
                patient.getMobile(),
                patient.getBloodGroup(),
                recommendations.scheduleType(),
                completionPercent
        );
    }

    private PatientVaccinationRecord resolveVaccination(UUID patientId, UUID tenantId, String vaccinationId) {
        if (!StringUtils.hasText(vaccinationId)) {
            return null;
        }
        UUID id = UUID.fromString(vaccinationId);
        return vaccinationService.listByPatient(tenantId, patientId).stream()
                .filter(row -> id.equals(row.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vaccination record not found"));
    }

    private VaccinationDocumentRenderer.PassportSummary toPassportSummary(VaccinationRecommendationSummary recommendations, List<PatientVaccinationRecord> history) {
        int completed = recommendations.completed().size() + (int) history.stream().filter(row -> "EXTERNAL".equalsIgnoreCase(row.source())).count();
        int applicable = recommendations.recommendedToday().size() + recommendations.overdue().size() + recommendations.upcoming().size() + recommendations.completed().size();
        String nextDue = nextDueVaccine(recommendations);
        int completionPercent = applicable > 0 ? Math.round((Math.min(completed, applicable) * 100f) / applicable) : 0;
        return new VaccinationDocumentRenderer.PassportSummary(
                completed,
                applicable,
                recommendations.upcoming().size(),
                recommendations.recommendedToday().size(),
                recommendations.overdue().size(),
                nextDue,
                nextDueDate(recommendations),
                completionPercent
        );
    }

    private String nextDueVaccine(VaccinationRecommendationSummary recommendations) {
        for (VaccinationRecommendationRecord record : recommendations.recommendedToday()) {
            if (StringUtils.hasText(record.vaccineName())) {
                return record.vaccineName();
            }
        }
        for (VaccinationRecommendationRecord record : recommendations.overdue()) {
            if (StringUtils.hasText(record.vaccineName())) {
                return record.vaccineName();
            }
        }
        for (VaccinationRecommendationRecord record : recommendations.upcoming()) {
            if (StringUtils.hasText(record.vaccineName())) {
                return record.vaccineName();
            }
        }
        return null;
    }

    private LocalDate nextDueDate(VaccinationRecommendationSummary recommendations) {
        for (VaccinationRecommendationRecord record : recommendations.recommendedToday()) {
            if (record.dueDate() != null) return record.dueDate();
        }
        for (VaccinationRecommendationRecord record : recommendations.overdue()) {
            if (record.dueDate() != null) return record.dueDate();
        }
        for (VaccinationRecommendationRecord record : recommendations.upcoming()) {
            if (record.dueDate() != null) return record.dueDate();
        }
        return null;
    }

    private GeneratedVaccinationDocumentResponse toResponse(ClinicalDocumentRecord record, String title, String documentNumber, String generatedBy) {
        return new GeneratedVaccinationDocumentResponse(
                record.id().toString(),
                clinicalDocumentService.downloadUrl(record.tenantId(), record.id(), DOWNLOAD_TTL),
                String.valueOf(DOWNLOAD_TTL.toSeconds()),
                record.originalFilename(),
                title,
                documentNumber,
                record.createdAt().toString(),
                generatedBy
        );
    }

    private void audit(UUID tenantId, UUID entityId, UUID actorAppUserId, String action, String message) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                entityId,
                action,
                actorAppUserId,
                OffsetDateTime.now(ZoneOffset.UTC),
                message,
                "{}"
        ));
    }

    private PatientEntity patient(UUID tenantId, UUID patientId) {
        return patientRepository.findByTenantIdAndId(tenantId, patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    private ClinicProfileRecord clinic(UUID tenantId) {
        return clinicProfileService.findByTenantId(tenantId)
                .orElse(null);
    }

    private String generatedByName(UUID tenantId, UUID actorAppUserId) {
        if (actorAppUserId == null) {
            return "System";
        }
        return tenantUserManagementService.list(tenantId).stream()
                .filter(user -> actorAppUserId.equals(user.appUserId()))
                .map(TenantUserRecord::displayName)
                .findFirst()
                .orElse("System");
    }

    private String normalizeCertificateType(String certificateType) {
        String normalized = safe(certificateType, "SINGLE_VACCINATION").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CHILD", "CHILD_IMMUNIZATION" -> "CHILD_IMMUNIZATION";
            case "SCHOOL", "SCHOOL_VACCINATION" -> "SCHOOL_VACCINATION";
            case "TRAVEL", "TRAVEL_VACCINATION" -> "TRAVEL_VACCINATION";
            case "SINGLE", "SINGLE_VACCINATION" -> "SINGLE_VACCINATION";
            default -> "SINGLE_VACCINATION";
        };
    }

    private String buildDocumentNumber(String prefix, String patientNumber, UUID patientId) {
        String token = StringUtils.hasText(patientNumber) ? patientNumber.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT) : patientId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return prefix + "-" + token + "-" + LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
    }

    private String ageLabel(PatientEntity patient) {
        if (patient.getDateOfBirth() != null) {
            long days = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(patient.getDateOfBirth(), LocalDate.now(ZoneOffset.UTC)));
            long years = days / 365;
            long months = (days % 365) / 30;
            if (years > 0) {
                return months > 0 ? years + " years " + months + " months" : years + " years";
            }
            return months + " months";
        }
        return patient.getAgeYears() == null ? "Age unavailable" : patient.getAgeYears() + " years";
    }

    private String safeFilename(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "email";
        }
        return channel.trim().toLowerCase(Locale.ROOT);
    }

    private int completionPercent(VaccinationRecommendationSummary recommendations, List<PatientVaccinationRecord> history) {
        int completed = recommendations.completed().size() + (int) history.stream().filter(row -> "EXTERNAL".equalsIgnoreCase(row.source())).count();
        int applicable = recommendations.recommendedToday().size() + recommendations.overdue().size() + recommendations.upcoming().size() + recommendations.completed().size();
        return applicable > 0 ? Math.round((Math.min(completed, applicable) * 100f) / applicable) : 0;
    }

    private String resolveRoute(PatientVaccinationRecord vaccination) {
        if (vaccination == null || vaccination.vaccineId() == null) {
            return null;
        }
        return vaccinationService.findVaccine(vaccination.tenantId(), vaccination.vaccineId())
                .map(VaccineMasterRecord::route)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private String resolveAdministrationSite(PatientVaccinationRecord vaccination) {
        if (vaccination == null || vaccination.vaccineId() == null) {
            return null;
        }
        return vaccinationService.findVaccine(vaccination.tenantId(), vaccination.vaccineId())
                .map(VaccineMasterRecord::administrationSite)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private String resolveRecipient(PatientEntity patient, String channel) {
        if ("whatsapp".equals(channel) || "sms".equals(channel)) {
            if (StringUtils.hasText(patient.getMobile())) {
                return patient.getMobile();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient mobile number is required for WhatsApp/SMS notifications");
        }
        if (StringUtils.hasText(patient.getEmail())) {
            return patient.getEmail();
        }
        if (StringUtils.hasText(patient.getMobile())) {
            return patient.getMobile();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient contact is required");
    }

}
