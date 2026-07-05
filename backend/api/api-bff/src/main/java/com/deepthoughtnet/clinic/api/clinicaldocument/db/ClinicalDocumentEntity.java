package com.deepthoughtnet.clinic.api.clinicaldocument.db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "patient_documents",
        indexes = {
                @Index(name = "ix_patient_documents_tenant_patient", columnList = "tenant_id,patient_id,created_at"),
                @Index(name = "ix_patient_documents_tenant_type", columnList = "tenant_id,document_type"),
                @Index(name = "ix_patient_documents_tenant_consultation", columnList = "tenant_id,consultation_id"),
                @Index(name = "ix_patient_documents_tenant_source", columnList = "tenant_id,source_module,source_entity_id"),
                @Index(name = "ix_patient_documents_tenant_uploaded_by", columnList = "tenant_id,uploaded_by_user_id"),
                @Index(name = "ix_patient_documents_tenant_active", columnList = "tenant_id,active"),
                @Index(name = "ix_patient_documents_tenant_report_date", columnList = "tenant_id,report_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_patient_documents_storage_object_key", columnNames = {"tenant_id", "storage_object_key"})
        }
)
public class ClinicalDocumentEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "source_module", length = 64)
    private String sourceModule;

    @Column(name = "source_entity_id", length = 128)
    private String sourceEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 48)
    private ClinicalDocumentType documentType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Column(name = "uploaded_by_name", nullable = false, length = 255)
    private String uploadedByName;

    @Column(name = "upload_source", nullable = false, length = 32)
    private String uploadSource;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_bucket", nullable = false, length = 255)
    private String storageBucket;

    @Column(name = "storage_object_key", nullable = false, length = 1024)
    private String storageObjectKey;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility;

    @Column(name = "verification_status", nullable = false, length = 32)
    private String verificationStatus;

    @Column(name = "ocr_status", nullable = false, length = 32)
    private String ocrStatus;

    @Column(name = "ai_index_status", nullable = false, length = 32)
    private String aiIndexStatus;

    @Column(name = "ai_extraction_status", length = 32)
    private String aiExtractionStatus;

    @Column(name = "ai_extraction_provider", length = 64)
    private String aiExtractionProvider;

    @Column(name = "ai_extraction_model", length = 128)
    private String aiExtractionModel;

    @Column(name = "ai_extraction_confidence", precision = 5, scale = 4)
    private BigDecimal aiExtractionConfidence;

    @Column(name = "ai_extraction_summary", columnDefinition = "text")
    private String aiExtractionSummary;

    @Column(name = "ai_extraction_structured_json", columnDefinition = "text")
    private String aiExtractionStructuredJson;

    @Column(name = "ai_extraction_review_notes", columnDefinition = "text")
    private String aiExtractionReviewNotes;

    @Column(name = "ai_extraction_accepted_json", columnDefinition = "text")
    private String aiExtractionAcceptedJson;

    @Column(name = "ai_extraction_override_reason", columnDefinition = "text")
    private String aiExtractionOverrideReason;

    @Column(name = "ai_extraction_reviewed_by_user_id")
    private UUID aiExtractionReviewedByUserId;

    @Column(name = "ai_extraction_reviewed_at")
    private OffsetDateTime aiExtractionReviewedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private int version;

    protected ClinicalDocumentEntity() {
    }

    public static ClinicalDocumentEntity create(
            UUID id,
            UUID tenantId,
            UUID patientId,
            UUID consultationId,
            UUID sourceEntityIdIgnored,
            UUID uploadedByUserId,
            ClinicalDocumentType documentType,
            String title,
            String description,
            LocalDate reportDate,
            String uploadedByName,
            String uploadSource,
            String fileName,
            String contentType,
            long fileSize,
            String storageBucket,
            String storageObjectKey,
            String checksum,
            String visibility,
            String verificationStatus,
            String ocrStatus,
            String aiIndexStatus,
            String sourceModule,
            String sourceEntityId,
            UUID createdBy,
            UUID updatedBy
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        ClinicalDocumentEntity entity = new ClinicalDocumentEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.consultationId = consultationId;
        entity.sourceModule = sourceModule;
        entity.sourceEntityId = sourceEntityId;
        entity.documentType = documentType;
        entity.title = title;
        entity.description = description;
        entity.reportDate = reportDate;
        entity.uploadedByUserId = uploadedByUserId;
        entity.uploadedByName = uploadedByName;
        entity.uploadSource = uploadSource;
        entity.fileName = fileName;
        entity.contentType = contentType;
        entity.fileSize = fileSize;
        entity.storageBucket = storageBucket;
        entity.storageObjectKey = storageObjectKey;
        entity.checksum = checksum;
        entity.visibility = visibility;
        entity.verificationStatus = verificationStatus;
        entity.ocrStatus = ocrStatus;
        entity.aiIndexStatus = aiIndexStatus;
        entity.aiExtractionStatus = null;
        entity.aiExtractionProvider = null;
        entity.aiExtractionModel = null;
        entity.aiExtractionConfidence = null;
        entity.aiExtractionSummary = null;
        entity.aiExtractionStructuredJson = null;
        entity.aiExtractionReviewNotes = null;
        entity.aiExtractionAcceptedJson = null;
        entity.aiExtractionOverrideReason = null;
        entity.aiExtractionReviewedByUserId = null;
        entity.aiExtractionReviewedAt = null;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.createdBy = createdBy;
        entity.updatedBy = updatedBy;
        return entity;
    }

    public static ClinicalDocumentEntity create(
            UUID tenantId,
            UUID patientId,
            UUID consultationId,
            UUID appointmentId,
            UUID uploadedByAppUserId,
            ClinicalDocumentType documentType,
            String originalFilename,
            String mediaType,
            long sizeBytes,
            String checksumSha256,
            String storageKey,
            String notes,
            String referredDoctor,
            String referredHospital,
            String referralNotes
    ) {
        return create(
                UUID.randomUUID(),
                tenantId,
                patientId,
                consultationId,
                null,
                uploadedByAppUserId,
                documentType,
                originalFilename,
                notes,
                null,
                "UNKNOWN",
                "OTHER",
                originalFilename,
                mediaType,
                sizeBytes,
                "legacy",
                storageKey,
                checksumSha256,
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "NOT_STARTED",
                "NOT_STARTED",
                null,
                null,
                uploadedByAppUserId,
                uploadedByAppUserId
        );
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getConsultationId() { return consultationId; }
    public UUID getAppointmentId() { return null; }
    public String getSourceModule() { return sourceModule; }
    public String getSourceEntityId() { return sourceEntityId; }
    public ClinicalDocumentType getDocumentType() { return documentType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getNotes() { return description; }
    public LocalDate getReportDate() { return reportDate; }
    public UUID getUploadedByUserId() { return uploadedByUserId; }
    public UUID getUploadedByAppUserId() { return uploadedByUserId; }
    public String getUploadedByName() { return uploadedByName; }
    public String getUploadSource() { return uploadSource; }
    public String getFileName() { return fileName; }
    public String getOriginalFilename() { return fileName; }
    public String getContentType() { return contentType; }
    public String getMediaType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public long getSizeBytes() { return fileSize; }
    public String getStorageBucket() { return storageBucket; }
    public String getStorageObjectKey() { return storageObjectKey; }
    public String getStorageKey() { return storageObjectKey; }
    public String getChecksum() { return checksum; }
    public String getChecksumSha256() { return checksum; }
    public String getVisibility() { return visibility; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getOcrStatus() { return ocrStatus; }
    public String getAiIndexStatus() { return aiIndexStatus; }
    public String getReferredDoctor() { return null; }
    public String getReferredHospital() { return null; }
    public String getReferralNotes() { return null; }
    public String getAiExtractionStatus() { return aiExtractionStatus; }
    public String getAiExtractionProvider() { return aiExtractionProvider; }
    public String getAiExtractionModel() { return aiExtractionModel; }
    public BigDecimal getAiExtractionConfidence() { return aiExtractionConfidence; }
    public String getAiExtractionSummary() { return aiExtractionSummary; }
    public String getAiExtractionStructuredJson() { return aiExtractionStructuredJson; }
    public String getAiExtractionReviewNotes() { return aiExtractionReviewNotes; }
    public String getAiExtractionAcceptedJson() { return aiExtractionAcceptedJson; }
    public String getAiExtractionOverrideReason() { return aiExtractionOverrideReason; }
    public UUID getAiExtractionReviewedByUserId() { return aiExtractionReviewedByUserId; }
    public UUID getAiExtractionReviewedByAppUserId() { return aiExtractionReviewedByUserId; }
    public OffsetDateTime getAiExtractionReviewedAt() { return aiExtractionReviewedAt; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public int getVersion() { return version; }

    public void updateMetadata(
            ClinicalDocumentType documentType,
            String title,
            String description,
            LocalDate reportDate,
            String visibility,
            String verificationStatus,
            String uploadedByName,
            String uploadSource,
            UUID updatedBy
    ) {
        this.documentType = documentType;
        this.title = title;
        this.description = description;
        this.reportDate = reportDate;
        this.visibility = visibility;
        this.verificationStatus = verificationStatus;
        this.uploadedByName = uploadedByName;
        this.uploadSource = uploadSource;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public void updatePublishedMetadata(
            ClinicalDocumentType documentType,
            String title,
            String description,
            LocalDate reportDate,
            String uploadedByName,
            String uploadSource,
            String fileName,
            String contentType,
            long fileSize,
            String storageBucket,
            String storageObjectKey,
            String checksum,
            String visibility,
            String verificationStatus,
            String ocrStatus,
            String aiIndexStatus,
            UUID updatedBy
    ) {
        this.documentType = documentType;
        this.title = title;
        this.description = description;
        this.reportDate = reportDate;
        this.uploadedByName = uploadedByName;
        this.uploadSource = uploadSource;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storageBucket = storageBucket;
        this.storageObjectKey = storageObjectKey;
        this.checksum = checksum;
        this.visibility = visibility;
        this.verificationStatus = verificationStatus;
        this.ocrStatus = ocrStatus;
        this.aiIndexStatus = aiIndexStatus;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public void softDelete(UUID updatedBy) {
        this.active = false;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markAiExtractionQueued() {
        this.aiExtractionStatus = "QUEUED";
        this.updatedAt = OffsetDateTime.now();
    }

    public void markAiExtractionProcessing(String ocrStatus) {
        this.aiExtractionStatus = "PROCESSING";
        this.ocrStatus = ocrStatus;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markAiExtractionSucceeded(String provider,
                                          String model,
                                          BigDecimal confidence,
                                          String summary,
                                          String structuredJson,
                                          String reviewStatus,
                                          String ocrStatus) {
        this.aiExtractionProvider = provider;
        this.aiExtractionModel = model;
        this.aiExtractionConfidence = confidence;
        this.aiExtractionSummary = summary;
        this.aiExtractionStructuredJson = structuredJson;
        this.aiExtractionStatus = reviewStatus;
        this.ocrStatus = ocrStatus;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markAiExtractionFailed(String provider, String model, String errorMessage) {
        this.aiExtractionProvider = provider;
        this.aiExtractionModel = model;
        this.aiExtractionStatus = "FAILED";
        this.aiExtractionSummary = errorMessage;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markAiExtractionReviewed(UUID reviewedByAppUserId, String reviewNotes, String reviewStatus, String acceptedJson, String overrideReason) {
        this.aiExtractionReviewedByUserId = reviewedByAppUserId;
        this.aiExtractionReviewedAt = OffsetDateTime.now();
        this.aiExtractionReviewNotes = reviewNotes;
        this.aiExtractionAcceptedJson = acceptedJson;
        this.aiExtractionOverrideReason = overrideReason;
        this.aiExtractionStatus = reviewStatus;
        this.updatedAt = OffsetDateTime.now();
    }
}
