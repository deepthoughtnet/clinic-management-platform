package com.deepthoughtnet.clinic.consultation.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "consultation_ai_summaries",
        indexes = {
                @Index(name = "ix_consultation_ai_summaries_tenant_consultation", columnList = "tenant_id,consultation_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_consultation_ai_summaries_tenant_consultation", columnNames = {"tenant_id", "consultation_id"})
        }
)
public class ConsultationAiSummaryEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "consultation_id", nullable = false)
    private UUID consultationId;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected ConsultationAiSummaryEntity() {
    }

    public static ConsultationAiSummaryEntity create(UUID tenantId, UUID consultationId) {
        ConsultationAiSummaryEntity entity = new ConsultationAiSummaryEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.consultationId = consultationId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String summary, String provider, String model, OffsetDateTime generatedAt) {
        this.summary = summary;
        this.provider = provider;
        this.model = model;
        this.generatedAt = generatedAt;
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

    public String getSummary() {
        return summary;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
