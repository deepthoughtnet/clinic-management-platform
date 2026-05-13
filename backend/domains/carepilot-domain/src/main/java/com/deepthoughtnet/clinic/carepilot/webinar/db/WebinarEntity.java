package com.deepthoughtnet.clinic.carepilot.webinar.db;

import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Persistence model for webinar/event definitions. */
@Entity
@Table(name = "carepilot_webinars", indexes = {
        @Index(name = "ix_cp_webinars_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "ix_cp_webinars_tenant_type", columnList = "tenant_id,webinar_type"),
        @Index(name = "ix_cp_webinars_tenant_start", columnList = "tenant_id,scheduled_start_at")
})
public class WebinarEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "webinar_type", nullable = false, length = 40)
    private WebinarType webinarType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WebinarStatus status;

    @Column(name = "webinar_url", length = 1024)
    private String webinarUrl;

    @Column(name = "organizer_name", length = 128)
    private String organizerName;

    @Column(name = "organizer_email", length = 256)
    private String organizerEmail;

    @Column(name = "scheduled_start_at", nullable = false)
    private OffsetDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private OffsetDateTime scheduledEndAt;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column
    private Integer capacity;

    @Column(name = "registration_enabled", nullable = false)
    private boolean registrationEnabled;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled;

    @Column(name = "followup_enabled", nullable = false)
    private boolean followupEnabled;

    @Column(columnDefinition = "text")
    private String tags;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private int version;

    protected WebinarEntity() {}

    public static WebinarEntity create(UUID tenantId, UUID actorId) {
        WebinarEntity entity = new WebinarEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.status = WebinarStatus.DRAFT;
        entity.webinarType = WebinarType.OTHER;
        entity.registrationEnabled = true;
        entity.reminderEnabled = true;
        entity.followupEnabled = true;
        entity.timezone = "UTC";
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void touch(UUID actorId) {
        this.updatedBy = actorId;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public WebinarType getWebinarType() { return webinarType; }
    public void setWebinarType(WebinarType webinarType) { this.webinarType = webinarType; }
    public WebinarStatus getStatus() { return status; }
    public void setStatus(WebinarStatus status) { this.status = status; }
    public String getWebinarUrl() { return webinarUrl; }
    public void setWebinarUrl(String webinarUrl) { this.webinarUrl = webinarUrl; }
    public String getOrganizerName() { return organizerName; }
    public void setOrganizerName(String organizerName) { this.organizerName = organizerName; }
    public String getOrganizerEmail() { return organizerEmail; }
    public void setOrganizerEmail(String organizerEmail) { this.organizerEmail = organizerEmail; }
    public OffsetDateTime getScheduledStartAt() { return scheduledStartAt; }
    public void setScheduledStartAt(OffsetDateTime scheduledStartAt) { this.scheduledStartAt = scheduledStartAt; }
    public OffsetDateTime getScheduledEndAt() { return scheduledEndAt; }
    public void setScheduledEndAt(OffsetDateTime scheduledEndAt) { this.scheduledEndAt = scheduledEndAt; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean registrationEnabled) { this.registrationEnabled = registrationEnabled; }
    public boolean isReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }
    public boolean isFollowupEnabled() { return followupEnabled; }
    public void setFollowupEnabled(boolean followupEnabled) { this.followupEnabled = followupEnabled; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
}
