package com.deepthoughtnet.clinic.appointment.db;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "appointments",
        indexes = {
                @Index(name = "ix_appointments_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_appointments_tenant_doctor_date", columnList = "tenant_id,doctor_user_id,appointment_date"),
                @Index(name = "ix_appointments_tenant_date_status", columnList = "tenant_id,appointment_date,status")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_appointments_token",
                        columnNames = {"tenant_id", "doctor_user_id", "appointment_date", "token_number"}
                )
        }
)
public class AppointmentEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time")
    private LocalTime appointmentTime;

    @Column(name = "token_number")
    private Integer tokenNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AppointmentPriority priority = AppointmentPriority.NORMAL;

    @Column(length = 512)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AppointmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AppointmentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected AppointmentEntity() {
    }

    public static AppointmentEntity create(UUID tenantId, UUID patientId, UUID doctorUserId) {
        AppointmentEntity entity = new AppointmentEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.doctorUserId = doctorUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            Integer tokenNumber,
            String reason,
            AppointmentType type,
            AppointmentStatus status,
            AppointmentPriority priority
    ) {
        this.appointmentDate = appointmentDate;
        this.appointmentTime = appointmentTime;
        this.tokenNumber = tokenNumber;
        this.reason = reason;
        this.type = type;
        this.status = status;
        this.priority = priority == null ? AppointmentPriority.NORMAL : priority;
        this.updatedAt = OffsetDateTime.now();
    }

    public void reassignDoctor(UUID doctorUserId) {
        this.doctorUserId = doctorUserId;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getDoctorUserId() {
        return doctorUserId;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public Integer getTokenNumber() {
        return tokenNumber;
    }

    public AppointmentPriority getPriority() {
        return priority;
    }

    public String getReason() {
        return reason;
    }

    public AppointmentType getType() {
        return type;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
