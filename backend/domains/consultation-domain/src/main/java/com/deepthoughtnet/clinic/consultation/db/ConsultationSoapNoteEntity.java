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
        name = "consultation_soap_notes",
        indexes = {
                @Index(name = "ix_consultation_soap_notes_tenant_consultation", columnList = "tenant_id,consultation_id"),
                @Index(name = "ix_consultation_soap_notes_tenant_status", columnList = "tenant_id,consultation_id,status"),
                @Index(name = "ix_consultation_soap_notes_tenant_source", columnList = "tenant_id,consultation_id,source"),
                @Index(name = "ix_consultation_soap_notes_tenant_source_hash", columnList = "tenant_id,consultation_id,source_hash")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_consultation_soap_notes_tenant_consultation_version",
                        columnNames = {"tenant_id", "consultation_id", "version_number"}
                )
        }
)
public class ConsultationSoapNoteEntity {
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
    private ConsultationSoapStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConsultationSoapSource source;

    @Column(columnDefinition = "text")
    private String subjective;

    @Column(columnDefinition = "text")
    private String objective;

    @Column(columnDefinition = "text")
    private String assessment;

    @Column(columnDefinition = "text")
    private String plan;

    @Column(name = "ai_provider", length = 64)
    private String aiProvider;

    @Column(name = "ai_model", length = 128)
    private String aiModel;

    @Column(name = "generated_by_app_user_id")
    private UUID generatedByAppUserId;

    @Column(name = "accepted_by_app_user_id")
    private UUID acceptedByAppUserId;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected ConsultationSoapNoteEntity() {
    }

    public static ConsultationSoapNoteEntity create(
            UUID tenantId,
            UUID consultationId,
            int versionNumber,
            ConsultationSoapStatus status,
            ConsultationSoapSource source,
            String subjective,
            String objective,
            String assessment,
            String plan,
            String aiProvider,
            String aiModel,
            UUID generatedByAppUserId,
            UUID acceptedByAppUserId,
            OffsetDateTime generatedAt,
            OffsetDateTime acceptedAt,
            String sourceHash
    ) {
        ConsultationSoapNoteEntity entity = new ConsultationSoapNoteEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.consultationId = consultationId;
        entity.versionNumber = versionNumber;
        entity.status = status;
        entity.source = source;
        entity.subjective = subjective;
        entity.objective = objective;
        entity.assessment = assessment;
        entity.plan = plan;
        entity.aiProvider = aiProvider;
        entity.aiModel = aiModel;
        entity.generatedByAppUserId = generatedByAppUserId;
        entity.acceptedByAppUserId = acceptedByAppUserId;
        entity.generatedAt = generatedAt;
        entity.acceptedAt = acceptedAt;
        entity.sourceHash = sourceHash;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void supersede(UUID supersededById) {
        this.status = ConsultationSoapStatus.SUPERSEDED;
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

    public ConsultationSoapStatus getStatus() {
        return status;
    }

    public ConsultationSoapSource getSource() {
        return source;
    }

    public String getSubjective() {
        return subjective;
    }

    public String getObjective() {
        return objective;
    }

    public String getAssessment() {
        return assessment;
    }

    public String getPlan() {
        return plan;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public UUID getGeneratedByAppUserId() {
        return generatedByAppUserId;
    }

    public UUID getAcceptedByAppUserId() {
        return acceptedByAppUserId;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public String getSourceHash() {
        return sourceHash;
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

    public enum ConsultationSoapStatus {
        DRAFT,
        ACCEPTED,
        SUPERSEDED
    }

    public enum ConsultationSoapSource {
        MANUAL,
        AI_DRAFT,
        AI_ACCEPTED
    }
}
