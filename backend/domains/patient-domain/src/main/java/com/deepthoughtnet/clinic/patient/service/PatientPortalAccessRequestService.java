package com.deepthoughtnet.clinic.patient.service;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.db.PatientPortalAccessRequestEntity;
import com.deepthoughtnet.clinic.patient.db.PatientPortalAccessRequestRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessContext;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessGrantRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestCommand;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestConflictException;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestStatus;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestType;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.security.AppUserProvisioner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PatientPortalAccessRequestService {
    private static final String ENTITY_TYPE = "PATIENT_PORTAL_ACCESS_REQUEST";
    private static final String ACCESS_CODE_SUBJECT_PREFIX = "patientportal-access";
    private static final Duration ACCESS_CODE_TTL = Duration.ofDays(7);

    private final PatientPortalAccessRequestRepository requestRepository;
    private final TenantRepository tenantRepository;
    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final AppUserProvisioner appUserProvisioner;
    private final AppUserRepository appUserRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PatientPortalAccessRequestService(
            PatientPortalAccessRequestRepository requestRepository,
            TenantRepository tenantRepository,
            PatientRepository patientRepository,
            PatientService patientService,
            AppUserProvisioner appUserProvisioner,
            AppUserRepository appUserRepository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.requestRepository = requestRepository;
        this.tenantRepository = tenantRepository;
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.appUserProvisioner = appUserProvisioner;
        this.appUserRepository = appUserRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PatientPortalAccessRequestRecord submit(PatientPortalAccessRequestCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Access request is required");
        }
        String fullName = normalizeRequired(command.fullName(), "Full name is required", 256);
        String mobile = normalizeRequiredPhone(command.mobile());
        String email = normalizeOptional(command.email(), 256);
        String note = normalizeOptional(command.note(), null);

        TenantEntity tenant = resolveTenant(command.context());
        if (tenant == null) {
            throw new IllegalArgumentException("Clinic context could not be resolved");
        }
        UUID tenantId = tenant.getId();

        requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(tenantId, mobile)
                .filter(existing -> !isTerminal(existing.getStatus()))
                .ifPresent(existing -> {
                    throw new PatientPortalAccessRequestConflictException(existing.getStatus() == PatientPortalAccessRequestStatus.APPROVED
                            || existing.getStatus() == PatientPortalAccessRequestStatus.ACTIVE
                            ? "Access has already been approved for this account."
                            : "An access request for this mobile number is already pending.");
                });

        PatientPortalAccessRequestEntity entity = PatientPortalAccessRequestEntity.create(tenantId, fullName, mobile, mobile, email, note);
        PatientPortalAccessRequestEntity saved = requestRepository.save(entity);
        recordAudit(tenantId, saved.getId(), "PATIENT_ACCESS_REQUESTED", null, "Patient access requested", detailsJson(saved));
        return toRecord(saved, tenant, null);
    }

    @Transactional(readOnly = true)
    public List<PatientPortalAccessRequestRecord> list(String status, String query) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedQuery = normalizeOptional(query, 128);
        List<PatientPortalAccessRequestEntity> all = requestRepository.findAll();
        Map<UUID, TenantEntity> tenants = loadTenants(all.stream().map(PatientPortalAccessRequestEntity::getTenantId).collect(Collectors.toSet()));

        return all.stream()
                .filter(request -> normalizedStatus == null || request.getStatus().name().equalsIgnoreCase(normalizedStatus))
                .filter(request -> matchesQuery(request, tenants.get(request.getTenantId()), normalizedQuery))
                .sorted(Comparator.comparing(PatientPortalAccessRequestEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(request -> toRecord(request, tenants.get(request.getTenantId()), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PatientPortalAccessRequestRecord> find(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return requestRepository.findById(id).map(request -> toRecord(request, tenantRepository.findById(request.getTenantId()).orElse(null), null));
    }

    @Transactional
    public PatientPortalAccessRequestRecord approve(UUID id, UUID actorAppUserId, String reviewedByDisplayName, String reason, UUID patientId) {
        PatientPortalAccessRequestEntity entity = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found"));
        if (entity.getStatus() == PatientPortalAccessRequestStatus.REJECTED || entity.getStatus() == PatientPortalAccessRequestStatus.REVOKED) {
            throw new PatientPortalAccessRequestConflictException("This access request is no longer reviewable.");
        }
        if ((entity.getStatus() == PatientPortalAccessRequestStatus.APPROVED || entity.getStatus() == PatientPortalAccessRequestStatus.ACTIVE)
                && entity.getLinkedPatientId() != null) {
            TenantEntity tenant = tenantRepository.findById(entity.getTenantId()).orElse(null);
            return toRecord(entity, tenant, null);
        }

        TenantEntity tenant = tenantRepository.findById(entity.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        PatientRecord linkedPatient = ensureLinkedPatient(tenant.getId(), entity, actorAppUserId, patientId);
        String linkedPatientDisplayName = linkedPatient == null ? null : linkedPatient.fullName();
        UUID linkedPatientId = linkedPatient == null ? null : linkedPatient.id();
        String accessCode = generateAccessCode();
        OffsetDateTime now = OffsetDateTime.now();
        entity.attachAccessCode(passwordEncoder.encode(accessCode), now, now.plus(ACCESS_CODE_TTL));
        entity.approve(actorAppUserId, reviewedByDisplayName, linkedPatientId, linkedPatientDisplayName);
        requestRepository.save(entity);
        recordAudit(
                tenant.getId(),
                entity.getId(),
                "PATIENT_ACCESS_APPROVED",
                actorAppUserId,
                "Patient access approved",
                detailsJson(entity)
        );
        return toRecord(entity, tenant, accessCode);
    }

    @Transactional
    public PatientPortalAccessRequestRecord reject(UUID id, UUID actorAppUserId, String reviewedByDisplayName, String reason) {
        PatientPortalAccessRequestEntity entity = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found"));
        if (entity.getStatus() == PatientPortalAccessRequestStatus.REJECTED) {
            TenantEntity tenant = tenantRepository.findById(entity.getTenantId()).orElse(null);
            return toRecord(entity, tenant, null);
        }
        if (entity.getStatus() == PatientPortalAccessRequestStatus.ACTIVE || entity.getStatus() == PatientPortalAccessRequestStatus.REVOKED) {
            throw new PatientPortalAccessRequestConflictException("This access request can no longer be rejected.");
        }
        TenantEntity tenant = tenantRepository.findById(entity.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        entity.reject(actorAppUserId, reviewedByDisplayName, normalizeOptional(reason, 512));
        requestRepository.save(entity);
        recordAudit(tenant.getId(), entity.getId(), "PATIENT_ACCESS_REJECTED", actorAppUserId, "Patient access rejected", detailsJson(entity));
        return toRecord(entity, tenant, null);
    }

    @Transactional
    public PatientPortalAccessRequestRecord revoke(UUID id, UUID actorAppUserId, String reviewedByDisplayName, String reason) {
        PatientPortalAccessRequestEntity entity = requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found"));
        if (entity.getStatus() == PatientPortalAccessRequestStatus.REVOKED) {
            TenantEntity tenant = tenantRepository.findById(entity.getTenantId()).orElse(null);
            return toRecord(entity, tenant, null);
        }
        TenantEntity tenant = tenantRepository.findById(entity.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        entity.revoke(actorAppUserId, reviewedByDisplayName, normalizeOptional(reason, 512));
        requestRepository.save(entity);
        recordAudit(tenant.getId(), entity.getId(), "PATIENT_ACCESS_REVOKED", actorAppUserId, "Patient access revoked", detailsJson(entity));
        return toRecord(entity, tenant, null);
    }

    @Transactional
    public PatientPortalAccessGrantRecord authenticate(UUID tenantId, String mobile, String accessCode, PatientPortalAccessContext context) {
        String normalizedMobile = normalizeRequiredPhone(mobile);
        TenantEntity tenant = resolveTenant(tenantId, context);
        if (tenant == null) {
            tenant = resolveTenantFromApprovedRequest(normalizedMobile);
        }
        if (tenant == null) {
            tenant = resolveTenantFromAnyRequest(normalizedMobile);
        }
        if (tenant == null) {
            throw new IllegalArgumentException("Clinic context could not be resolved");
        }

        PatientPortalAccessRequestEntity entity = requestRepository.findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(tenant.getId(), normalizedMobile)
                .orElseThrow(() -> new PatientPortalAccessRequestConflictException("No approved access request was found for this account."));
        if (entity.getStatus() == PatientPortalAccessRequestStatus.REJECTED || entity.getStatus() == PatientPortalAccessRequestStatus.REVOKED || entity.getStatus() == PatientPortalAccessRequestStatus.REQUESTED) {
            throw new PatientPortalAccessRequestConflictException("This access request is not currently active.");
        }
        if (entity.getAccessCodeHash() == null || entity.getAccessCodeExpiresAt() == null || entity.getAccessCodeExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new PatientPortalAccessRequestConflictException("This access code has expired. Please request a new approval.");
        }
        if (!passwordEncoder.matches(normalizeRequired(accessCode, "Access code is required", 64), entity.getAccessCodeHash())) {
            throw new PatientPortalAccessRequestConflictException("The access code is invalid.");
        }
        PatientRecord patient = ensureLinkedPatient(tenant.getId(), entity, null, null);
        if (patient == null) {
            throw new PatientPortalAccessRequestConflictException("This approved access request has not been linked to a patient yet.");
        }

        String displayName = patient.fullName();
        String subject = patientSubject(tenant.getId(), patient.id());
        UUID appUserId = appUserProvisioner.upsertAndReturnId(tenant.getId(), subject, patient.email(), displayName);
        appUserRepository.findByTenantIdAndId(tenant.getId(), appUserId).ifPresent(appUser -> {
            appUser.setPatientId(patient.id());
            appUser.updateProfile(patient.email(), displayName);
        });
        if (entity.getStatus() == PatientPortalAccessRequestStatus.APPROVED) {
            entity.activate();
            requestRepository.save(entity);
            recordAudit(tenant.getId(), entity.getId(), "PATIENT_ACCESS_ACTIVATED", null, "Patient access activated", detailsJson(entity));
        }
        return new PatientPortalAccessGrantRecord(tenant.getId(), tenant.getCode(), patient.id(), displayName, normalizedMobile, subject);
    }

    private TenantEntity resolveTenant(PatientPortalAccessContext context) {
        if (context == null) {
            return null;
        }
        if (StringUtils.hasText(context.tenantId())) {
            try {
                UUID tenantId = UUID.fromString(context.tenantId().trim());
                TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
                if (tenant != null) {
                    return tenant;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to slug lookup
            }
        }
        if (StringUtils.hasText(context.clinicSlug())) {
            String normalized = context.clinicSlug().trim().toLowerCase(Locale.ROOT);
            TenantEntity tenant = tenantRepository.findByCode(normalized).orElse(null);
            if (tenant != null) {
                return tenant;
            }
        }
        if (StringUtils.hasText(context.clinicId())) {
            try {
                UUID tenantId = UUID.fromString(context.clinicId().trim());
                return tenantRepository.findById(tenantId).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return null;
    }

    private TenantEntity resolveTenant(UUID tenantId, PatientPortalAccessContext context) {
        if (tenantId != null) {
            return tenantRepository.findById(tenantId).orElse(null);
        }
        return resolveTenant(context);
    }

    private TenantEntity resolveTenantFromApprovedRequest(String normalizedMobile) {
        List<PatientPortalAccessRequestEntity> matches = requestRepository.findAll().stream()
                .filter(request -> Objects.equals(request.getMobileNormalized(), normalizedMobile))
                .filter(request -> request.getStatus() == PatientPortalAccessRequestStatus.APPROVED || request.getStatus() == PatientPortalAccessRequestStatus.ACTIVE)
                .sorted(Comparator.comparing(PatientPortalAccessRequestEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (matches.isEmpty()) {
            return null;
        }
        UUID tenantId = matches.get(0).getTenantId();
        boolean ambiguous = matches.stream().anyMatch(request -> !Objects.equals(request.getTenantId(), tenantId));
        if (ambiguous) {
            throw new PatientPortalAccessRequestConflictException("Please use the clinic context associated with this approved access request.");
        }
        return tenantRepository.findById(tenantId).orElse(null);
    }

    private TenantEntity resolveTenantFromAnyRequest(String normalizedMobile) {
        List<PatientPortalAccessRequestEntity> matches = requestRepository.findAll().stream()
                .filter(request -> Objects.equals(request.getMobileNormalized(), normalizedMobile))
                .sorted(Comparator.comparing(PatientPortalAccessRequestEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (matches.isEmpty()) {
            return null;
        }
        UUID tenantId = matches.get(0).getTenantId();
        boolean ambiguous = matches.stream().anyMatch(request -> !Objects.equals(request.getTenantId(), tenantId));
        if (ambiguous) {
            throw new PatientPortalAccessRequestConflictException("Please use the clinic context associated with this access request.");
        }
        return tenantRepository.findById(tenantId).orElse(null);
    }

    private PatientEntity resolvePatient(UUID tenantId, String mobileNormalized, UUID patientId) {
        if (patientId != null) {
            return patientRepository.findByTenantIdAndId(tenantId, patientId)
                    .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        }
        List<PatientEntity> candidates = patientRepository.findByTenantIdAndMobileIgnoreCaseAndActiveTrue(tenantId, mobileNormalized);
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .min(Comparator.comparing(PatientEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientEntity::getId))
                .orElse(candidates.get(0));
    }

    private PatientRecord ensureLinkedPatient(UUID tenantId, PatientPortalAccessRequestEntity entity, UUID actorAppUserId, UUID patientId) {
        if (entity.getLinkedPatientId() != null) {
            PatientEntity patient = patientRepository.findByTenantIdAndId(tenantId, entity.getLinkedPatientId())
                    .orElseThrow(() -> new PatientPortalAccessRequestConflictException("The linked patient record is no longer available."));
            return toPatientRecord(patient);
        }

        PatientEntity existingPatient = resolvePatient(tenantId, entity.getMobileNormalized(), patientId);
        if (existingPatient != null) {
            entity.linkPatient(existingPatient.getId(), fullName(existingPatient.getFirstName(), existingPatient.getLastName()));
            requestRepository.save(entity);
            return toPatientRecord(existingPatient);
        }

        PatientRecord createdPatient = patientService.create(
                tenantId,
                new PatientUpsertCommand(
                        firstName(entity.getFullName()),
                        lastName(entity.getFullName()),
                        PatientGender.UNKNOWN,
                        null,
                        null,
                        entity.getMobileNormalized(),
                        entity.getEmail(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        entity.getNote(),
                        true
                ),
                actorAppUserId
        );
        entity.linkPatient(createdPatient.id(), createdPatient.fullName());
        requestRepository.save(entity);
        return createdPatient;
    }

    private PatientRecord toPatientRecord(PatientEntity patient) {
        return new PatientRecord(
                patient.getId(),
                patient.getTenantId(),
                patient.getPatientNumber(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getGender(),
                patient.getDateOfBirth(),
                patient.getAgeYears(),
                patient.getMobile(),
                patient.getEmail(),
                patient.getAddressLine1(),
                patient.getAddressLine2(),
                patient.getCity(),
                patient.getState(),
                patient.getCountry(),
                patient.getPostalCode(),
                patient.getEmergencyContactName(),
                patient.getEmergencyContactMobile(),
                patient.getBloodGroup(),
                patient.getAllergies(),
                patient.getExistingConditions(),
                patient.getLongTermMedications(),
                patient.getSurgicalHistory(),
                patient.getNotes(),
                patient.isActive(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }

    private String firstName(String fullName) {
        return splitName(fullName)[0];
    }

    private String lastName(String fullName) {
        return splitName(fullName)[1];
    }

    private String[] splitName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return new String[]{"Patient", ""};
        }
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        int lastSpace = normalized.lastIndexOf(' ');
        if (lastSpace <= 0 || lastSpace >= normalized.length() - 1) {
            return new String[]{normalized, ""};
        }
        return new String[]{normalized.substring(0, lastSpace).trim(), normalized.substring(lastSpace + 1).trim()};
    }

    private Map<UUID, TenantEntity> loadTenants(Set<UUID> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        return tenantRepository.findAllById(tenantIds).stream()
                .collect(Collectors.toMap(TenantEntity::getId, tenant -> tenant, (a, b) -> a, LinkedHashMap::new));
    }

    private boolean matchesQuery(PatientPortalAccessRequestEntity request, TenantEntity tenant, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String term = query.trim().toLowerCase(Locale.ROOT);
        String haystack = String.join(" ",
                safe(request.getFullName()),
                safe(request.getMobile()),
                safe(request.getEmail()),
                safe(tenant == null ? null : tenant.getCode()),
                safe(tenant == null ? null : tenant.getName())
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(term);
    }

    private PatientPortalAccessRequestRecord toRecord(PatientPortalAccessRequestEntity entity, TenantEntity tenant, String temporaryAccessCode) {
        return new PatientPortalAccessRequestRecord(
                entity.getId(),
                entity.getTenantId(),
                tenant == null ? null : tenant.getCode(),
                tenant == null ? null : tenant.getName(),
                entity.getRequestType(),
                entity.getFullName(),
                entity.getMobile(),
                entity.getEmail(),
                entity.getNote(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getLinkedPatientId(),
                entity.getLinkedPatientDisplayName(),
                entity.getReviewedBy(),
                entity.getReviewedByDisplayName(),
                temporaryAccessCode,
                entity.getRequestedAt(),
                entity.getReviewedAt(),
                entity.getApprovedAt(),
                entity.getActivatedAt(),
                entity.getRevokedAt(),
                entity.getAccessCodeExpiresAt(),
                entity.getRequestedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    private void recordAudit(UUID tenantId, UUID entityId, String action, UUID actorAppUserId, String message, String detailsJson) {
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                entityId,
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                message,
                detailsJson
        ));
    }

    private String detailsJson(PatientPortalAccessRequestEntity entity) {
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("id", entity.getId().toString());
            details.put("tenantId", entity.getTenantId().toString());
            details.put("requestType", entity.getRequestType().name());
            details.put("fullName", entity.getFullName());
            details.put("mobile", entity.getMobile());
            details.put("status", entity.getStatus().name());
            details.put("linkedPatientId", entity.getLinkedPatientId() == null ? null : entity.getLinkedPatientId().toString());
            details.put("reviewedBy", entity.getReviewedBy() == null ? null : entity.getReviewedBy().toString());
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private boolean isTerminal(PatientPortalAccessRequestStatus status) {
        return status == PatientPortalAccessRequestStatus.REJECTED
                || status == PatientPortalAccessRequestStatus.REVOKED;
    }

    private String generateAccessCode() {
        int code = ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        return String.valueOf(code);
    }

    private String normalizeRequiredPhone(String value) {
        String normalized = normalizeRequired(value, "Mobile number is required", 16);
        normalized = normalized.replaceAll("[\\s-]", "");
        if (!normalized.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (maxLength > 0 && normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptional(String value, Integer maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (maxLength != null && normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String fullName(String firstName, String lastName) {
        String first = StringUtils.hasText(firstName) ? firstName.trim() : "";
        String last = StringUtils.hasText(lastName) ? lastName.trim() : "";
        return (first + " " + last).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String patientSubject(UUID tenantId, UUID patientId) {
        return "patientportal:" + tenantId + ":" + patientId;
    }
}
