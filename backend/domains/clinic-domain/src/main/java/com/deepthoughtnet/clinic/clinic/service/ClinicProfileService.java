package com.deepthoughtnet.clinic.clinic.service;

import com.deepthoughtnet.clinic.clinic.db.ClinicProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.ClinicProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileUpsertCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.StringUtils;

@Service
public class ClinicProfileService {
    private static final String ENTITY_TYPE = "CLINIC_PROFILE";
    private static final int MAX_CLINIC_NAME_LENGTH = 256;
    private static final int MAX_DISPLAY_NAME_LENGTH = 256;
    private static final int MAX_EMAIL_LENGTH = 256;
    private static final int MAX_ADDRESS_LENGTH = 256;
    private static final int MAX_CITY_LENGTH = 128;
    private static final int MAX_STATE_LENGTH = 128;
    private static final int MAX_COUNTRY_LENGTH = 128;
    private static final int MAX_POSTAL_CODE_LENGTH = 32;
    private static final int MAX_REGISTRATION_NUMBER_LENGTH = 128;
    private static final int MAX_GST_LENGTH = 15;
    private static final int MAX_SLUG_LENGTH = 192;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\u0000-\\u001F\\u007F]");
    private static final Pattern INDIAN_MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern GSTIN_PATTERN = Pattern.compile("^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final Pattern PUBLIC_SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern NON_INDIA_POSTAL_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9\\s-]{0,31}$");

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

    @Transactional(readOnly = true)
    public Optional<ClinicProfileRecord> findBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return Optional.empty();
        }
        return repository.findBySlugIgnoreCase(slug.trim()).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public List<ClinicProfileRecord> findAll() {
        return repository.findAll().stream().map(this::toRecord).toList();
    }

    @Transactional
    public ClinicProfileRecord upsert(UUID tenantId, ClinicProfileUpsertCommand command, UUID actorAppUserId) {
        requireTenant(tenantId);
        validate(command);

        ClinicProfileEntity entity = repository.findByTenantId(tenantId)
                .orElseGet(() -> ClinicProfileEntity.create(tenantId));
        boolean created = entity.getClinicName() == null;
        NormalizedClinicProfile normalized = normalize(command, entity.getId());

        entity.update(
                normalized.clinicName(),
                normalized.displayName(),
                normalized.phone(),
                normalized.email(),
                normalized.addressLine1(),
                normalized.addressLine2(),
                normalized.city(),
                normalized.state(),
                normalized.country(),
                normalized.postalCode(),
                normalized.registrationNumber(),
                normalized.gstNumber(),
                command.logoDocumentId(),
                command.active(),
                command.publicListingEnabled(),
                normalized.slug()
        );

        ClinicProfileEntity saved;
        try {
            saved = repository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            if (isSlugConstraintViolation(ex)) {
                throw new IllegalArgumentException("This public slug is already in use. Choose another one.", ex);
            }
            throw ex;
        }

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
                entity.isPublicListingEnabled(),
                entity.getSlug(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validate(ClinicProfileUpsertCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Clinic profile update is required.");
        }
    }

    private NormalizedClinicProfile normalize(ClinicProfileUpsertCommand command, UUID currentClinicProfileId) {
        String clinicName = requireBusinessText(command.clinicName(), "Clinic name", MAX_CLINIC_NAME_LENGTH);
        String displayName = requireBusinessText(command.displayName(), "Display name", MAX_DISPLAY_NAME_LENGTH);
        String phone = requirePhone(command.phone());
        String email = requireEmail(command.email());
        String addressLine1 = requireBusinessText(command.addressLine1(), "Address line 1", MAX_ADDRESS_LENGTH);
        String addressLine2 = normalizeOptionalText(command.addressLine2(), "Address line 2", MAX_ADDRESS_LENGTH);
        String city = requireBusinessText(command.city(), "City", MAX_CITY_LENGTH);
        String state = requireBusinessText(command.state(), "State", MAX_STATE_LENGTH);
        String country = requireBusinessText(command.country(), "Country", MAX_COUNTRY_LENGTH);
        String postalCode = requirePostalCode(command.postalCode(), country);
        String registrationNumber = requireBusinessText(command.registrationNumber(), "Registration number", MAX_REGISTRATION_NUMBER_LENGTH);
        String gstNumber = normalizeGstin(command.gstNumber());
        String slug = resolveSlug(command, currentClinicProfileId, clinicName, displayName, city);
        return new NormalizedClinicProfile(
                clinicName,
                displayName,
                phone,
                email,
                addressLine1,
                addressLine2,
                city,
                state,
                country,
                postalCode,
                registrationNumber,
                gstNumber,
                slug
        );
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant is required.");
        }
    }

    private String requireBusinessText(String value, String fieldLabel, int maxLength) {
        String normalized = normalizeRequiredText(value, fieldLabel);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String fieldLabel) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldLabel + " is required.");
        }
        if (containsControlCharacters(normalized)) {
            throw new IllegalArgumentException(fieldLabel + " must not contain control characters.");
        }
        return normalized;
    }

    private String requirePhone(String value) {
        String normalized = normalizeRequiredText(value, "Phone");
        String digits = normalized.replaceAll("[^0-9]", "");
        if (!INDIAN_MOBILE_PATTERN.matcher(digits).matches()) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number.");
        }
        return digits;
    }

    private String requireEmail(String value) {
        String normalized = normalizeRequiredText(value, "Email");
        if (normalized.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Email must be " + MAX_EMAIL_LENGTH + " characters or fewer.");
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        return normalized;
    }

    private String requirePostalCode(String value, String country) {
        String normalized = normalizeRequiredText(value, "Postal code");
        if (isIndiaCountry(country)) {
            String digits = normalized.replaceAll("\\D", "");
            if (!digits.matches("^\\d{6}$")) {
                throw new IllegalArgumentException("Enter a valid 6-digit PIN code.");
            }
            return digits;
        }
        if (!NON_INDIA_POSTAL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid postal code.");
        }
        return normalized;
    }

    private String normalizeGstin(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.length() > MAX_GST_LENGTH) {
            throw new IllegalArgumentException("GST number must be " + MAX_GST_LENGTH + " characters or fewer.");
        }
        if (!GSTIN_PATTERN.matcher(upper).matches()) {
            throw new IllegalArgumentException("Enter a valid GSTIN.");
        }
        return upper;
    }

    private String normalizeOptionalText(String value, String fieldLabel, int maxLength) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (containsControlCharacters(normalized)) {
            throw new IllegalArgumentException(fieldLabel + " must not contain control characters.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private String resolveSlug(ClinicProfileUpsertCommand command, UUID currentClinicProfileId, String clinicName, String displayName, String city) {
        String provided = normalize(command.slug());
        if (!StringUtils.hasText(provided)) {
            String base = slugify(firstNonBlank(displayName, clinicName, city, "clinic"));
            return ensureUniqueGeneratedSlug(base, currentClinicProfileId);
        }
        String normalized = provided.toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException("Public slug must be " + MAX_SLUG_LENGTH + " characters or fewer.");
        }
        if (!PUBLIC_SLUG_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid public slug.");
        }
        if (isSlugTaken(normalized, currentClinicProfileId)) {
            throw new IllegalArgumentException("This public slug is already in use. Choose another one.");
        }
        return normalized;
    }

    private String ensureUniqueGeneratedSlug(String requestedSlug, UUID currentClinicProfileId) {
        String base = StringUtils.hasText(requestedSlug) ? requestedSlug : "clinic";
        int suffix = 1;
        String candidate = buildSlugCandidate(base, suffix);
        while (isSlugTaken(candidate, currentClinicProfileId)) {
            suffix++;
            candidate = buildSlugCandidate(base, suffix);
        }
        return candidate;
    }

    private String buildSlugCandidate(String base, int suffix) {
        String candidateBase = slugify(base);
        if (!StringUtils.hasText(candidateBase)) {
            candidateBase = "clinic";
        }
        String suffixText = suffix <= 1 ? "" : "-" + suffix;
        int maxBaseLength = MAX_SLUG_LENGTH - suffixText.length();
        if (candidateBase.length() > maxBaseLength) {
            candidateBase = candidateBase.substring(0, maxBaseLength).replaceAll("(^-|-$)", "");
        }
        if (!StringUtils.hasText(candidateBase)) {
            candidateBase = "clinic";
        }
        String candidate = candidateBase + suffixText;
        if (candidate.length() > MAX_SLUG_LENGTH) {
            candidate = candidate.substring(0, MAX_SLUG_LENGTH).replaceAll("(^-|-$)", "");
        }
        return candidate;
    }

    private boolean isSlugTaken(String slug, UUID currentClinicProfileId) {
        return repository.findBySlugIgnoreCase(slug)
                .filter(existing -> currentClinicProfileId == null || !currentClinicProfileId.equals(existing.getId()))
                .isPresent();
    }

    private boolean isIndiaCountry(String country) {
        return country != null && country.trim().equalsIgnoreCase("india");
    }

    private boolean containsControlCharacters(String value) {
        return value != null && CONTROL_CHARACTERS.matcher(value).find();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String slugify(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isSlugConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("slug") || normalized.contains("uq_clinic_profiles_slug")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
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
        details.put("publicListingEnabled", entity.isPublicListingEnabled());
        details.put("slug", entity.getSlug());

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

    private record NormalizedClinicProfile(
            String clinicName,
            String displayName,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String country,
            String postalCode,
            String registrationNumber,
            String gstNumber,
            String slug
    ) {
    }
}
