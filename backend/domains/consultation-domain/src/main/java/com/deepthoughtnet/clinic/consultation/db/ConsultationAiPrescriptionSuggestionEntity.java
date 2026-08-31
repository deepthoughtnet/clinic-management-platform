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
        name = "consultation_ai_prescription_suggestions",
        indexes = {
                @Index(name = "ix_consultation_ai_prescription_suggestions_tenant_consultation", columnList = "tenant_id,consultation_id"),
                @Index(name = "ix_consultation_ai_prescription_suggestions_tenant_status", columnList = "tenant_id,consultation_id,status"),
                @Index(name = "ix_consultation_ai_prescription_suggestions_tenant_context", columnList = "tenant_id,consultation_id,source_hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_consultation_ai_prescription_suggestions_tenant_consultation_version",
                        columnNames = {"tenant_id", "consultation_id", "version_number"}
                )
        }
)
public class ConsultationAiPrescriptionSuggestionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "consultation_id", nullable = false)
    private UUID consultationId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConsultationAiPrescriptionSuggestionStatus status;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "generated_by_app_user_id")
    private UUID generatedByAppUserId;

    @Column(name = "generated_by_display_name", length = 255)
    private String generatedByDisplayName;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    private String contentJson;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected ConsultationAiPrescriptionSuggestionEntity() {
    }

    public static ConsultationAiPrescriptionSuggestionEntity create(
            UUID tenantId,
            UUID consultationId,
            int versionNumber,
            String sourceHash,
            String provider,
            String model,
            UUID generatedByAppUserId,
            String generatedByDisplayName,
            OffsetDateTime generatedAt,
            String contentJson
    ) {
        ConsultationAiPrescriptionSuggestionEntity entity = new ConsultationAiPrescriptionSuggestionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.consultationId = consultationId;
        entity.versionNumber = versionNumber;
        entity.status = ConsultationAiPrescriptionSuggestionStatus.CURRENT;
        entity.sourceHash = sourceHash;
        entity.provider = provider;
        entity.model = model;
        entity.generatedByAppUserId = generatedByAppUserId;
        entity.generatedByDisplayName = generatedByDisplayName;
        entity.generatedAt = generatedAt;
        entity.contentJson = contentJson;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void supersede(UUID supersededById) {
        this.status = ConsultationAiPrescriptionSuggestionStatus.SUPERSEDED;
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

    public int getVersionNumber() {
        return versionNumber;
    }

    public ConsultationAiPrescriptionSuggestionStatus getStatus() {
        return status;
    }

    public String getSourceHash() {
        return sourceHash;
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

    public String getContentJson() {
        return contentJson;
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

    public enum ConsultationAiPrescriptionSuggestionStatus {
        CURRENT,
        SUPERSEDED
    }
}
