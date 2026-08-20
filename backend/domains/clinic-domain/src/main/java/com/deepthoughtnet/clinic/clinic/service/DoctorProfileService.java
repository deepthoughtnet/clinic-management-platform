package com.deepthoughtnet.clinic.clinic.service;

import com.deepthoughtnet.clinic.clinic.db.DoctorProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.DoctorProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfilePhotoRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DoctorProfileService {
    private static final Logger log = LoggerFactory.getLogger(DoctorProfileService.class);

    private static final int MAX_SPECIALIZATION_LENGTH = 128;
    private static final int MAX_QUALIFICATION_LENGTH = 256;
    private static final int MAX_REGISTRATION_NUMBER_LENGTH = 128;
    private static final int MAX_CONSULTATION_ROOM_LENGTH = 128;
    private static final int MAX_SLUG_LENGTH = 192;
    private static final int MAX_MOBILE_LENGTH = 32;
    private static final int MAX_FEE_SCALE = 2;
    private static final BigDecimal MAX_FEE = new BigDecimal("9999999.99");
    private static final int MAX_EXPERIENCE_YEARS = 80;
    private static final int MIN_DOCTOR_AGE = 18;
    private static final int MAX_DOCTOR_AGE = 100;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\u0000-\\u001F\\u007F]");
    private static final Pattern PUBLIC_SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern REGISTRATION_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9/._-]{2,127}$");
    private static final Pattern INDIAN_MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");

    private final DoctorProfileRepository doctorProfileRepository;
    private final ObjectStorageService storageService;

    public DoctorProfileService(
            DoctorProfileRepository doctorProfileRepository,
            ObjectStorageService storageService
    ) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public Optional<DoctorProfileRecord> findByDoctorUserId(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        return doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<DoctorProfileRecord> findBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return Optional.empty();
        }
        return doctorProfileRepository.findBySlugIgnoreCase(slug.trim()).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public List<DoctorProfileRecord> findByTenantIdAndActive(UUID tenantId) {
        requireTenant(tenantId);
        return doctorProfileRepository.findByTenantIdAndActiveTrue(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorProfileRecord> findAll() {
        return doctorProfileRepository.findAll().stream().map(this::toRecord).toList();
    }

    @Transactional
    public DoctorProfileRecord upsert(UUID tenantId, UUID doctorUserId, DoctorProfileUpsertCommand command) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }

        DoctorProfileEntity entity = doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .orElseGet(() -> DoctorProfileEntity.create(tenantId, doctorUserId));

        String mobile = normalizeMobile(command.mobile());
        List<String> specializations = normalizeSpecializations(command.specializations(), command.specialization());
        if (specializations.isEmpty()) {
            throw new IllegalArgumentException("Select at least one specialization.");
        }
        String specialization = specializations.getFirst();
        String qualification = normalizeRequiredText(command.qualification(), "Qualification", MAX_QUALIFICATION_LENGTH);
        String registrationNumber = normalizeRegistrationNumber(command.registrationNumber());
        String consultationRoom = normalizeOptionalText(command.consultationRoom(), "Consultation room", MAX_CONSULTATION_ROOM_LENGTH);
        BigDecimal consultationFee = normalizeOptionalMoney(command.consultationFee(), "Enter a valid consultation fee.");
        BigDecimal opdFee = normalizeRequiredMoney(command.opdFee(), "OPD fee is required.", "Enter a valid OPD fee.");
        BigDecimal followUpFee = normalizeRequiredMoney(command.followUpFee(), "Follow-up fee is required.", "Enter a valid follow-up fee.");
        BigDecimal emergencyFee = normalizeRequiredMoney(command.emergencyFee(), "Emergency fee is required.", "Enter a valid emergency fee.");
        Integer yearsOfExperience = normalizeRequiredWholeNumber(command.yearsOfExperience(), "Years of experience is required.", "Enter a valid number of years of experience.");
        LocalDate dateOfBirth = normalizeDateOfBirth(command.dateOfBirth(), command.age(), entity.getDateOfBirth());
        Integer derivedAge = dateOfBirth != null
                ? calculateAge(dateOfBirth)
                : (entity.getDateOfBirth() != null ? calculateAge(entity.getDateOfBirth()) : (command.age() != null ? command.age() : entity.getAge()));
        if (dateOfBirth != null) {
            validateDateOfBirth(dateOfBirth, yearsOfExperience);
        }
        if (dateOfBirth == null && entity.getDateOfBirth() == null) {
            throw new IllegalArgumentException("Date of birth is required.");
        }
        String slug = resolveSlug(command.slug(), command.doctorName(), specialization, entity.getId(), entity.getSlug());

        String normalizedRegistrationNumber = registrationNumber;
        if (StringUtils.hasText(normalizedRegistrationNumber)) {
            doctorProfileRepository.findFirstByTenantIdAndActiveTrueAndRegistrationNumberIgnoreCase(tenantId, normalizedRegistrationNumber)
                    .filter(existing -> !doctorUserId.equals(existing.getDoctorUserId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Doctor registration number already exists for this clinic.");
                    });
        }

        try {
            entity.update(
                    mobile,
                    specialization,
                    specializations,
                    qualification,
                    normalizedRegistrationNumber,
                    consultationRoom,
                    consultationFee,
                    opdFee,
                    followUpFee,
                    emergencyFee,
                    yearsOfExperience,
                    derivedAge,
                    dateOfBirth,
                    command.active(),
                    command.publicListingEnabled(),
                    slug
            );
            return toRecord(doctorProfileRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            if (isSlugConstraintViolation(ex)) {
                throw new IllegalArgumentException("This public slug is already in use. Choose another one.", ex);
            }
            throw ex;
        }
    }

    @Transactional
    public DoctorProfileRecord updatePhoto(UUID tenantId, UUID doctorUserId, String originalFilename, String contentType, byte[] bytes) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("photo bytes are required");
        }

        log.info(
                "doctor.profile.photo.upload.started tenantId={} doctorUserId={} originalFilename={} contentType={} incomingSizeBytes={}",
                tenantId,
                doctorUserId,
                originalFilename,
                contentType,
                bytes.length
        );

        DoctorProfileEntity entity = doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .orElseGet(() -> DoctorProfileEntity.create(tenantId, doctorUserId));
        String fileName = normalizeText(originalFilename);
        if (!StringUtils.hasText(fileName)) {
            fileName = "doctor-photo";
        }
        String storageKey = storageService.buildDocumentStorageKey(tenantId, fileName);
        if (!isSupportedPhoto(contentType, fileName)) {
            throw new IllegalArgumentException("Doctor profile photo must be JPG, PNG, or WEBP.");
        }
        if (bytes.length > (10L * 1024L * 1024L)) {
            throw new IllegalArgumentException("Doctor profile photo must be 10 MB or smaller.");
        }

        String oldKey = entity.getPhotoStorageKey();
        String normalizedContentType = normalizePhotoContentType(contentType, fileName);
        try {
            storageService.putObject(storageKey, normalizedContentType, bytes);
            long storedSizeBytes = storageService.statObjectSize(storageKey);
            if (storedSizeBytes <= 0L) {
                throw new IllegalStateException("Stored doctor profile photo is empty.");
            }
            entity.updatePhoto(storageKey, normalizedContentType, storedSizeBytes, fileName);
            DoctorProfileEntity savedEntity = doctorProfileRepository.saveAndFlush(entity);
            DoctorProfileRecord saved = toRecord(savedEntity);
            if (StringUtils.hasText(oldKey) && !oldKey.equals(storageKey)) {
                storageService.deleteObjectQuietly(oldKey);
            }
            log.info(
                    "doctor.profile.photo.upload.completed tenantId={} doctorUserId={} storageKey={} sizeBytes={}",
                    tenantId,
                    doctorUserId,
                    storageKey,
                    storedSizeBytes
            );
            return saved;
        } catch (RuntimeException ex) {
            log.error(
                    "doctor.profile.photo.upload.failed tenantId={} doctorUserId={} storageKey={}",
                    tenantId,
                    doctorUserId,
                    storageKey,
                    ex
            );
            storageService.deleteObjectQuietly(storageKey);
            throw ex;
        }
    }

    @Transactional
    public Optional<DoctorProfileRecord> findByDoctorUserIdWithPhotoRepair(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        return doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .map(this::repairPhotoMetadataIfNeeded)
                .map(this::toRecord);
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public DoctorProfilePhotoRecord downloadPhoto(UUID tenantId, UUID doctorUserId) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        DoctorProfileEntity entity = doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor profile not found for this clinic."));
        if (!isValidStorageKey(entity.getPhotoStorageKey())) {
            throw new IllegalArgumentException("Doctor profile photo is not available.");
        }
        byte[] bytes = storageService.getObjectBytes(entity.getPhotoStorageKey());
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Doctor profile photo content is empty.");
        }
        String contentType = normalizePhotoContentType(entity.getPhotoContentType(), entity.getPhotoOriginalFilename());
        return new DoctorProfilePhotoRecord(
                StringUtils.hasText(entity.getPhotoOriginalFilename()) ? entity.getPhotoOriginalFilename() : "doctor-photo",
                StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                bytes.length,
                bytes
        );
    }

    private DoctorProfileRecord toRecord(DoctorProfileEntity entity) {
        List<String> specializations = parseSpecializations(entity.getSpecializationsJson(), entity.getSpecialization());
        String photoUrl = buildPhotoUrl(entity);
        Integer age = entity.getDateOfBirth() != null ? calculateAge(entity.getDateOfBirth()) : entity.getAge();
        return new DoctorProfileRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getDoctorUserId(),
                entity.getMobile(),
                entity.getSpecialization(),
                specializations,
                entity.getQualification(),
                entity.getRegistrationNumber(),
                entity.getConsultationRoom(),
                entity.getConsultationFee(),
                entity.getOpdFee(),
                entity.getFollowUpFee(),
                entity.getEmergencyFee(),
                entity.getYearsOfExperience(),
                age,
                entity.getDateOfBirth(),
                entity.isActive(),
                entity.isPublicListingEnabled(),
                entity.getSlug(),
                photoUrl,
                entity.getPhotoOriginalFilename(),
                entity.getPhotoContentType(),
                entity.getPhotoSizeBytes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void validateDateOfBirth(LocalDate dateOfBirth, Integer yearsOfExperience) {
        if (dateOfBirth == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            throw new IllegalArgumentException("Date of birth cannot be in the future.");
        }
        int age = calculateAge(dateOfBirth);
        if (age < MIN_DOCTOR_AGE || age > MAX_DOCTOR_AGE) {
            throw new IllegalArgumentException("Enter a valid date of birth.");
        }
        if (yearsOfExperience != null && yearsOfExperience > Math.max(0, age - MIN_DOCTOR_AGE)) {
            throw new IllegalArgumentException("Years of experience cannot exceed the doctor's possible professional experience based on date of birth.");
        }
    }

    private String resolveSlug(String slug, String doctorName, String specialization, UUID currentDoctorProfileId, String currentSlug) {
        String provided = normalizeText(slug);
        if (!StringUtils.hasText(provided)) {
            String existing = normalizeText(currentSlug);
            if (StringUtils.hasText(existing)) {
                return existing.toLowerCase(Locale.ROOT);
            }
            String base = slugify(firstNonBlank(doctorName, specialization, "doctor"));
            return ensureUniqueGeneratedSlug(base, currentDoctorProfileId);
        }
        String normalized = provided.toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_SLUG_LENGTH) {
            throw new IllegalArgumentException("Public slug must be " + MAX_SLUG_LENGTH + " characters or fewer.");
        }
        if (!PUBLIC_SLUG_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid public slug.");
        }
        if (isSlugTaken(normalized, currentDoctorProfileId)) {
            throw new IllegalArgumentException("This public slug is already in use. Choose another one.");
        }
        return normalized;
    }

    private String ensureUniqueGeneratedSlug(String requestedSlug, UUID currentDoctorProfileId) {
        String base = StringUtils.hasText(requestedSlug) ? requestedSlug : "doctor";
        int suffix = 1;
        String candidate = buildSlugCandidate(base, suffix);
        while (isSlugTaken(candidate, currentDoctorProfileId)) {
            suffix++;
            candidate = buildSlugCandidate(base, suffix);
        }
        return candidate;
    }

    private String buildSlugCandidate(String base, int suffix) {
        String candidateBase = slugify(base);
        if (!StringUtils.hasText(candidateBase)) {
            candidateBase = "doctor";
        }
        String suffixText = suffix <= 1 ? "" : "-" + suffix;
        int maxBaseLength = MAX_SLUG_LENGTH - suffixText.length();
        if (candidateBase.length() > maxBaseLength) {
            candidateBase = candidateBase.substring(0, maxBaseLength).replaceAll("(^-|-$)", "");
        }
        if (!StringUtils.hasText(candidateBase)) {
            candidateBase = "doctor";
        }
        String candidate = candidateBase + suffixText;
        if (candidate.length() > MAX_SLUG_LENGTH) {
            candidate = candidate.substring(0, MAX_SLUG_LENGTH).replaceAll("(^-|-$)", "");
        }
        return candidate;
    }

    private boolean isSlugTaken(String slug, UUID currentDoctorProfileId) {
        return doctorProfileRepository.findBySlugIgnoreCase(slug)
                .filter(existing -> currentDoctorProfileId == null || !currentDoctorProfileId.equals(existing.getId()))
                .isPresent();
    }

    private String normalizeRequiredText(String value, String fieldLabel, int maxLength) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldLabel + " is required.");
        }
        if (containsControlCharacters(normalized)) {
            throw new IllegalArgumentException(fieldLabel + " must not contain control characters.");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + " must be " + maxLength + " characters or fewer.");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, String fieldLabel, int maxLength) {
        String normalized = normalizeText(value);
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

    private String normalizeMobile(String value) {
        String normalized = normalizeRequiredText(value, "Mobile number", MAX_MOBILE_LENGTH);
        String digits = normalized.replaceAll("[^0-9]", "");
        if (!INDIAN_MOBILE_PATTERN.matcher(digits).matches()) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number.");
        }
        return digits;
    }

    private String normalizeRegistrationNumber(String value) {
        String normalized = normalizeRequiredText(value, "Registration number", MAX_REGISTRATION_NUMBER_LENGTH).toUpperCase(Locale.ROOT);
        if (!REGISTRATION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Enter a valid doctor registration number.");
        }
        return normalized;
    }

    private BigDecimal normalizeRequiredMoney(BigDecimal value, String requiredMessage, String invalidMessage) {
        if (value == null) {
            throw new IllegalArgumentException(requiredMessage);
        }
        validateMoney(value, invalidMessage);
        return value;
    }

    private BigDecimal normalizeOptionalMoney(BigDecimal value, String invalidMessage) {
        if (value == null) {
            return null;
        }
        validateMoney(value, invalidMessage);
        return value;
    }

    private void validateMoney(BigDecimal value, String invalidMessage) {
        if (value.scale() > MAX_FEE_SCALE || value.stripTrailingZeros().scale() > MAX_FEE_SCALE || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(MAX_FEE) > 0) {
            throw new IllegalArgumentException(invalidMessage);
        }
    }

    private Integer normalizeRequiredWholeNumber(Integer value, String requiredMessage, String invalidMessage) {
        if (value == null) {
            throw new IllegalArgumentException(requiredMessage);
        }
        if (value < 0 || value > MAX_EXPERIENCE_YEARS) {
            throw new IllegalArgumentException(invalidMessage);
        }
        return value;
    }

    private LocalDate normalizeDateOfBirth(LocalDate dateOfBirth, Integer age, LocalDate existingDateOfBirth) {
        if (dateOfBirth == null) {
            if (existingDateOfBirth != null) {
                return existingDateOfBirth;
            }
            if (age == null) {
                return null;
            }
            return null;
        }
        return dateOfBirth;
    }

    private Integer calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    private List<String> normalizeSpecializations(List<String> specializations, String fallback) {
        List<String> values = new ArrayList<>();
        if (specializations != null) {
            for (String value : specializations) {
                String normalized = normalizeText(value);
                if (StringUtils.hasText(normalized)) {
                    if (normalized.length() > MAX_SPECIALIZATION_LENGTH) {
                        throw new IllegalArgumentException("Specialization must be " + MAX_SPECIALIZATION_LENGTH + " characters or fewer.");
                    }
                    if (!values.contains(normalized)) {
                        values.add(normalized);
                    }
                }
            }
        }
        if (values.isEmpty() && StringUtils.hasText(fallback)) {
            for (String token : fallback.split(",")) {
                String normalized = normalizeText(token);
                if (StringUtils.hasText(normalized)) {
                    if (normalized.length() > MAX_SPECIALIZATION_LENGTH) {
                        throw new IllegalArgumentException("Specialization must be " + MAX_SPECIALIZATION_LENGTH + " characters or fewer.");
                    }
                    if (!values.contains(normalized)) {
                        values.add(normalized);
                    }
                }
            }
        }
        return values;
    }

    private List<String> parseSpecializations(String raw, String fallback) {
        if (StringUtils.hasText(raw)) {
            String[] tokens = raw.split("\\|");
            List<String> values = new ArrayList<>();
            for (String token : tokens) {
                String normalized = normalizeText(token);
                if (StringUtils.hasText(normalized) && !values.contains(normalized)) {
                    values.add(normalized);
                }
            }
            if (!values.isEmpty()) {
                return values;
            }
        }
        if (StringUtils.hasText(fallback)) {
            List<String> values = new ArrayList<>();
            for (String token : fallback.split(",")) {
                String normalized = normalizeText(token);
                if (StringUtils.hasText(normalized) && !values.contains(normalized)) {
                    values.add(normalized);
                }
            }
            return values;
        }
        return List.of();
    }

    private boolean isValidStorageKey(String value) {
        return StringUtils.hasText(value) && !"undefined".equalsIgnoreCase(value.trim());
    }

    private boolean isSupportedPhoto(String contentType, String fileName) {
        String normalized = normalizePhotoContentType(contentType, fileName);
        if (normalized == null) {
            return false;
        }
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return "image/jpeg".equals(normalized)
                || "image/png".equals(normalized)
                || "image/webp".equals(normalized)
                || lowerFileName.endsWith(".jpg")
                || lowerFileName.endsWith(".jpeg")
                || lowerFileName.endsWith(".png")
                || lowerFileName.endsWith(".webp");
    }

    private String normalizePhotoContentType(String contentType, String fileName) {
        String normalized = normalizeText(contentType);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if ("image/jpeg".equals(normalized) || "image/png".equals(normalized) || "image/webp".equals(normalized)) {
            return normalized;
        }
        if (StringUtils.hasText(fileName)) {
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (lower.endsWith(".png")) {
                return "image/png";
            }
            if (lower.endsWith(".webp")) {
                return "image/webp";
            }
        }
        return normalized;
    }

    private DoctorProfileEntity repairPhotoMetadataIfNeeded(DoctorProfileEntity entity) {
        if (!isValidStorageKey(entity.getPhotoStorageKey())) {
            return entity;
        }
        boolean needsSizeRepair = entity.getPhotoSizeBytes() == null || entity.getPhotoSizeBytes() <= 0L;
        boolean needsContentTypeRepair = !StringUtils.hasText(entity.getPhotoContentType()) && StringUtils.hasText(entity.getPhotoOriginalFilename());
        if (!needsSizeRepair && !needsContentTypeRepair) {
            return entity;
        }
        if (needsSizeRepair) {
            long sizeBytes = storageService.statObjectSize(entity.getPhotoStorageKey());
            if (sizeBytes <= 0L) {
                throw new IllegalStateException("Stored doctor profile photo is empty.");
            }
            entity.updatePhoto(
                    entity.getPhotoStorageKey(),
                    normalizePhotoContentType(entity.getPhotoContentType(), entity.getPhotoOriginalFilename()),
                    sizeBytes,
                    entity.getPhotoOriginalFilename()
            );
            return doctorProfileRepository.saveAndFlush(entity);
        }
        entity.updatePhoto(
                entity.getPhotoStorageKey(),
                normalizePhotoContentType(entity.getPhotoContentType(), entity.getPhotoOriginalFilename()),
                entity.getPhotoSizeBytes(),
                entity.getPhotoOriginalFilename()
        );
        return doctorProfileRepository.saveAndFlush(entity);
    }

    private String buildPhotoUrl(DoctorProfileEntity entity) {
        if (!isValidStorageKey(entity.getPhotoStorageKey())) {
            return null;
        }
        long version = entity.getUpdatedAt() == null ? 0L : entity.getUpdatedAt().toInstant().toEpochMilli();
        return "/api/doctors/%s/photo?v=%d".formatted(entity.getDoctorUserId(), version);
    }

    private boolean isSlugConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("slug") || normalized.contains("uq_doctor_profiles_slug")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void requireTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
    }

    private void requireDoctor(UUID doctorUserId) {
        if (doctorUserId == null) {
            throw new IllegalArgumentException("doctorUserId is required");
        }
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private boolean containsControlCharacters(String value) {
        return value != null && CONTROL_CHARACTERS.matcher(value).find();
    }

    private String slugify(String value) {
        String normalized = normalizeText(value);
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
}
