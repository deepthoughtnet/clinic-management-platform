package com.deepthoughtnet.clinic.commercial.platform.db;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TargetSegment;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.TemplateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_plan_templates")
public class CommercialPlanTemplateEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_segment", nullable = false, length = 64)
    private TargetSegment targetSegment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TemplateStatus status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "current_draft_revision", nullable = false)
    private int currentDraftRevision;

    @Column(name = "latest_published_version_number")
    private Integer latestPublishedVersionNumber;

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

    public CommercialPlanTemplateEntity() {
    }

    public static CommercialPlanTemplateEntity create(UUID id, String code, String name, String description, TargetSegment targetSegment, TemplateStatus status, int displayOrder, OffsetDateTime now, UUID actor) {
        CommercialPlanTemplateEntity entity = new CommercialPlanTemplateEntity();
        entity.id = id;
        entity.code = code;
        entity.name = name;
        entity.description = description;
        entity.targetSegment = targetSegment;
        entity.status = status;
        entity.displayOrder = displayOrder;
        entity.currentDraftRevision = 1;
        entity.createdAt = now;
        entity.createdBy = actor;
        entity.updatedAt = now;
        entity.updatedBy = actor;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TargetSegment getTargetSegment() { return targetSegment; }
    public TemplateStatus getStatus() { return status; }
    public int getDisplayOrder() { return displayOrder; }
    public int getCurrentDraftRevision() { return currentDraftRevision; }
    public Integer getLatestPublishedVersionNumber() { return latestPublishedVersionNumber; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }

    public void update(String name, String description, TargetSegment targetSegment, TemplateStatus status, int displayOrder, OffsetDateTime now, UUID actor) {
        this.name = name;
        this.description = description;
        this.targetSegment = targetSegment;
        this.status = status;
        this.displayOrder = displayOrder;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void retire(OffsetDateTime now, UUID actor) {
        this.status = TemplateStatus.RETIRED;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void markPublished(Integer versionNumber, OffsetDateTime now, UUID actor) {
        this.latestPublishedVersionNumber = versionNumber;
        this.status = TemplateStatus.ACTIVE;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void incrementDraftRevision(OffsetDateTime now, UUID actor) {
        this.currentDraftRevision = this.currentDraftRevision + 1;
        this.updatedAt = now;
        this.updatedBy = actor;
    }
}
