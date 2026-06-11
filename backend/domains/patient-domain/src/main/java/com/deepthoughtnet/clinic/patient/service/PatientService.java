package com.deepthoughtnet.clinic.patient.service;

import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventAction;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PatientService {
    private static final String ENTITY_TYPE = AuditEntityType.PATIENT;
    private static final String RECEPTIONIST_ROLE = "RECEPTIONIST";

    private final PatientRepository repository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public PatientService(PatientRepository repository, AuditEventPublisher auditEventPublisher, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PatientRecord> search(UUID tenantId, PatientSearchCriteria criteria) {
        requireTenant(tenantId);
        PatientSearchCriteria safeCriteria = criteria == null ? new PatientSearchCriteria(null, null, null, null) : criteria;
        String patientNumber = normalizeNullable(safeCriteria.patientNumber());
        String mobile = normalizeNullable(safeCriteria.mobile());
        String name = normalizeNullable(safeCriteria.name());
        Boolean active = safeCriteria.active();

        Specification<PatientEntity> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (patientNumber != null) {
                predicates.add(cb.equal(cb.lower(root.get("patientNumber")), patientNumber.toLowerCase()));
            }
            if (mobile != null) {
                String mobileTerm = mobile.toLowerCase();
                predicates.add(mobileTerm.length() >= 6
                        ? cb.like(cb.lower(root.get("mobile")), mobileTerm + "%")
                        : cb.equal(cb.lower(root.get("mobile")), mobileTerm));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (name != null) {
                String nameLike = "%" + name.toLowerCase() + "%";
                var fullName = cb.lower(cb.concat(cb.concat(cb.coalesce(root.get("firstName"), ""), " "), cb.coalesce(root.get("lastName"), "")));
                predicates.add(cb.or(
                        cb.like(fullName, nameLike),
                        cb.like(cb.lower(root.get("firstName")), nameLike),
                        cb.like(cb.lower(root.get("lastName")), nameLike)
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return repository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PatientRecord> findById(UUID tenantId, UUID id) {
        requireTenant(tenantId);
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return repository.findByTenantIdAndId(tenantId, id).map(this::toRecord);
    }

    @Transactional
    public PatientRecord create(UUID tenantId, PatientUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validate(command);

        String patientNumber = generatePatientNumber(tenantId);
        PatientEntity entity = PatientEntity.create(tenantId, patientNumber);
        applyCommand(entity, command);
        PatientEntity saved = repository.save(entity);

        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                saved.getId(),
                "patient.created",
                actorAppUserId,
                OffsetDateTime.now(),
                "Created patient " + saved.getPatientNumber(),
                detailsJson(saved)
        ));

        return toRecord(saved);
    }

    @Transactional
    public PatientRecord update(UUID tenantId, UUID id, PatientUpsertCommand command, UUID actorAppUserId) {
        return update(tenantId, id, command, actorAppUserId, null, null, null);
    }

    @Transactional
    public PatientRecord update(UUID tenantId, UUID id, PatientUpsertCommand command, UUID actorAppUserId, String actorRole, ZoneId zoneId, String actorEmail) {
        requireTenant(tenantId);
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        validate(command);
        PatientEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        enforceEditWindow(entity, actorRole, zoneId);

        Map<String, Object> before = snapshot(entity);
        applyCommand(entity, command);
        PatientEntity saved = repository.save(entity);

        recordFieldChanges(tenantId, saved, before, actorAppUserId, actorEmail, actorRole, OffsetDateTime.now());

        return toRecord(saved);
    }

    @Transactional
    public PatientRecord deactivate(UUID tenantId, UUID id, UUID actorAppUserId) {
        return deactivate(tenantId, id, actorAppUserId, null, null, null);
    }

    @Transactional
    public PatientRecord deactivate(UUID tenantId, UUID id, UUID actorAppUserId, String actorRole, ZoneId zoneId, String actorEmail) {
        requireTenant(tenantId);
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        PatientEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        enforceEditWindow(entity, actorRole, zoneId);
        Map<String, Object> before = snapshot(entity);
        entity.update(
                entity.getFirstName(),
                entity.getLastName(),
                entity.getGender(),
                entity.getDateOfBirth(),
                entity.getAgeYears(),
                entity.getMobile(),
                entity.getEmail(),
                entity.getAddressLine1(),
                entity.getAddressLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getCountry(),
                entity.getPostalCode(),
                entity.getEmergencyContactName(),
                entity.getEmergencyContactMobile(),
                entity.getBloodGroup(),
                entity.getAllergies(),
                entity.getExistingConditions(),
                entity.getLongTermMedications(),
                entity.getSurgicalHistory(),
                entity.getNotes(),
                false
        );
        PatientEntity saved = repository.save(entity);
        recordFieldChanges(tenantId, saved, before, actorAppUserId, actorEmail, actorRole, OffsetDateTime.now());
        return toRecord(saved);
    }

    public boolean canEditPatient(PatientRecord patient, String actorRole, ZoneId zoneId) {
        if (patient == null) {
            return false;
        }
        return canEditPatient(patient.createdAt(), actorRole, zoneId);
    }

    public boolean canEditPatient(OffsetDateTime createdAt, String actorRole, ZoneId zoneId) {
        if (!RECEPTIONIST_ROLE.equals(normalizeRole(actorRole))) {
            return true;
        }
        ZoneId effectiveZone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        if (createdAt == null) {
            return false;
        }
        LocalDate createdDate = createdAt.atZoneSameInstant(effectiveZone).toLocalDate();
        return createdDate.isEqual(LocalDate.now(effectiveZone));
    }

    private void applyCommand(PatientEntity entity, PatientUpsertCommand command) {
        Integer ageYears = command.ageYears();
        if (ageYears == null && command.dateOfBirth() != null) {
            ageYears = Period.between(command.dateOfBirth(), LocalDate.now()).getYears();
        }
        entity.update(
                normalize(command.firstName()),
                normalizeOptionalText(command.lastName()),
                command.gender() == null ? PatientGender.UNKNOWN : command.gender(),
                command.dateOfBirth(),
                ageYears,
                normalizeMobile(command.mobile(), "mobile"),
                normalizeNullable(command.email()),
                normalizeNullable(command.addressLine1()),
                normalizeNullable(command.addressLine2()),
                normalizeNullable(command.city()),
                normalizeNullable(command.state()),
                normalizeNullable(command.country()),
                normalizeNullable(command.postalCode()),
                normalizeNullable(command.emergencyContactName()),
                StringUtils.hasText(command.emergencyContactMobile()) ? normalizeMobile(command.emergencyContactMobile(), "emergencyContactMobile") : null,
                normalizeNullable(command.bloodGroup()),
                normalizeNullable(command.allergies()),
                normalizeNullable(command.existingConditions()),
                normalizeNullable(command.longTermMedications()),
                normalizeNullable(command.surgicalHistory()),
                normalizeNullable(command.notes()),
                command.active()
        );
    }

    private PatientRecord toRecord(PatientEntity entity) {
        return new PatientRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientNumber(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getGender(),
                entity.getDateOfBirth(),
                entity.getAgeYears(),
                entity.getMobile(),
                entity.getEmail(),
                entity.getAddressLine1(),
                entity.getAddressLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getCountry(),
                entity.getPostalCode(),
                entity.getEmergencyContactName(),
                entity.getEmergencyContactMobile(),
                entity.getBloodGroup(),
                entity.getAllergies(),
                entity.getExistingConditions(),
                entity.getLongTermMedications(),
                entity.getSurgicalHistory(),
                entity.getNotes(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String generatePatientNumber(UUID tenantId) {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = "PAT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            if (!repository.existsByTenantIdAndPatientNumber(tenantId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique patient number");
    }

    private void validate(PatientUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireText(command.firstName(), "firstName");
        if (command.gender() == null) {
            throw new IllegalArgumentException("gender is required");
        }
        normalizeMobile(command.mobile(), "mobile");
        if (StringUtils.hasText(command.emergencyContactMobile())) {
            normalizeMobile(command.emergencyContactMobile(), "emergencyContactMobile");
        }
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String normalizeMobile(String value, String field) {
        requireText(value, field);
        String normalized = value.trim().replaceAll("[\\s-]", "");
        if (!normalized.matches("[0-9]{10}")) {
            throw new IllegalArgumentException(field + " must be a valid 10-digit mobile number");
        }
        return normalized;
    }

    private String detailsJson(PatientEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("patientNumber", entity.getPatientNumber());
        details.put("firstName", entity.getFirstName());
        details.put("lastName", entity.getLastName());
        details.put("gender", entity.getGender());
        details.put("mobile", entity.getMobile());
        details.put("active", entity.isActive());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"patientNumber\":\"" + entity.getPatientNumber() + "\"}";
        }
    }

    private void enforceEditWindow(PatientEntity entity, String actorRole, ZoneId zoneId) {
        if (canEditPatient(entity.getCreatedAt(), actorRole, zoneId)) {
            return;
        }
        if (RECEPTIONIST_ROLE.equals(normalizeRole(actorRole))) {
            throw new ForbiddenException("Patient details can be edited by Clinic Admin after registration day.");
        }
    }

    private void recordFieldChanges(
            UUID tenantId,
            PatientEntity saved,
            Map<String, Object> before,
            UUID actorAppUserId,
            String actorEmail,
            String actorRole,
            OffsetDateTime occurredAt
    ) {
        Map<String, Object> after = snapshot(saved);
        List<String> changedFields = before.keySet().stream()
                .filter(field -> !Objects.equals(before.get(field), after.get(field)))
                .toList();
        if (changedFields.isEmpty()) {
            return;
        }

        for (String field : changedFields) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("actionType", AuditEventAction.PATIENT_PROFILE_UPDATED);
            details.put("patientId", saved.getId());
            details.put("patientNumber", saved.getPatientNumber());
            details.put("tenantId", tenantId);
            details.put("actorAppUserId", actorAppUserId);
            details.put("actorEmail", actorEmail);
            details.put("actorRole", normalizeRole(actorRole));
            details.put("timestamp", occurredAt == null ? null : occurredAt.toString());
            details.put("changedField", field);
            details.put("oldValue", before.get(field));
            details.put("newValue", after.get(field));

            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    ENTITY_TYPE,
                    saved.getId(),
                    AuditEventAction.PATIENT_PROFILE_UPDATED,
                    actorAppUserId,
                    occurredAt,
                    "Updated patient " + saved.getPatientNumber() + " field " + field,
                    toJson(details)
            ));
        }
    }

    private Map<String, Object> snapshot(PatientEntity entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstName", entity.getFirstName());
        data.put("lastName", entity.getLastName());
        data.put("gender", entity.getGender());
        data.put("dateOfBirth", entity.getDateOfBirth());
        data.put("ageYears", entity.getAgeYears());
        data.put("mobile", entity.getMobile());
        data.put("email", entity.getEmail());
        data.put("addressLine1", entity.getAddressLine1());
        data.put("addressLine2", entity.getAddressLine2());
        data.put("city", entity.getCity());
        data.put("state", entity.getState());
        data.put("country", entity.getCountry());
        data.put("postalCode", entity.getPostalCode());
        data.put("emergencyContactName", entity.getEmergencyContactName());
        data.put("emergencyContactMobile", entity.getEmergencyContactMobile());
        data.put("bloodGroup", entity.getBloodGroup());
        data.put("allergies", entity.getAllergies());
        data.put("existingConditions", entity.getExistingConditions());
        data.put("longTermMedications", entity.getLongTermMedications());
        data.put("surgicalHistory", entity.getSurgicalHistory());
        data.put("notes", entity.getNotes());
        data.put("active", entity.isActive());
        return data;
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            return "{\"patientId\":\"" + (data.get("patientId") == null ? "" : data.get("patientId")) + "\"}";
        }
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        String normalized = role.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
