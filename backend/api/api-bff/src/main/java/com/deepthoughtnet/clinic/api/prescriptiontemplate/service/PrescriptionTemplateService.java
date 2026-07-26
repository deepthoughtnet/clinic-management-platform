package com.deepthoughtnet.clinic.api.prescriptiontemplate.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateEntity;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateRepository;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateSettings;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionLogoResolver;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTemplateConfig;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionLogoAsset;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

@Service
public class PrescriptionTemplateService implements PrescriptionLogoResolver {
    private static final long MAX_LOGO_SIZE_BYTES = 2L * 1024L * 1024L;
    private static final java.util.Set<String> ALLOWED_LOGO_MIME_TYPES = java.util.Set.of("image/png", "image/jpeg", "image/webp");

    private final PrescriptionTemplateRepository repository;
    private final AuditEventPublisher auditEventPublisher;
    private final ClinicalDocumentService clinicalDocumentService;
    private final ObjectStorageService objectStorageService;

    public PrescriptionTemplateService(
            PrescriptionTemplateRepository repository,
            AuditEventPublisher auditEventPublisher,
            ClinicalDocumentService clinicalDocumentService,
            ObjectStorageService objectStorageService
    ) {
        this.repository = repository;
        this.auditEventPublisher = auditEventPublisher;
        this.clinicalDocumentService = clinicalDocumentService;
        this.objectStorageService = objectStorageService;
    }

