package com.deepthoughtnet.clinic.api.medicationsafety.db;

import com.deepthoughtnet.clinic.api.medicationsafety.MedicationSafetyReviewDecisionStatus;
import com.deepthoughtnet.clinic.api.medicationsafety.MedicationSafetySeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "prescription_safety_reviews",
        indexes = {
                @Index(name = "ix_prescription_safety_reviews_tenant_prescription", columnList = "tenant_id,prescription_id,updated_at"),
                @Index(name = "ix_prescription_safety_reviews_tenant_consultation", columnList = "tenant_id,consultation_id,updated_at"),
                @Index(name = "ix_prescription_safety_reviews_tenant_patient", columnList = "tenant_id,patient_id,updated_at"),
                @Index(name = "ix_prescription_safety_reviews_tenant_prescription_generation", columnList = "tenant_id,prescription_id,snapshot_generation")
        }
)
public class PrescriptionSafetyReviewEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "consultation_id", nullable = false)
    private UUID consultationId;

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "prescription_version", nullable = false)
    private Integer prescriptionVersion;

    @Column(name = "snapshot_generation", nullable = false)
    private Integer snapshotGeneration;

    @Column(name = "prescription_hash", nullable = false, length = 128)
    private String prescriptionHash;

    @Column(name = "patient_context_hash", nullable = false, length = 128)
    private String patientContextHash;

    @Column(name = "rules_version", nullable = false, length = 64)
    private String rulesVersion;

    @Column(name = "evaluation_id", nullable = false, length = 128)
    private String evaluationId;

    @Column(name = "evaluation_overall_severity", nullable = false, length = 24)
    private String evaluationOverallSeverity;

    @Column(name = "evaluation_snapshot_json", nullable = false, columnDefinition = "text")
    private String evaluationSnapshotJson;

    @Column(name = "decision_status", nullable = false, length = 48)
    private String decisionStatus;

    @Column(name = "reviewed_by_app_user_id")
    private UUID reviewedByAppUserId;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "acknowledgement_json", columnDefinition = "text")
    private String acknowledgementJson;

    @Column(name = "override_reason_code", length = 64)
    private String overrideReasonCode;

    @Column(name = "override_reason_text", columnDefinition = "text")
    private String overrideReasonText;

    @Column(name = "override_category", length = 64)
    private String overrideCategory;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected PrescriptionSafetyReviewEntity() {
    }

    public static PrescriptionSafetyReviewEntity create(UUID tenantId,
                                                         UUID patientId,
                                                         UUID consultationId,
                                                         UUID prescriptionId,
                                                         Integer prescriptionVersion,
                                                         String prescriptionHash,
                                                         String patientContextHash,
                                                         String rulesVersion,
                                                         String evaluationId,
                                                         MedicationSafetySeverity overallSeverity,
                                                         String evaluationSnapshotJson,
                                                         String decisionStatus,
                                                         UUID reviewedByAppUserId,
                                                         String acknowledgementJson,
                                                         String overrideReasonCode,
                                                         String overrideReasonText,
                                                         String overrideCategory) {
        return create(tenantId, patientId, consultationId, prescriptionId, prescriptionVersion, prescriptionHash, patientContextHash,
                rulesVersion, evaluationId, overallSeverity, evaluationSnapshotJson, decisionStatus, reviewedByAppUserId,
                acknowledgementJson, overrideReasonCode, overrideReasonText, overrideCategory, 1);
    }

    public static PrescriptionSafetyReviewEntity create(UUID tenantId,
                                                         UUID patientId,
                                                         UUID consultationId,
                                                         UUID prescriptionId,
                                                         Integer prescriptionVersion,
                                                         String prescriptionHash,
                                                         String patientContextHash,
                                                         String rulesVersion,
                                                         String evaluationId,
                                                         MedicationSafetySeverity overallSeverity,
                                                         String evaluationSnapshotJson,
                                                         String decisionStatus,
                                                         UUID reviewedByAppUserId,
                                                         String acknowledgementJson,
                                                         String overrideReasonCode,
                                                         String overrideReasonText,
                                                         String overrideCategory,
                                                         Integer snapshotGeneration) {
        PrescriptionSafetyReviewEntity entity = new PrescriptionSafetyReviewEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.consultationId = consultationId;
        entity.prescriptionId = prescriptionId;
        entity.prescriptionVersion = prescriptionVersion;
        entity.snapshotGeneration = snapshotGeneration == null ? 1 : snapshotGeneration;
        entity.prescriptionHash = prescriptionHash;
        entity.patientContextHash = patientContextHash;
        entity.rulesVersion = rulesVersion;
        entity.evaluationId = evaluationId;
        entity.evaluationOverallSeverity = overallSeverity == null ? null : overallSeverity.name();
        entity.evaluationSnapshotJson = evaluationSnapshotJson;
        entity.decisionStatus = decisionStatus;
        entity.reviewedByAppUserId = reviewedByAppUserId;
        entity.reviewedAt = OffsetDateTime.now();
        entity.acknowledgementJson = acknowledgementJson;
        entity.overrideReasonCode = overrideReasonCode;
        entity.overrideReasonText = overrideReasonText;
        entity.overrideCategory = overrideCategory;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String evaluationSnapshotJson,
                       String decisionStatus,
                       UUID reviewedByAppUserId,
                       String acknowledgementJson,
                       String overrideReasonCode,
                       String overrideReasonText,
                       String overrideCategory) {
        this.evaluationSnapshotJson = evaluationSnapshotJson;
        this.decisionStatus = decisionStatus;
        this.reviewedByAppUserId = reviewedByAppUserId;
        this.reviewedAt = OffsetDateTime.now();
        this.acknowledgementJson = acknowledgementJson;
        this.overrideReasonCode = overrideReasonCode;
        this.overrideReasonText = overrideReasonText;
        this.overrideCategory = overrideCategory;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markFinalized() {
        this.decisionStatus = MedicationSafetyReviewDecisionStatus.FINALIZED.name();
        this.finalizedAt = OffsetDateTime.now();
        this.updatedAt = this.finalizedAt;
    }

    public void markStale() {
        this.decisionStatus = MedicationSafetyReviewDecisionStatus.STALE.name();
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getConsultationId() { return consultationId; }
    public UUID getPrescriptionId() { return prescriptionId; }
    public Integer getPrescriptionVersion() { return prescriptionVersion; }
    public Integer getSnapshotGeneration() { return snapshotGeneration; }
    public String getPrescriptionHash() { return prescriptionHash; }
    public String getPatientContextHash() { return patientContextHash; }
    public String getRulesVersion() { return rulesVersion; }
    public String getEvaluationId() { return evaluationId; }
    public String getEvaluationOverallSeverity() { return evaluationOverallSeverity; }
    public String getEvaluationSnapshotJson() { return evaluationSnapshotJson; }
    public String getDecisionStatus() { return decisionStatus; }
    public UUID getReviewedByAppUserId() { return reviewedByAppUserId; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public String getAcknowledgementJson() { return acknowledgementJson; }
    public String getOverrideReasonCode() { return overrideReasonCode; }
    public String getOverrideReasonText() { return overrideReasonText; }
    public String getOverrideCategory() { return overrideCategory; }
    public OffsetDateTime getFinalizedAt() { return finalizedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }
}
