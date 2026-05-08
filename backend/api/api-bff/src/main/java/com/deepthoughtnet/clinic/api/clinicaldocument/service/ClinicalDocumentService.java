package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ClinicalDocumentService {
    private static final long MAX_SIZE_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final ClinicalDocumentRepository repository;
    private final ObjectStorageService storageService;
    private final AuditEventPublisher auditEventPublisher;

    public ClinicalDocumentService(
            ClinicalDocumentRepository repository,
            ObjectStorageService storageService,
            AuditEventPublisher auditEventPublisher
    ) {
        this.repository = repository;
        this.storageService = storageService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ClinicalDocumentRecord> listByPatient(UUID tenantId, UUID patientId) {
        return repository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public ClinicalDocumentRecord get(UUID tenantId, UUID id) {
        return toRecord(findTenantDocument(tenantId, id));
    }

    @Transactional(readOnly = true)
    public String downloadUrl(UUID tenantId, UUID id, Duration ttl) {
        ClinicalDocumentEntity document = findTenantDocument(tenantId, id);
        return storageService.generatePresignedDownloadUrl(document.getStorageKey(), ttl);
    }

    @Transactional
    public ClinicalDocumentRecord upload(ClinicalDocumentUploadCommand command) {
        validate(command);
        String mediaType = normalizeMediaType(command.mediaType(), command.originalFilename());
        String checksum = sha256(command.bytes());
        String storageKey = storageService.buildDocumentStorageKey(command.tenantId(), command.originalFilename());
        if (repository.existsByTenantIdAndStorageKey(command.tenantId(), storageKey)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document storage key already exists");
        }

        storageService.putObject(storageKey, mediaType, command.bytes());
        try {
            ClinicalDocumentEntity saved = repository.save(ClinicalDocumentEntity.create(
                    command.tenantId(),
                    command.patientId(),
                    command.consultationId(),
                    command.appointmentId(),
                    command.uploadedByAppUserId(),
                    command.documentType(),
                    command.originalFilename().trim(),
                    mediaType,
                    command.bytes().length,
                    checksum,
                    storageKey,
                    normalizeNullable(command.notes()),
                    normalizeNullable(command.referredDoctor()),
                    normalizeNullable(command.referredHospital()),
                    normalizeNullable(command.referralNotes())
            ));
            auditEventPublisher.record(new AuditEventCommand(
                    command.tenantId(),
                    AuditEntityType.DOCUMENT,
                    saved.getId(),
                    "PATIENT_CLINICAL_DOCUMENT_UPLOADED",
                    command.uploadedByAppUserId(),
                    OffsetDateTime.now(),
                    "Uploaded patient clinical document",
                    "{\"patientId\":\"%s\",\"documentType\":\"%s\"}".formatted(command.patientId(), command.documentType())
            ));
            return toRecord(saved);
        } catch (RuntimeException ex) {
            storageService.deleteObjectQuietly(storageKey);
            throw ex;
        }
    }

    private ClinicalDocumentEntity findTenantDocument(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private void validate(ClinicalDocumentUploadCommand command) {
        if (command == null || command.tenantId() == null || command.patientId() == null || command.uploadedByAppUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing document upload context");
        }
        if (command.documentType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
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
        String mediaType = normalizeMediaType(command.mediaType(), command.originalFilename());
        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPG, JPEG, and PNG documents are supported");
        }
    }

    private String normalizeMediaType(String mediaType, String filename) {
        String value = mediaType == null ? "" : mediaType.trim().toLowerCase(Locale.ROOT);
        if (value.equals("image/jpg")) {
            return "image/jpeg";
        }
        if (!value.isBlank()) {
            return value;
        }
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
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

    private ClinicalDocumentRecord toRecord(ClinicalDocumentEntity entity) {
        return new ClinicalDocumentRecord(
                entity.getId(), entity.getTenantId(), entity.getPatientId(), entity.getConsultationId(), entity.getAppointmentId(),
                entity.getUploadedByAppUserId(), entity.getDocumentType(), entity.getOriginalFilename(), entity.getMediaType(),
                entity.getSizeBytes(), entity.getChecksumSha256(), entity.getStorageKey(), entity.getNotes(), entity.getReferredDoctor(),
                entity.getReferredHospital(), entity.getReferralNotes(), entity.getAiExtractionStatus(), entity.getAiExtractionProvider(),
                entity.getAiExtractionModel(), entity.getAiExtractionConfidence(), entity.getAiExtractionSummary(),
                entity.getAiExtractionStructuredJson(), entity.getAiExtractionReviewNotes(), entity.getAiExtractionReviewedByAppUserId(),
                entity.getAiExtractionReviewedAt(), entity.getOcrStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
