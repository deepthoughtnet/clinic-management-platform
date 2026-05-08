package com.deepthoughtnet.clinic.api.clinicaldocument.db;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "patient_clinical_documents",
        indexes = {
                @Index(name = "ix_patient_clinical_documents_tenant_patient", columnList = "tenant_id,patient_id,created_at"),
                @Index(name = "ix_patient_clinical_documents_tenant_type", columnList = "tenant_id,document_type"),
                @Index(name = "ix_patient_clinical_documents_tenant_uploaded_by", columnList = "tenant_id,uploaded_by_app_user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_patient_clinical_documents_storage_key", columnNames = {"tenant_id", "storage_key"})
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

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "uploaded_by_app_user_id", nullable = false)
    private UUID uploadedByAppUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 48)
    private ClinicalDocumentType documentType;

    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 128)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "referred_doctor", length = 255)
    private String referredDoctor;

    @Column(name = "referred_hospital", length = 255)
    private String referredHospital;

    @Column(name = "referral_notes", columnDefinition = "text")
    private String referralNotes;

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

    @Column(name = "ai_extraction_reviewed_by_app_user_id")
    private UUID aiExtractionReviewedByAppUserId;

    @Column(name = "ai_extraction_reviewed_at")
    private OffsetDateTime aiExtractionReviewedAt;

    @Column(name = "ocr_status", length = 32)
    private String ocrStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected ClinicalDocumentEntity() {
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
        OffsetDateTime now = OffsetDateTime.now();
        ClinicalDocumentEntity entity = new ClinicalDocumentEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.consultationId = consultationId;
        entity.appointmentId = appointmentId;
        entity.uploadedByAppUserId = uploadedByAppUserId;
        entity.documentType = documentType;
        entity.originalFilename = originalFilename;
        entity.mediaType = mediaType;
        entity.sizeBytes = sizeBytes;
        entity.checksumSha256 = checksumSha256;
        entity.storageKey = storageKey;
        entity.notes = notes;
        entity.referredDoctor = referredDoctor;
        entity.referredHospital = referredHospital;
        entity.referralNotes = referralNotes;
        entity.aiExtractionStatus = "PENDING";
        entity.aiExtractionProvider = null;
        entity.aiExtractionModel = null;
        entity.aiExtractionConfidence = null;
        entity.aiExtractionSummary = null;
        entity.aiExtractionStructuredJson = null;
        entity.aiExtractionReviewNotes = null;
        entity.aiExtractionReviewedByAppUserId = null;
        entity.aiExtractionReviewedAt = null;
        entity.ocrStatus = "PENDING";
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getConsultationId() { return consultationId; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getUploadedByAppUserId() { return uploadedByAppUserId; }
    public ClinicalDocumentType getDocumentType() { return documentType; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getStorageKey() { return storageKey; }
    public String getNotes() { return notes; }
    public String getReferredDoctor() { return referredDoctor; }
    public String getReferredHospital() { return referredHospital; }
    public String getReferralNotes() { return referralNotes; }
    public String getAiExtractionStatus() { return aiExtractionStatus; }
    public String getAiExtractionProvider() { return aiExtractionProvider; }
    public String getAiExtractionModel() { return aiExtractionModel; }
    public BigDecimal getAiExtractionConfidence() { return aiExtractionConfidence; }
    public String getAiExtractionSummary() { return aiExtractionSummary; }
    public String getAiExtractionStructuredJson() { return aiExtractionStructuredJson; }
    public String getAiExtractionReviewNotes() { return aiExtractionReviewNotes; }
    public UUID getAiExtractionReviewedByAppUserId() { return aiExtractionReviewedByAppUserId; }
    public OffsetDateTime getAiExtractionReviewedAt() { return aiExtractionReviewedAt; }
    public String getOcrStatus() { return ocrStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

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

    public void markAiExtractionReviewed(UUID reviewedByAppUserId, String reviewNotes, String reviewStatus) {
        this.aiExtractionReviewedByAppUserId = reviewedByAppUserId;
        this.aiExtractionReviewedAt = OffsetDateTime.now();
        this.aiExtractionReviewNotes = reviewNotes;
        this.aiExtractionStatus = reviewStatus;
        this.updatedAt = OffsetDateTime.now();
    }
}