    @Transactional(readOnly = true)
    public PrescriptionTemplateRecord getActive(UUID tenantId) {
        return repository.findFirstByTenantIdAndActiveTrueOrderByTemplateVersionDesc(tenantId)
                .map(this::toRecord)
                .orElse(defaultRecord(tenantId));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionTemplateRecord> history(UUID tenantId) {
        return repository.findByTenantIdOrderByTemplateVersionDesc(tenantId).stream().map(this::toRecord).toList();
    }

    @Transactional
    public PrescriptionTemplateRecord save(UUID tenantId, UUID actorAppUserId, PrescriptionTemplateSettings settings) {
        repository.findByTenantIdAndActiveTrue(tenantId).forEach(PrescriptionTemplateEntity::deactivate);
        int nextVersion = repository.findByTenantIdOrderByTemplateVersionDesc(tenantId).stream().findFirst().map(row -> row.getTemplateVersion() + 1).orElse(1);
        PrescriptionTemplateEntity saved = repository.save(PrescriptionTemplateEntity.create(tenantId, nextVersion, actorAppUserId, sanitize(settings)));
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.CLINIC,
                tenantId,
                "PRESCRIPTION_TEMPLATE_UPDATED",
                actorAppUserId,
                OffsetDateTime.now(),
                "Prescription template updated",
                "{\"templateVersion\":" + nextVersion + "}"
        ));
        return toRecord(saved);
    }

    @Transactional
    public PrescriptionTemplateRecord uploadLogo(UUID tenantId, UUID actorAppUserId, MultipartFile file) throws IOException {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId is required");
        }
        if (actorAppUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actorAppUserId is required");
        }
        validateLogo(file);
        byte[] bytes = file.getBytes();
        String originalFilename = sanitize(file.getOriginalFilename());
        String mediaType = normalizeLogoContentType(file.getContentType(), originalFilename);
        validateLogoBytes(mediaType, bytes);
        if ("image/webp".equals(mediaType)) {
            if (!looksLikeWebp(bytes)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo file appears to be corrupt or unsupported.");
            }
        } else if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo file appears to be corrupt or unsupported.");
        }
        ClinicalDocumentRecord document = clinicalDocumentService.upload(new com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentUploadCommand(
                tenantId,
                tenantId,
                null,
                actorAppUserId,
                ClinicalDocumentType.OTHER,
                "Clinic logo",
                null,
                "OTHER",
                "PRESCRIPTION_TEMPLATE",
                "branding-logo",
                "INTERNAL_ONLY",
                originalFilename == null ? "clinic-logo" : originalFilename,
                mediaType,
                bytes,
                "Prescription branding logo"
        ));
        return saveLogoReference(tenantId, actorAppUserId, document.id());
    }

    @Transactional
    public PrescriptionTemplateRecord removeLogo(UUID tenantId, UUID actorAppUserId) {
        return saveLogoReference(tenantId, actorAppUserId, null);
    }

    @Override
    public java.util.Optional<PrescriptionLogoAsset> resolve(UUID tenantId, UUID logoDocumentId) {
        if (tenantId == null || logoDocumentId == null) {
            return java.util.Optional.empty();
        }
        try {
            ClinicalDocumentRecord record = clinicalDocumentService.get(tenantId, logoDocumentId);
            byte[] bytes = clinicalDocumentService.downloadBytes(tenantId, logoDocumentId);
            if (bytes == null || bytes.length == 0) {
                return java.util.Optional.empty();
            }
            String contentType = normalizeLogoContentType(record.mediaType(), record.originalFilename());
            return java.util.Optional.of(new PrescriptionLogoAsset(bytes, contentType, record.originalFilename()));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return java.util.Optional.empty();
            }
            throw ex;
        } catch (IllegalStateException ex) {
            return java.util.Optional.empty();
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    public PrescriptionTemplateConfig toPdfConfig(PrescriptionTemplateRecord record) {
        if (record == null) return PrescriptionTemplateConfig.defaults();
        return new PrescriptionTemplateConfig(
                record.clinicLogoDocumentId() == null ? null : record.clinicLogoDocumentId().toString(),
                record.headerText(), record.footerText(), record.primaryColor(), record.accentColor(), record.disclaimer(),
                record.doctorSignatureText(), record.showQrCode(), record.watermarkText()
        );
    }

    private PrescriptionTemplateSettings sanitize(PrescriptionTemplateSettings settings) {
        return new PrescriptionTemplateSettings(
                settings.clinicLogoDocumentId(), clean(settings.headerText()), clean(settings.footerText()), color(settings.primaryColor(), "#0f766e"),
                color(settings.accentColor(), "#14b8a6"), clean(settings.disclaimer()), clean(settings.doctorSignatureText()), settings.showQrCode(), clean(settings.watermarkText())
        );
    }

    private PrescriptionTemplateRecord saveLogoReference(UUID tenantId, UUID actorAppUserId, UUID logoDocumentId) {
        PrescriptionTemplateEntity entity = repository.findByTenantIdAndActiveTrue(tenantId)
                .stream()
                .findFirst()
                .orElseGet(() -> repository.findByTenantIdOrderByTemplateVersionDesc(tenantId).stream().findFirst().orElse(null));
        if (entity == null) {
            entity = PrescriptionTemplateEntity.create(tenantId, 1, actorAppUserId, new PrescriptionTemplateSettings(
                    logoDocumentId,
                    null,
                    null,
                    "#0f766e",
                    "#14b8a6",
                    null,
                    null,
                    true,
                    null
            ));
        } else {
            entity.deactivate();
            entity = PrescriptionTemplateEntity.create(tenantId, entity.getTemplateVersion() + 1, actorAppUserId, new PrescriptionTemplateSettings(
                    logoDocumentId,
                    entity.getHeaderText(),
                    entity.getFooterText(),
                    entity.getPrimaryColor(),
                    entity.getAccentColor(),
                    entity.getDisclaimer(),
                    entity.getDoctorSignatureText(),
                    entity.isShowQrCode(),
                    entity.getWatermarkText()
            ));
        }
        PrescriptionTemplateEntity saved = repository.save(entity);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.CLINIC,
                tenantId,
                logoDocumentId == null ? "PRESCRIPTION_TEMPLATE_LOGO_REMOVED" : "PRESCRIPTION_TEMPLATE_LOGO_UPLOADED",
                actorAppUserId,
                OffsetDateTime.now(),
                logoDocumentId == null ? "Removed prescription branding logo" : "Updated prescription branding logo",
                "{\"logoDocumentId\":\"%s\"}".formatted(logoDocumentId == null ? "" : logoDocumentId)
        ));
        return toRecord(saved);
    }

    private void validateLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo file is required");
        }
        if (file.getSize() <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo file cannot be empty");
        }
        if (file.getSize() > MAX_LOGO_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo must be 2 MB or smaller");
        }
        String contentType = normalizeLogoContentType(file.getContentType(), file.getOriginalFilename());
        if (!StringUtils.hasText(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo MIME type is required");
        }
        if (!ALLOWED_LOGO_MIME_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo must be PNG, JPG, JPEG, or WEBP");
        }
    }

    private void validateLogoBytes(String mediaType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo file cannot be empty");
        }
        if (!StringUtils.hasText(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo MIME type is required");
        }
        if (!ALLOWED_LOGO_MIME_TYPES.contains(mediaType.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo must be PNG, JPG, JPEG, or WEBP");
        }
    }

    private boolean looksLikeWebp(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        return bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private String normalizeLogoContentType(String contentType, String originalFilename) {
        String normalized = sanitize(contentType);
        if ("image/jpg".equalsIgnoreCase(normalized)) {
            normalized = "image/jpeg";
        }
        if (StringUtils.hasText(normalized) && ALLOWED_LOGO_MIME_TYPES.contains(normalized.toLowerCase(Locale.ROOT))) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        String lowerName = sanitize(originalFilename).toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerName.endsWith(".webp")) {
            return "image/webp";
        }
        return normalized;
    }

    private String sanitize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String color(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim();
        if (!normalized.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid color such as #0F766E");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private PrescriptionTemplateRecord defaultRecord(UUID tenantId) {
        return new PrescriptionTemplateRecord(null, tenantId, 0, true, null, null, null, "#0f766e", "#14b8a6", null, null, true, null, null, null, null);
    }

    private PrescriptionTemplateRecord toRecord(PrescriptionTemplateEntity e) {
        return new PrescriptionTemplateRecord(e.getId(), e.getTenantId(), e.getTemplateVersion(), e.isActive(), e.getClinicLogoDocumentId(), e.getHeaderText(), e.getFooterText(), e.getPrimaryColor(), e.getAccentColor(), e.getDisclaimer(), e.getDoctorSignatureText(), e.isShowQrCode(), e.getWatermarkText(), e.getChangedByAppUserId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
