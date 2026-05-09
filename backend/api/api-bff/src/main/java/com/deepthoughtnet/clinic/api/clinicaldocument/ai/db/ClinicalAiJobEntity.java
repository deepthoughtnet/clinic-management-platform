package com.deepthoughtnet.clinic.api.clinicaldocument.ai.db;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "clinical_ai_jobs",
        indexes = {
                @Index(name = "ix_clinical_ai_jobs_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_clinical_ai_jobs_tenant_document", columnList = "tenant_id,document_id"),
                @Index(name = "ix_clinical_ai_jobs_next_attempt", columnList = "next_attempt_at")
        }
)
public class ClinicalAiJobEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 32)
    private ClinicalAiJobType jobType;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "consultation_id")
    private UUID consultationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClinicalAiJobStatus status;

    @Column(length = 64)
    private String provider;

    @Column(length = 128)
    private String model;

    @Column(length = 64)
    private String ocrProvider;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "request_json", columnDefinition = "text")
    private String requestJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(name = "summary_text", columnDefinition = "text")
    private String summaryText;

    @Column(name = "review_status", length = 32)
    private String reviewStatus;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @Column(name = "reviewed_by_app_user_id")
    private UUID reviewedByAppUserId;

    @Column(name = "approved_by_app_user_id")
    private UUID approvedByAppUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "requested_by_app_user_id")
    private UUID requestedByAppUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected ClinicalAiJobEntity() {
    }

    public static ClinicalAiJobEntity queued(UUID tenantId,
                                             ClinicalAiJobType jobType,
                                             String sourceType,
                                             UUID sourceId,
                                             UUID documentId,
                                             UUID patientId,
                                             UUID consultationId,
                                             UUID requestedByAppUserId,
                                             String requestJson) {
        OffsetDateTime now = OffsetDateTime.now();
        ClinicalAiJobEntity entity = new ClinicalAiJobEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.jobType = jobType;
        entity.sourceType = sourceType;
        entity.sourceId = sourceId;
        entity.documentId = documentId;
        entity.patientId = patientId;
        entity.consultationId = consultationId;
        entity.status = ClinicalAiJobStatus.QUEUED;
        entity.attemptCount = 0;
        entity.requestedByAppUserId = requestedByAppUserId;
        entity.requestJson = requestJson;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public ClinicalAiJobType getJobType() {
        return jobType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getConsultationId() {
        return consultationId;
    }

    public ClinicalAiJobStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getOcrProvider() {
        return ocrProvider;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public UUID getReviewedByAppUserId() {
        return reviewedByAppUserId;
    }

    public UUID getApprovedByAppUserId() {
        return approvedByAppUserId;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public UUID getRequestedByAppUserId() {
        return requestedByAppUserId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void markProcessing() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = ClinicalAiJobStatus.PROCESSING;
        this.startedAt = now;
        this.completedAt = null;
        this.errorMessage = null;
        this.nextAttemptAt = null;
        this.attemptCount = this.attemptCount + 1;
        this.updatedAt = now;
    }

    public void markReviewRequired(String provider, String model, String ocrProvider, BigDecimal confidence, String summaryText, String resultJson) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = ClinicalAiJobStatus.REVIEW_REQUIRED;
        this.provider = provider;
        this.model = model;
        this.ocrProvider = ocrProvider;
        this.confidence = confidence;
        this.summaryText = summaryText;
        this.resultJson = resultJson;
        this.completedAt = now;
        this.errorMessage = null;
        this.nextAttemptAt = null;
        this.reviewStatus = "REVIEW_REQUIRED";
        this.reviewNotes = null;
        this.reviewedByAppUserId = null;
        this.approvedByAppUserId = null;
        this.reviewedAt = null;
        this.approvedAt = null;
        this.updatedAt = now;
    }

    public void markSucceeded(String provider,
                              String model,
                              String ocrProvider,
                              BigDecimal confidence,
                              String summaryText,
                              String resultJson,
                              String reviewStatus,
                              UUID reviewedByAppUserId,
                              UUID approvedByAppUserId,
                              String reviewNotes) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = ClinicalAiJobStatus.SUCCEEDED;
        this.provider = provider;
        this.model = model;
        this.ocrProvider = ocrProvider;
        this.confidence = confidence;
        this.summaryText = summaryText;
        this.resultJson = resultJson;
        this.completedAt = now;
        this.errorMessage = null;
        this.nextAttemptAt = null;
        this.reviewStatus = reviewStatus;
        this.reviewedByAppUserId = reviewedByAppUserId;
        this.approvedByAppUserId = approvedByAppUserId;
        this.reviewNotes = reviewNotes;
        this.reviewedAt = reviewedByAppUserId == null ? null : now;
        this.approvedAt = approvedByAppUserId == null ? null : now;
        this.updatedAt = now;
    }

    public void markFailed(String errorMessage, boolean retryable, long retryDelayMillis) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = retryable ? ClinicalAiJobStatus.RETRY_SCHEDULED : ClinicalAiJobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = now;
        this.nextAttemptAt = retryable ? now.plusNanos(retryDelayMillis * 1_000_000L) : null;
        this.updatedAt = now;
    }

    public void markReviewed(UUID reviewerAppUserId, boolean approved, String reviewNotes, String reviewStatus, UUID approvedByAppUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        this.reviewedByAppUserId = reviewerAppUserId;
        this.reviewedAt = now;
        this.reviewNotes = reviewNotes;
        this.reviewStatus = reviewStatus;
        this.approvedByAppUserId = approvedByAppUserId;
        this.approvedAt = approvedByAppUserId == null ? null : now;
        this.status = approved ? ClinicalAiJobStatus.SUCCEEDED : ClinicalAiJobStatus.FAILED;
        this.updatedAt = now;
    }
}
