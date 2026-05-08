package com.deepthoughtnet.clinic.patient.service;

import com.deepthoughtnet.clinic.patient.db.PatientEntity;
import com.deepthoughtnet.clinic.patient.db.PatientRepository;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.patient.service.model.PatientSearchCriteria;
import com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PatientService {
    private static final String ENTITY_TYPE = "PATIENT";

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
        requireTenant(tenantId);
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        validate(command);
        PatientEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        applyCommand(entity, command);
        PatientEntity saved = repository.save(entity);

        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                saved.getId(),
                "patient.updated",
                actorAppUserId,
                OffsetDateTime.now(),
                "Updated patient " + saved.getPatientNumber(),
                detailsJson(saved)
        ));

        return toRecord(saved);
    }

    @Transactional
    public PatientRecord deactivate(UUID tenantId, UUID id, UUID actorAppUserId) {
        requireTenant(tenantId);
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        PatientEntity entity = repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
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
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                saved.getId(),
                "patient.deactivated",
                actorAppUserId,
                OffsetDateTime.now(),
                "Deactivated patient " + saved.getPatientNumber(),
                detailsJson(saved)
        ));
        return toRecord(saved);
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
                normalize(command.mobile()),
                normalizeNullable(command.email()),
                normalizeNullable(command.addressLine1()),
                normalizeNullable(command.addressLine2()),
                normalizeNullable(command.city()),
                normalizeNullable(command.state()),
                normalizeNullable(command.country()),
                normalizeNullable(command.postalCode()),
                normalizeNullable(command.emergencyContactName()),
                normalizeNullable(command.emergencyContactMobile()),
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
        requireText(command.mobile(), "mobile");
        String mobile = command.mobile().trim();
        if (!mobile.matches("\\+?[0-9]{7,15}")) {
            throw new IllegalArgumentException("mobile must be a valid phone number");
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
}
