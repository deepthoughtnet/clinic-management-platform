package com.deepthoughtnet.clinic.consultation.db;

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
        name = "consultation_clinical_reasoning_results",
        indexes = {
                @Index(name = "ix_consultation_clinical_reasoning_tenant_consultation", columnList = "tenant_id,consultation_id"),
                @Index(name = "ix_consultation_clinical_reasoning_tenant_status", columnList = "tenant_id,consultation_id,status"),
                @Index(name = "ix_consultation_clinical_reasoning_tenant_context", columnList = "tenant_id,consultation_id,context_hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_consultation_clinical_reasoning_tenant_consultation_version",
                        columnNames = {"tenant_id", "consultation_id", "version_number"}
                )
        }
)
public class ClinicalReasoningResultEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "consultation_id", nullable = false)
    private UUID consultationId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ClinicalReasoningLifecycleStatus status;

    @Column(name = "context_hash", nullable = false, length = 64)
    private String contextHash;

    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;

    @Column(name = "reasoning_engine_version", nullable = false, length = 64)
    private String reasoningEngineVersion;

    @Column(length = 64)
    private String provider;

    @Column(length = 128)
    private String model;

    @Column(name = "generated_by_app_user_id")
    private UUID generatedByAppUserId;

    @Column(name = "generated_by_display_name", length = 255)
    private String generatedByDisplayName;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "result_json", columnDefinition = "text", nullable = false)
    private String resultJson;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected ClinicalReasoningResultEntity() {
    }

    public static ClinicalReasoningResultEntity create(UUID tenantId,
                                                       UUID consultationId,
                                                       UUID patientId,
                                                       int versionNumber,
                                                       String contextHash,
                                                       String promptVersion,
                                                       String reasoningEngineVersion,
                                                       String provider,
                                                       String model,
                                                       UUID generatedByAppUserId,
                                                       String generatedByDisplayName,
                                                       OffsetDateTime generatedAt,
                                                       String resultJson) {
        ClinicalReasoningResultEntity entity = new ClinicalReasoningResultEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.consultationId = consultationId;
        entity.patientId = patientId;
        entity.versionNumber = versionNumber;
        entity.status = ClinicalReasoningLifecycleStatus.CURRENT;
        entity.contextHash = contextHash;
        entity.promptVersion = promptVersion;
        entity.reasoningEngineVersion = reasoningEngineVersion;
        entity.provider = provider;
        entity.model = model;
        entity.generatedByAppUserId = generatedByAppUserId;
        entity.generatedByDisplayName = generatedByDisplayName;
        entity.generatedAt = generatedAt;
        entity.resultJson = resultJson;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void supersede(UUID supersededById) {
        this.status = ClinicalReasoningLifecycleStatus.SUPERSEDED;
        this.supersededById = supersededById;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getConsultationId() {
        return consultationId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public ClinicalReasoningLifecycleStatus getStatus() {
        return status;
    }

    public String getContextHash() {
        return contextHash;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getReasoningEngineVersion() {
        return reasoningEngineVersion;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public UUID getGeneratedByAppUserId() {
        return generatedByAppUserId;
    }

    public String getGeneratedByDisplayName() {
        return generatedByDisplayName;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public String getResultJson() {
        return resultJson;
    }

    public UUID getSupersededById() {
        return supersededById;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum ClinicalReasoningLifecycleStatus {
        CURRENT,
        SUPERSEDED
    }
}
