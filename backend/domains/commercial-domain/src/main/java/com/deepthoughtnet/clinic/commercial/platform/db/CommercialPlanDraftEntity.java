package com.deepthoughtnet.clinic.commercial.platform.db;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.DraftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_plan_drafts")
public class CommercialPlanDraftEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, unique = true)
    private CommercialPlanTemplateEntity template;

    @Column(nullable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DraftStatus status;

    @Column(name = "draft_notes", length = 1000)
    private String draftNotes;

    @Column(name = "validation_status", nullable = false, length = 32)
    private String validationStatus;

    @Column(name = "publication_ready", nullable = false)
    private boolean publicationReady;

    @Column(name = "config_json", nullable = false, columnDefinition = "text")
    private String configJson;

    @Column(name = "config_hash", nullable = false, length = 128)
    private String configHash;

    @Column(name = "validation_json", nullable = false, columnDefinition = "text")
    private String validationJson;

    @Column(name = "last_validated_at")
    private OffsetDateTime lastValidatedAt;

    @Column(name = "last_validated_by")
    private UUID lastValidatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    public CommercialPlanDraftEntity() {
    }

    public static CommercialPlanDraftEntity create(CommercialPlanTemplateEntity template, int revision, String configJson, String configHash, String validationJson, DraftStatus status, boolean publicationReady, OffsetDateTime now, UUID actor) {
        CommercialPlanDraftEntity entity = new CommercialPlanDraftEntity();
        entity.id = UUID.randomUUID();
        entity.template = template;
        entity.revision = revision;
        entity.configJson = configJson;
        entity.configHash = configHash;
        entity.validationJson = validationJson;
        entity.status = status;
        entity.publicationReady = publicationReady;
        entity.validationStatus = publicationReady ? "READY" : "BLOCKED";
        entity.createdAt = now;
        entity.createdBy = actor;
        entity.updatedAt = now;
        entity.updatedBy = actor;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public CommercialPlanTemplateEntity getTemplate() { return template; }
    public int getRevision() { return revision; }
    public DraftStatus getStatus() { return status; }
    public String getDraftNotes() { return draftNotes; }
    public String getValidationStatus() { return validationStatus; }
    public boolean isPublicationReady() { return publicationReady; }
    public String getConfigJson() { return configJson; }
    public String getConfigHash() { return configHash; }
    public String getValidationJson() { return validationJson; }
    public OffsetDateTime getLastValidatedAt() { return lastValidatedAt; }
    public UUID getLastValidatedBy() { return lastValidatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }

    public void update(int revision, String draftNotes, String configJson, String configHash, String validationJson, DraftStatus status, boolean publicationReady, OffsetDateTime now, UUID actor) {
        this.revision = revision;
        this.draftNotes = draftNotes;
        this.configJson = configJson;
        this.configHash = configHash;
        this.validationJson = validationJson;
        this.status = status;
        this.publicationReady = publicationReady;
        this.validationStatus = publicationReady ? "READY" : "BLOCKED";
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void markValidated(String validationStatus, boolean publicationReady, String validationJson, OffsetDateTime now, UUID actor) {
        this.validationStatus = validationStatus;
        this.publicationReady = publicationReady;
        this.validationJson = validationJson;
        this.lastValidatedAt = now;
        this.lastValidatedBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void markPublished(OffsetDateTime now, UUID actor) {
        this.status = DraftStatus.READY_TO_PUBLISH;
        this.updatedAt = now;
        this.updatedBy = actor;
    }
}
