package com.deepthoughtnet.clinic.appointment.db;

import com.deepthoughtnet.clinic.appointment.service.model.WaitlistStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "appointment_waitlist",
        indexes = {
                @Index(name = "ix_appointment_waitlist_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_appointment_waitlist_tenant_doctor_date", columnList = "tenant_id,doctor_user_id,preferred_date"),
                @Index(name = "ix_appointment_waitlist_tenant_patient", columnList = "tenant_id,patient_id")
        }
)
public class AppointmentWaitlistEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_user_id")
    private UUID doctorUserId;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Column(name = "preferred_start_time")
    private LocalTime preferredStartTime;

    @Column(name = "preferred_end_time")
    private LocalTime preferredEndTime;

    @Column(length = 512)
    private String reason;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(name = "booked_appointment_id")
    private UUID bookedAppointmentId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected AppointmentWaitlistEntity() {}

    public static AppointmentWaitlistEntity create(UUID tenantId, UUID patientId, UUID doctorUserId) {
        AppointmentWaitlistEntity entity = new AppointmentWaitlistEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.doctorUserId = doctorUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(LocalDate preferredDate, LocalTime preferredStartTime, LocalTime preferredEndTime, String reason, String notes, WaitlistStatus status) {
        this.preferredDate = preferredDate;
        this.preferredStartTime = preferredStartTime;
        this.preferredEndTime = preferredEndTime;
        this.reason = reason;
        this.notes = notes;
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markBooked(UUID appointmentId) {
        this.status = WaitlistStatus.BOOKED;
        this.bookedAppointmentId = appointmentId;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorUserId() { return doctorUserId; }
    public LocalDate getPreferredDate() { return preferredDate; }
    public LocalTime getPreferredStartTime() { return preferredStartTime; }
    public LocalTime getPreferredEndTime() { return preferredEndTime; }
    public String getReason() { return reason; }
    public String getNotes() { return notes; }
    public WaitlistStatus getStatus() { return status; }
    public UUID getBookedAppointmentId() { return bookedAppointmentId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
