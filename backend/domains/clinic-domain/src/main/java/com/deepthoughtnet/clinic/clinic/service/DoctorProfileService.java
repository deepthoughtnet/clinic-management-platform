package com.deepthoughtnet.clinic.clinic.service;

import com.deepthoughtnet.clinic.clinic.db.DoctorProfileEntity;
import com.deepthoughtnet.clinic.clinic.db.DoctorProfileRepository;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfilePhotoRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DoctorProfileService {
    private static final Logger log = LoggerFactory.getLogger(DoctorProfileService.class);

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

    @Transactional
    public DoctorProfileRecord upsert(UUID tenantId, UUID doctorUserId, DoctorProfileUpsertCommand command) {
        requireTenant(tenantId);
        requireDoctor(doctorUserId);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        DoctorProfileEntity entity = doctorProfileRepository.findByTenantIdAndDoctorUserId(tenantId, doctorUserId)
                .orElseGet(() -> DoctorProfileEntity.create(tenantId, doctorUserId));
        String registrationNumber = normalizeNullable(command.registrationNumber());
        if (StringUtils.hasText(registrationNumber)) {
            doctorProfileRepository.findFirstByTenantIdAndActiveTrueAndRegistrationNumberIgnoreCase(tenantId, registrationNumber)
                    .filter(existing -> !doctorUserId.equals(existing.getDoctorUserId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Doctor registration number already exists for this clinic.");
                    });
        }
        List<String> specializations = normalizeSpecializations(command.specializations(), command.specialization());
        BigDecimal opdFee = command.opdFee() != null ? command.opdFee() : command.consultationFee();
        BigDecimal legacyConsultationFee = command.consultationFee() != null ? command.consultationFee() : opdFee;
        entity.update(
                normalizeNullable(command.mobile()),
                specializations.isEmpty() ? normalizeNullable(command.specialization()) : specializations.get(0),
                specializations,
                normalizeNullable(command.qualification()),
                registrationNumber,
                normalizeNullable(command.consultationRoom()),
                legacyConsultationFee,
                opdFee,
                command.followUpFee(),
                command.emergencyFee(),
                command.yearsOfExperience(),
                command.age(),
                command.active(),
                command.publicListingEnabled(),
                normalizeNullable(command.slug())
        );
        return toRecord(doctorProfileRepository.save(entity));
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
        String fileName = normalizeNullable(originalFilename);
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

    @Transactional(readOnly = true)
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
                entity.getAge(),
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

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeSpecializations(List<String> specializations, String fallback) {
        List<String> values = new ArrayList<>();
        if (specializations != null) {
            for (String value : specializations) {
                String normalized = normalizeNullable(value);
                if (normalized != null && !values.contains(normalized)) {
                    values.add(normalized);
                }
            }
        }
        if (values.isEmpty() && StringUtils.hasText(fallback)) {
            for (String token : fallback.split(",")) {
                String normalized = normalizeNullable(token);
                if (normalized != null && !values.contains(normalized)) {
                    values.add(normalized);
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
                String normalized = normalizeNullable(token);
                if (normalized != null && !values.contains(normalized)) {
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
                String normalized = normalizeNullable(token);
                if (normalized != null && !values.contains(normalized)) {
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
        String normalized = normalizeNullable(contentType);
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
}
