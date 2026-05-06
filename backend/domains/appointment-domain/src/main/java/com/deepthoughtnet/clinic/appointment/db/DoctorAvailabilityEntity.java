package com.deepthoughtnet.clinic.appointment.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "doctor_availability",
        indexes = {
                @Index(name = "ix_doctor_availability_tenant_doctor", columnList = "tenant_id,doctor_user_id"),
                @Index(name = "ix_doctor_availability_tenant_day", columnList = "tenant_id,day_of_week")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_doctor_availability_slot",
                        columnNames = {"tenant_id", "doctor_user_id", "day_of_week", "start_time", "end_time"}
                )
        }
)
public class DoctorAvailabilityEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 16)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "consultation_duration_minutes", nullable = false)
    private Integer consultationDurationMinutes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DoctorAvailabilityEntity() {
    }

    public static DoctorAvailabilityEntity create(UUID tenantId, UUID doctorUserId) {
        DoctorAvailabilityEntity entity = new DoctorAvailabilityEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.doctorUserId = doctorUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, Integer consultationDurationMinutes, boolean active) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.consultationDurationMinutes = consultationDurationMinutes;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getDoctorUserId() {
        return doctorUserId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getConsultationDurationMinutes() {
        return consultationDurationMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
