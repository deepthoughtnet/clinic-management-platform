package com.deepthoughtnet.clinic.carepilot.lead.db;

import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Persistence model for CarePilot leads. */
@Entity
@Table(name = "carepilot_leads", indexes = {
        @Index(name = "ix_cp_leads_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "ix_cp_leads_tenant_source", columnList = "tenant_id,source"),
        @Index(name = "ix_cp_leads_tenant_followup", columnList = "tenant_id,next_follow_up_at"),
        @Index(name = "ix_cp_leads_tenant_assigned", columnList = "tenant_id,assigned_to_app_user_id")
})
public class LeadEntity {
    @Id
    @Column(nullable = false)
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;
    @Column(name = "last_name", length = 128)
    private String lastName;
    @Column(name = "full_name", length = 260)
    private String fullName;
    @Column(nullable = false, length = 64)
    private String phone;
    @Column(length = 256)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PatientGender gender;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeadSource source;
    @Column(name = "source_details", length = 512)
    private String sourceDetails;
    @Column(name = "campaign_id")
    private UUID campaignId;
    @Column(name = "assigned_to_app_user_id")
    private UUID assignedToAppUserId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LeadStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeadPriority priority;
    @Column(columnDefinition = "text")
    private String notes;
    @Column(columnDefinition = "text")
    private String tags;
    @Column(name = "converted_patient_id")
    private UUID convertedPatientId;
    @Column(name = "booked_appointment_id")
    private UUID bookedAppointmentId;
    @Column(name = "last_contacted_at")
    private OffsetDateTime lastContactedAt;
    @Column(name = "next_follow_up_at")
    private OffsetDateTime nextFollowUpAt;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    @Column(nullable = false)
    private int version;

    protected LeadEntity() {}

    public static LeadEntity create(UUID tenantId, UUID actorId) {
        LeadEntity entity = new LeadEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.status = LeadStatus.NEW;
        entity.priority = LeadPriority.MEDIUM;
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

    public void setConverted(UUID patientId, UUID actorId) {
        this.convertedPatientId = patientId;
        this.status = LeadStatus.CONVERTED;
        touch(actorId);
    }

    public void setBookedAppointmentId(UUID bookedAppointmentId, UUID actorId) {
        this.bookedAppointmentId = bookedAppointmentId;
        touch(actorId);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public PatientGender getGender() { return gender; }
    public void setGender(PatientGender gender) { this.gender = gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public LeadSource getSource() { return source; }
    public void setSource(LeadSource source) { this.source = source; }
    public String getSourceDetails() { return sourceDetails; }
    public void setSourceDetails(String sourceDetails) { this.sourceDetails = sourceDetails; }
    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }
    public UUID getAssignedToAppUserId() { return assignedToAppUserId; }
    public void setAssignedToAppUserId(UUID assignedToAppUserId) { this.assignedToAppUserId = assignedToAppUserId; }
    public LeadStatus getStatus() { return status; }
    public void setStatus(LeadStatus status) { this.status = status; }
    public LeadPriority getPriority() { return priority; }
    public void setPriority(LeadPriority priority) { this.priority = priority; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public UUID getConvertedPatientId() { return convertedPatientId; }
    public UUID getBookedAppointmentId() { return bookedAppointmentId; }
    public OffsetDateTime getLastContactedAt() { return lastContactedAt; }
    public void setLastContactedAt(OffsetDateTime lastContactedAt) { this.lastContactedAt = lastContactedAt; }
    public OffsetDateTime getNextFollowUpAt() { return nextFollowUpAt; }
    public void setNextFollowUpAt(OffsetDateTime nextFollowUpAt) { this.nextFollowUpAt = nextFollowUpAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
