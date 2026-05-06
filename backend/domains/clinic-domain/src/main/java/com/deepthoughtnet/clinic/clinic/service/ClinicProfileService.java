package com.deepthoughtnet.clinic.clinic.service;

import com.deepthoughtnet.clinic.clinic.db.ClinicProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.ClinicProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClinicProfileService {
    private static final String ENTITY_TYPE = "CLINIC_PROFILE";

    private final ClinicProfileRepository repository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public ClinicProfileService(
            ClinicProfileRepository repository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<ClinicProfileRecord> findByTenantId(UUID tenantId) {
        requireTenant(tenantId);
        return repository.findByTenantId(tenantId).map(this::toRecord);
    }

    @Transactional
    public ClinicProfileRecord upsert(UUID tenantId, ClinicProfileUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validate(command);

        ClinicProfileEntity entity = repository.findByTenantId(tenantId)
                .orElseGet(() -> ClinicProfileEntity.create(tenantId));
        boolean created = entity.getClinicName() == null;

        entity.update(
                normalize(command.clinicName()),
                normalize(command.displayName()),
                normalize(command.phone()),
                normalize(command.email()),
                normalize(command.addressLine1()),
                normalizeNullable(command.addressLine2()),
                normalize(command.city()),
                normalize(command.state()),
                normalize(command.country()),
                normalize(command.postalCode()),
                normalizeNullable(command.registrationNumber()),
                normalizeNullable(command.gstNumber()),
                command.logoDocumentId(),
                command.active()
        );

        ClinicProfileEntity saved = repository.save(entity);

        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                saved.getId(),
                created ? "clinic.profile.created" : "clinic.profile.updated",
                actorAppUserId,
                OffsetDateTime.now(),
                created ? "Created clinic profile" : "Updated clinic profile",
                detailsJson(saved)
        ));

        return toRecord(saved);
    }

    private ClinicProfileRecord toRecord(ClinicProfileEntity entity) {
        return new ClinicProfileRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getClinicName(),
                entity.getDisplayName(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getAddressLine1(),
                entity.getAddressLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getCountry(),
                entity.getPostalCode(),
                entity.getRegistrationNumber(),
                entity.getGstNumber(),
                entity.getLogoDocumentId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validate(ClinicProfileUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        requireText(command.clinicName(), "clinicName");
        requireText(command.displayName(), "displayName");
        requireText(command.phone(), "phone");
        requireText(command.email(), "email");
        requireText(command.addressLine1(), "addressLine1");
        requireText(command.city(), "city");
        requireText(command.state(), "state");
        requireText(command.country(), "country");
        requireText(command.postalCode(), "postalCode");
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

    private String detailsJson(ClinicProfileEntity entity) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("clinicName", entity.getClinicName());
        details.put("displayName", entity.getDisplayName());
        details.put("phone", entity.getPhone());
        details.put("email", entity.getEmail());
        details.put("addressLine1", entity.getAddressLine1());
        details.put("addressLine2", entity.getAddressLine2());
        details.put("city", entity.getCity());
        details.put("state", entity.getState());
        details.put("country", entity.getCountry());
        details.put("postalCode", entity.getPostalCode());
        details.put("registrationNumber", entity.getRegistrationNumber());
        details.put("gstNumber", entity.getGstNumber());
        details.put("logoDocumentId", entity.getLogoDocumentId());
        details.put("active", entity.isActive());

        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"clinicName\":\"" + escape(entity.getClinicName()) + "\"}";
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
