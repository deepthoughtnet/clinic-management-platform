package com.deepthoughtnet.clinic.api.clinicalmemory.db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "patient_longitudinal_concepts",
        indexes = {
                @Index(name = "ix_patient_longitudinal_concepts_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_patient_longitudinal_concepts_tenant_patient_status", columnList = "tenant_id,patient_id,verification_status"),
                @Index(name = "ix_patient_longitudinal_concepts_tenant_patient_key", columnList = "tenant_id,patient_id,concept_key"),
                @Index(name = "ix_patient_longitudinal_concepts_source_document", columnList = "tenant_id,source_document_id")
        }
)
public class PatientLongitudinalConceptEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "source_document_type", length = 64)
    private String sourceDocumentType;

    @Column(name = "source_document_title", columnDefinition = "text")
    private String sourceDocumentTitle;

    @Column(name = "source_document_date")
    private LocalDate sourceDocumentDate;

    @Column(name = "concept_family", length = 64, nullable = false)
    private String conceptFamily;

    @Column(name = "concept_key", length = 128, nullable = false)
    private String conceptKey;

    @Column(name = "concept_label", columnDefinition = "text", nullable = false)
    private String conceptLabel;

    @Column(name = "value_text", columnDefinition = "text")
    private String valueText;

    @Column(name = "value_unit", length = 64)
    private String valueUnit;

    @Column(name = "evidence_text", columnDefinition = "text")
    private String evidenceText;

    @Column(name = "source_summary", columnDefinition = "text")
    private String sourceSummary;

    @Column(name = "verification_status", length = 32, nullable = false)
    private String verificationStatus;

    @Column(precision = 6, scale = 4)
    private BigDecimal confidence;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @Column(name = "override_reason", columnDefinition = "text")
    private String overrideReason;

    @Column(name = "reviewed_by_app_user_id")
    private UUID reviewedByAppUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "observed_at")
    private OffsetDateTime observedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PatientLongitudinalConceptEntity() {
    }

    public static PatientLongitudinalConceptEntity create(UUID tenantId,
                                                          UUID patientId,
                                                          UUID sourceDocumentId,
                                                          String sourceDocumentType,
                                                          String sourceDocumentTitle,
                                                          LocalDate sourceDocumentDate,
                                                          String conceptFamily,
                                                          String conceptKey,
                                                          String conceptLabel,
                                                          String valueText,
                                                          String valueUnit,
                                                          String evidenceText,
                                                          String sourceSummary,
                                                          String verificationStatus,
                                                          BigDecimal confidence,
                                                          OffsetDateTime observedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        PatientLongitudinalConceptEntity entity = new PatientLongitudinalConceptEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.sourceDocumentId = sourceDocumentId;
        entity.sourceDocumentType = sourceDocumentType;
        entity.sourceDocumentTitle = sourceDocumentTitle;
        entity.sourceDocumentDate = sourceDocumentDate;
        entity.conceptFamily = conceptFamily;
        entity.conceptKey = conceptKey;
        entity.conceptLabel = conceptLabel;
        entity.valueText = valueText;
        entity.valueUnit = valueUnit;
        entity.evidenceText = evidenceText;
        entity.sourceSummary = sourceSummary;
        entity.verificationStatus = verificationStatus;
        entity.confidence = confidence;
        entity.observedAt = observedAt;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public String getSourceDocumentType() { return sourceDocumentType; }
    public String getSourceDocumentTitle() { return sourceDocumentTitle; }
    public LocalDate getSourceDocumentDate() { return sourceDocumentDate; }
    public String getConceptFamily() { return conceptFamily; }
    public String getConceptKey() { return conceptKey; }
    public String getConceptLabel() { return conceptLabel; }
    public String getValueText() { return valueText; }
    public String getValueUnit() { return valueUnit; }
    public String getEvidenceText() { return evidenceText; }
    public String getSourceSummary() { return sourceSummary; }
    public String getVerificationStatus() { return verificationStatus; }
    public BigDecimal getConfidence() { return confidence; }
    public String getReviewNotes() { return reviewNotes; }
    public String getOverrideReason() { return overrideReason; }
    public UUID getReviewedByAppUserId() { return reviewedByAppUserId; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getObservedAt() { return observedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void markVerified(String verificationStatus, UUID reviewedByAppUserId, String reviewNotes, String overrideReason) {
        this.verificationStatus = verificationStatus;
        this.reviewedByAppUserId = reviewedByAppUserId;
        this.reviewNotes = reviewNotes;
        this.overrideReason = overrideReason;
        this.reviewedAt = OffsetDateTime.now();
        this.updatedAt = this.reviewedAt;
    }
}
