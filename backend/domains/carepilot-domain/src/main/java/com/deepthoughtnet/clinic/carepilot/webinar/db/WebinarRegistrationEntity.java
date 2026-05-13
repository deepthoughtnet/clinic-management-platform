package com.deepthoughtnet.clinic.carepilot.webinar.db;

import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationSource;
import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
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

/** Persistence model for webinar registrations. */
@Entity
@Table(name = "carepilot_webinar_registrations", indexes = {
        @Index(name = "ix_cp_webreg_tenant_webinar", columnList = "tenant_id,webinar_id"),
        @Index(name = "ix_cp_webreg_tenant_status", columnList = "tenant_id,registration_status"),
        @Index(name = "ix_cp_webreg_tenant_created", columnList = "tenant_id,created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_cp_webreg_tenant_webinar_email", columnNames = {"tenant_id", "webinar_id", "attendee_email"})
})
public class WebinarRegistrationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "webinar_id", nullable = false)
    private UUID webinarId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "attendee_name", nullable = false, length = 180)
    private String attendeeName;

    @Column(name = "attendee_email", length = 256)
    private String attendeeEmail;

    @Column(name = "attendee_phone", length = 64)
    private String attendeePhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 24)
    private WebinarRegistrationStatus registrationStatus;

    @Column(nullable = false)
    private boolean attended;

    @Column(name = "attended_at")
    private OffsetDateTime attendedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WebinarRegistrationSource source;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected WebinarRegistrationEntity() {}

    public static WebinarRegistrationEntity create(UUID tenantId, UUID webinarId) {
        WebinarRegistrationEntity entity = new WebinarRegistrationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.webinarId = webinarId;
        entity.registrationStatus = WebinarRegistrationStatus.REGISTERED;
        entity.attended = false;
        entity.source = WebinarRegistrationSource.MANUAL;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getWebinarId() { return webinarId; }
    public UUID getPatientId() { return patientId; }
    public void setPatientId(UUID patientId) { this.patientId = patientId; }
    public UUID getLeadId() { return leadId; }
    public void setLeadId(UUID leadId) { this.leadId = leadId; }
    public String getAttendeeName() { return attendeeName; }
    public void setAttendeeName(String attendeeName) { this.attendeeName = attendeeName; }
    public String getAttendeeEmail() { return attendeeEmail; }
    public void setAttendeeEmail(String attendeeEmail) { this.attendeeEmail = attendeeEmail; }
    public String getAttendeePhone() { return attendeePhone; }
    public void setAttendeePhone(String attendeePhone) { this.attendeePhone = attendeePhone; }
    public WebinarRegistrationStatus getRegistrationStatus() { return registrationStatus; }
    public void setRegistrationStatus(WebinarRegistrationStatus registrationStatus) { this.registrationStatus = registrationStatus; }
    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }
    public OffsetDateTime getAttendedAt() { return attendedAt; }
    public void setAttendedAt(OffsetDateTime attendedAt) { this.attendedAt = attendedAt; }
    public WebinarRegistrationSource getSource() { return source; }
    public void setSource(WebinarRegistrationSource source) { this.source = source; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
