package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalDocumentAiOps;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalDocumentService {
    private static final long MAX_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "bat", "cmd", "com", "msi", "ps1", "vbs", "js", "jar", "sh", "php", "pl", "scr", "hta", "apk", "bin"
    );

    private final ClinicalDocumentRepository repository;
    private final ObjectStorageService storageService;
    private final AppUserRepository appUserRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final String storageBucket;

    public ClinicalDocumentService(
            ClinicalDocumentRepository repository,
            ObjectStorageService storageService,
            AppUserRepository appUserRepository,
            AuditEventPublisher auditEventPublisher,
            @Value("${clinic.storage.minio.bucket:clinic-documents}") String storageBucket
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.appUserRepository = appUserRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.storageBucket = storageBucket;
    }

    @Transactional(readOnly = true)
    public List<ClinicalDocumentRecord> listByPatient(UUID tenantId, UUID patientId) {
        return listByPatient(tenantId, patientId, null, null, null, null, null, null);
    }

    @Transactional
    public List<ClinicalDocumentRecord> listByPatient(
            UUID tenantId,
            UUID patientId,
            ClinicalDocumentType documentType,
            LocalDate reportDateFrom,
            LocalDate reportDateTo,
            String uploadSource,
            UUID consultationId,
            String search
    ) {
        String searchTerm = normalizeNullable(search);
        String uploadSourceFilter = normalizeNullable(uploadSource);

        return repository.findByTenantIdAndPatientIdAndActiveTrueOrderByCreatedAtDesc(tenantId, patientId).stream()
                .filter(entity -> documentType == null || entity.getDocumentType() == documentType)
                .filter(entity -> consultationId == null || consultationId.equals(entity.getConsultationId()))
                .filter(entity -> uploadSourceFilter == null || uploadSourceFilter.equalsIgnoreCase(entity.getUploadSource()))
                .filter(entity -> reportDateFrom == null || (entity.getReportDate() != null && !entity.getReportDate().isBefore(reportDateFrom)))
                .filter(entity -> reportDateTo == null || (entity.getReportDate() != null && !entity.getReportDate().isAfter(reportDateTo)))
                .filter(entity -> matchesSearch(entity, searchTerm))
                .map(entity -> repairPublishedLabDocumentIfNeeded(tenantId, entity))
                .map(this::toRecord)
                .sorted(Comparator.comparing(ClinicalDocumentRecord::createdAt).reversed())
                .toList();
    }

    @Transactional
    public ClinicalDocumentRecord get(UUID tenantId, UUID id) {
        return toRecord(repairPublishedLabDocumentIfNeeded(tenantId, findTenantDocument(tenantId, id)));
    }

    @Transactional
    public String downloadUrl(UUID tenantId, UUID id, Duration ttl) {
        ClinicalDocumentEntity document = repairPublishedLabDocumentIfNeeded(tenantId, findTenantDocument(tenantId, id));
        String storageKey = requireStorageKey(document.getStorageObjectKey(), "download");
        return storageService.generatePresignedDownloadUrl(storageKey, ttl);
    }

    @Transactional
    public byte[] downloadBytes(UUID tenantId, UUID id) {
        ClinicalDocumentEntity document = repairPublishedLabDocumentIfNeeded(tenantId, findTenantDocument(tenantId, id));
        String storageKey = requireStorageKey(resolveStorageKey(document, tenantId), "download");
        byte[] bytes = storageService.getObjectBytes(storageKey);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Unable to download document because stored content is empty for key " + storageKey);
        }
        if (document.getSizeBytes() != bytes.length) {
            document.updatePublishedMetadata(
                    document.getDocumentType(),
                    document.getTitle(),
                    document.getDescription(),
                    document.getReportDate(),
                    document.getUploadedByName(),
                    document.getUploadSource(),
                    document.getFileName(),
                    document.getContentType(),
                    bytes.length,
                    isValidStorageKey(document.getStorageBucket()) ? document.getStorageBucket() : storageBucket,
                    storageKey,
                    document.getChecksumSha256(),
                    document.getVisibility(),
                    document.getVerificationStatus(),
                    document.getOcrStatus(),
                    document.getAiIndexStatus(),
                    document.getUpdatedBy()
            );
            repository.save(document);
        }
        return bytes;
    }

    @Transactional
    public ClinicalDocumentRecord upload(ClinicalDocumentUploadCommand command) {
        validate(command);
        String fileName = sanitizeFilename(command.originalFilename());
        String extension = fileExtension(fileName);
        String mediaType = normalizeMediaType(command.mediaType(), fileName);
        validateFileType(extension, mediaType);

        UUID documentId = UUID.randomUUID();
        String checksum = sha256(command.bytes());
        String title = normalizeRequired(command.title(), "Title is required");
        String uploadSource = normalizeUploadSource(command.uploadSource());
        String visibility = normalizeVisibility(command.visibility());
        String verificationStatus = "UNVERIFIED";
        String ocrStatus = "NOT_STARTED";
        String aiIndexStatus = "NOT_STARTED";
        String uploadedByName = resolveUploadedByName(command.tenantId(), command.uploadedByAppUserId());
        String sourceModule = normalizeNullable(command.sourceModule());
        String sourceEntityId = normalizeNullable(command.sourceEntityId());
        String description = normalizeNullable(command.notes());
        String storageKey = resolvePatientDocumentStorageKey(command.tenantId(), command.patientId(), documentId, fileName);
        if (repository.existsByTenantIdAndStorageObjectKey(command.tenantId(), storageKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document storage key already exists");
        }

        storageService.putObject(storageKey, mediaType, command.bytes());
        try {
            ClinicalDocumentEntity saved = repository.save(ClinicalDocumentEntity.create(
                    documentId,
                    command.tenantId(),
                    command.patientId(),
                    command.consultationId(),
                    null,
                    command.uploadedByAppUserId(),
                    command.documentType(),
                    title,
                    description,
                    command.reportDate(),
                    uploadedByName,
                    uploadSource,
                    fileName,
                    mediaType,
                    command.bytes().length,
                    storageBucket,
                    storageKey,
                    checksum,
                    visibility,
                    verificationStatus,
                    ocrStatus,
                    aiIndexStatus,
                    sourceModule,
                    sourceEntityId,
                    command.uploadedByAppUserId(),
                    command.uploadedByAppUserId()
            ));
            auditEventPublisher.record(new AuditEventCommand(
                    command.tenantId(),
                    AuditEntityType.DOCUMENT,
                    saved.getId(),
                    "PATIENT_DOCUMENT_UPLOADED",
                    command.uploadedByAppUserId(),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    "Uploaded patient document",
                    "{\"patientId\":\"%s\",\"documentType\":\"%s\",\"uploadSource\":\"%s\"}".formatted(command.patientId(), command.documentType(), uploadSource)
            ));
            return toRecord(saved);
        } catch (RuntimeException ex) {
            storageService.deleteObjectQuietly(storageKey);
            throw ex;
        }
    }

    @Transactional
    public ClinicalDocumentRecord publishLabReport(ClinicalDocumentUploadCommand command) {
        validate(command);
        String fileName = sanitizeFilename(command.originalFilename());
        String extension = fileExtension(fileName);
        String mediaType = normalizeMediaType(command.mediaType(), fileName);
        validateFileType(extension, mediaType);

        UUID tenantId = command.tenantId();
        UUID patientId = command.patientId();
        UUID uploadedByAppUserId = command.uploadedByAppUserId();
        String title = normalizeRequired(command.title(), "Title is required");
        String uploadSource = normalizeUploadSource(command.uploadSource());
        String visibility = "PATIENT_VISIBLE";
        String verificationStatus = "PUBLISHED";
        String ocrStatus = "COMPLETED";
        String aiIndexStatus = "COMPLETED";
        String uploadedByName = resolveUploadedByName(tenantId, uploadedByAppUserId);
        String sourceModule = normalizeNullable(command.sourceModule());
        String sourceEntityId = normalizeNullable(command.sourceEntityId());
        String description = normalizeNullable(command.notes());
        LocalDate reportDate = command.reportDate();
        String checksum = sha256(command.bytes());

        ClinicalDocumentEntity existing = repository
                .findFirstByTenantIdAndSourceModuleAndSourceEntityIdAndDocumentTypeAndActiveTrueOrderByCreatedAtDesc(
                        tenantId,
                        sourceModule,
                        sourceEntityId,
                        ClinicalDocumentType.LAB_REPORT
                )
                .orElse(null);

        if (existing != null) {
            String storageKey = isValidStorageKey(existing.getStorageKey())
                    ? existing.getStorageKey()
                    : resolvePatientDocumentStorageKey(tenantId, patientId, existing.getId(), fileName);
            if (!isValidStorageKey(storageKey)) {
                throw new IllegalStateException("Unable to publish lab report: invalid storage key resolved for existing document");
            }
            storageService.putObject(storageKey, mediaType, command.bytes());
            existing.updatePublishedMetadata(
                    ClinicalDocumentType.LAB_REPORT,
                    title,
                    description,
                    reportDate,
                    uploadedByName,
                    uploadSource,
                    fileName,
                    mediaType,
                    command.bytes().length,
                    storageBucket,
                    storageKey,
                    checksum,
                    visibility,
                    verificationStatus,
                    ocrStatus,
                    aiIndexStatus,
                    uploadedByAppUserId
            );
            ClinicalDocumentEntity saved = repository.save(existing);
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    AuditEntityType.DOCUMENT,
                    saved.getId(),
                    "PATIENT_DOCUMENT_PUBLISHED",
                    uploadedByAppUserId,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    "Published lab report document",
                    "{\"patientId\":\"%s\",\"documentType\":\"LAB_REPORT\",\"sourceModule\":\"%s\",\"sourceEntityId\":\"%s\"}".formatted(
                            patientId,
                            sourceModule,
                            sourceEntityId
                    )
            ));
            return toRecord(saved);
        }

        UUID documentId = UUID.randomUUID();
        String storageKey = resolvePatientDocumentStorageKey(tenantId, patientId, documentId, fileName);
        if (!isValidStorageKey(storageKey)) {
            throw new IllegalStateException("Unable to publish lab report: invalid storage key generated");
        }
        if (repository.existsByTenantIdAndStorageObjectKey(tenantId, storageKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document storage key already exists");
        }

        storageService.putObject(storageKey, mediaType, command.bytes());
        try {
            ClinicalDocumentEntity saved = repository.save(ClinicalDocumentEntity.create(
                    documentId,
                    tenantId,
                    patientId,
                    command.consultationId(),
                    null,
                    uploadedByAppUserId,
                    ClinicalDocumentType.LAB_REPORT,
                    title,
                    description,
                    reportDate,
                    uploadedByName,
                    uploadSource,
                    fileName,
                    mediaType,
                    command.bytes().length,
                    storageBucket,
                    storageKey,
                    checksum,
                    visibility,
                    verificationStatus,
                    ocrStatus,
                    aiIndexStatus,
                    sourceModule,
                    sourceEntityId,
                    uploadedByAppUserId,
                    uploadedByAppUserId
            ));
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    AuditEntityType.DOCUMENT,
                    saved.getId(),
                    "PATIENT_DOCUMENT_PUBLISHED",
                    uploadedByAppUserId,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    "Published lab report document",
                    "{\"patientId\":\"%s\",\"documentType\":\"LAB_REPORT\",\"sourceModule\":\"%s\",\"sourceEntityId\":\"%s\"}".formatted(
                            patientId,
                            sourceModule,
                            sourceEntityId
                    )
            ));
            return toRecord(saved);
        } catch (RuntimeException ex) {
            storageService.deleteObjectQuietly(storageKey);
            throw ex;
        }
    }

    @Transactional
    public ClinicalDocumentRecord patch(UUID tenantId, UUID id, ClinicalDocumentPatchCommand command, UUID actorAppUserId) {
        ClinicalDocumentEntity document = findTenantDocument(tenantId, id);
        String title = normalizeRequired(command.title(), "Title is required");
        document.updateMetadata(
                command.documentType() == null ? document.getDocumentType() : command.documentType(),
                title,
                normalizeNullable(command.description()),
                command.reportDate(),
                command.visibility() == null ? document.getVisibility() : normalizeVisibility(command.visibility()),
                command.verificationStatus() == null ? document.getVerificationStatus() : normalizeVerificationStatus(command.verificationStatus()),
                resolveUploadedByName(tenantId, document.getUploadedByUserId()),
                document.getUploadSource(),
                actorAppUserId
        );
        ClinicalDocumentEntity saved = repository.save(document);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                saved.getId(),
                "PATIENT_DOCUMENT_UPDATED",
                actorAppUserId,
                OffsetDateTime.now(ZoneOffset.UTC),
                "Updated patient document metadata",
                "{\"patientId\":\"%s\",\"documentId\":\"%s\"}".formatted(saved.getPatientId(), saved.getId())
        ));
        return toRecord(saved);
    }

    @Transactional
    public void delete(UUID tenantId, UUID id, UUID actorAppUserId) {
        ClinicalDocumentEntity document = findTenantDocument(tenantId, id);
        document.softDelete(actorAppUserId);
        repository.save(document);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                id,
                "PATIENT_DOCUMENT_DELETED",
                actorAppUserId,
                OffsetDateTime.now(ZoneOffset.UTC),
                "Soft deleted patient document",
                "{\"patientId\":\"%s\",\"documentId\":\"%s\"}".formatted(document.getPatientId(), id)
        ));
    }

    private ClinicalDocumentEntity findTenantDocument(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndIdAndActiveTrue(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private void validate(ClinicalDocumentUploadCommand command) {
        if (command == null || command.tenantId() == null || command.patientId() == null || command.uploadedByAppUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing document upload context");
        }
        if (command.documentType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        if (command.originalFilename() == null || command.originalFilename().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }
        if (command.bytes() == null || command.bytes().length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document file is required");
        }
        if (command.bytes().length > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document must be 25 MB or smaller");
        }
        String fileName = sanitizeFilename(command.originalFilename());
        String extension = fileExtension(fileName);
        String mediaType = normalizeMediaType(command.mediaType(), fileName);
        validateFileType(extension, mediaType);
    }

    private boolean matchesSearch(ClinicalDocumentEntity entity, String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return true;
        }
        String haystack = String.join(" ",
                safeLower(entity.getTitle()),
                safeLower(entity.getDescription()),
                safeLower(entity.getFileName()),
                safeLower(entity.getUploadedByName()),
                safeLower(entity.getSourceModule()),
                safeLower(entity.getSourceEntityId()));
        return haystack.contains(searchTerm.toLowerCase(Locale.ROOT));
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeMediaType(String mediaType, String filename) {
        String value = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        if (value.equals("image/jpg")) {
            return "image/jpeg";
        }
        if (!value.isBlank()) {
            return value;
        }
        String extension = fileExtension(filename);
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    private void validateFileType(String extension, String mediaType) {
        if (extension.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File extension is required");
        }
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Executable files are not allowed");
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPG, JPEG, and PNG files are allowed");
        }
        String expectedMediaType = switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
        if (!expectedMediaType.equals(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File type does not match the allowed MIME types");
        }
    }

    private String sanitizeFilename(String filename) {
        String value = filename == null ? "" : filename.trim();
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }
        String normalized = value.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String fileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        int index = lower.lastIndexOf('.');
        if (index < 0 || index == lower.length() - 1) {
            return "";
        }
        return lower.substring(index + 1);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to checksum document", ex);
        }
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isValidStorageKey(String value) {
        String normalized = normalizeNullable(value);
        return normalized != null && !"undefined".equalsIgnoreCase(normalized);
    }

    private String requireStorageKey(String value, String action) {
        String normalized = normalizeNullable(value);
        if (!isValidStorageKey(normalized)) {
            throw new IllegalStateException("Unable to " + action + " document because storage key is missing or invalid");
        }
        return normalized;
    }

    private String resolvePatientDocumentStorageKey(UUID tenantId, UUID patientId, UUID documentId, String fileName) {
        String storageKey = storageService.buildPatientDocumentStorageKey(tenantId, patientId, documentId, fileName);
        if (isValidStorageKey(storageKey)) {
            return storageKey;
        }
        String fallbackKey = "tenant/%s/patients/%s/documents/%s/%s".formatted(
                tenantId,
                patientId,
                documentId,
                sanitizeFilename(fileName)
        );
        if (isValidStorageKey(fallbackKey)) {
            return fallbackKey;
        }
        return storageKey;
    }

    private ClinicalDocumentEntity repairPublishedLabDocumentIfNeeded(UUID tenantId, ClinicalDocumentEntity document) {
        if (document == null || document.getDocumentType() != ClinicalDocumentType.LAB_REPORT) {
            return document;
        }
        boolean needsStorageKeyRepair = !isValidStorageKey(document.getStorageKey());
        boolean needsSizeRepair = document.getSizeBytes() <= 0;
        boolean needsBucketRepair = !isValidStorageKey(document.getStorageBucket())
                || !storageBucket.equals(document.getStorageBucket());
        if (!needsStorageKeyRepair && !needsSizeRepair && !needsBucketRepair) {
            return document;
        }

        String candidateStorageKey = resolveStorageKey(document, tenantId);
        if (!isValidStorageKey(candidateStorageKey)) {
            return document;
        }

        long storageSize = -1L;
        try {
            storageSize = storageService.statObjectSize(candidateStorageKey);
        } catch (RuntimeException ex) {
            storageSize = -1L;
        }
        if (storageSize <= 0) {
            byte[] bytes;
            try {
                bytes = storageService.getObjectBytes(candidateStorageKey);
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Unable to read published lab report from storage for key " + candidateStorageKey, ex);
            }
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("Unable to read published lab report from storage for key " + candidateStorageKey);
            }
            storageSize = bytes.length;
        }

        String storageBucketValue = isValidStorageKey(document.getStorageBucket()) ? document.getStorageBucket() : storageBucket;
        document.updatePublishedMetadata(
                document.getDocumentType(),
                document.getTitle(),
                document.getDescription(),
                document.getReportDate(),
                document.getUploadedByName(),
                document.getUploadSource(),
                document.getFileName(),
                document.getContentType(),
                storageSize,
                storageBucketValue,
                candidateStorageKey,
                document.getChecksumSha256(),
                document.getVisibility(),
                document.getVerificationStatus(),
                document.getOcrStatus(),
                document.getAiIndexStatus(),
                document.getUpdatedBy()
        );
        return repository.save(document);
    }

    private String resolveStorageKey(ClinicalDocumentEntity document, UUID tenantId) {
        String storageKey = normalizeNullable(document.getStorageKey());
        if (isValidStorageKey(storageKey)) {
            return storageKey;
        }
        return resolvePatientDocumentStorageKey(
                tenantId,
                document.getPatientId(),
                document.getId(),
                document.getOriginalFilename()
        );
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeUploadSource(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "OTHER" : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeVisibility(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "INTERNAL_ONLY" : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeVerificationStatus(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "UNVERIFIED" : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String sourceModule, String defaultValue) {
        return defaultValue;
    }

    private String resolveUploadedByName(UUID tenantId, UUID appUserId) {
        return appUserRepository.findByTenantIdAndId(tenantId, appUserId)
                .map(user -> {
                    String displayName = user.getDisplayName();
                    if (displayName == null || displayName.isBlank()) {
                        displayName = user.getEmail();
                    }
                    return displayName == null || displayName.isBlank() ? appUserId.toString() : displayName.trim();
                })
                .orElse(appUserId.toString());
    }

    private ClinicalDocumentRecord toRecord(ClinicalDocumentEntity entity) {
        return new ClinicalDocumentRecord(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientId(),
                entity.getConsultationId(),
                entity.getSourceModule(),
                entity.getSourceEntityId(),
                entity.getUploadedByUserId(),
                entity.getUploadedByName(),
                entity.getDocumentType(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getReportDate(),
                entity.getUploadSource(),
                entity.getOriginalFilename(),
                entity.getMediaType(),
                entity.getSizeBytes(),
                entity.getChecksumSha256(),
                entity.getStorageBucket(),
                entity.getStorageKey(),
                entity.getVisibility(),
                entity.getVerificationStatus(),
                entity.getOcrStatus(),
                entity.getAiIndexStatus(),
                entity.getAiExtractionStatus(),
                entity.getAiExtractionProvider(),
                entity.getAiExtractionModel(),
                entity.getAiExtractionConfidence(),
                entity.getAiExtractionSummary(),
                entity.getAiExtractionStructuredJson(),
                entity.getAiExtractionReviewNotes(),
                entity.getAiExtractionAcceptedJson(),
                entity.getAiExtractionOverrideReason(),
                entity.getAiExtractionReviewedByAppUserId(),
                entity.getAiExtractionReviewedAt(),
                toAiOps(entity),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ClinicalDocumentAiOps toAiOps(ClinicalDocumentEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ClinicalDocumentAiOps(
                entity.getLastAiRetryAt() == null ? null : entity.getLastAiRetryAt().toString(),
                entity.getLastAiRetryStatus(),
                entity.getLastAiRetryMessage(),
                entity.getLastAiRetryJobId() == null ? null : entity.getLastAiRetryJobId().toString(),
                entity.getLastMemoryRepairAt() == null ? null : entity.getLastMemoryRepairAt().toString(),
                entity.getLastMemoryRepairStatus(),
                entity.getLastMemoryRepairMessage(),
                entity.getLastMemoryRepairBy() == null ? null : entity.getLastMemoryRepairBy().toString(),
                entity.getLastMemoryRepairDeletedPendingConceptCount(),
                entity.getLastMemoryRepairInsertedConceptCount(),
                entity.getLastMemoryRepairSkippedAcceptedConceptCount(),
                entity.getLastMemoryRepairFilteredPollutedConceptCount(),
                entity.getLastMemoryRepairCorrectedValues()
        );
    }
}
