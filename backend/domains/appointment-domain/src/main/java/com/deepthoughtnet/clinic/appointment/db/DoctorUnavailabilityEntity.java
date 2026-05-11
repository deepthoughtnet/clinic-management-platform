package com.deepthoughtnet.clinic.appointment.db;

import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "doctor_unavailability",
        indexes = {
                @Index(name = "ix_doctor_unavailability_tenant_doctor", columnList = "tenant_id,doctor_user_id"),
                @Index(name = "ix_doctor_unavailability_tenant_start", columnList = "tenant_id,start_at")
        }
)
public class DoctorUnavailabilityEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 24)
    private DoctorUnavailabilityType blockType;

    @Column(length = 512)
    private String reason;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DoctorUnavailabilityEntity() {
    }

    public static DoctorUnavailabilityEntity create(UUID tenantId, UUID doctorUserId) {
        DoctorUnavailabilityEntity entity = new DoctorUnavailabilityEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.doctorUserId = doctorUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(OffsetDateTime startAt, OffsetDateTime endAt, DoctorUnavailabilityType blockType, String reason, boolean active) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.blockType = blockType;
        this.reason = reason;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDoctorUserId() { return doctorUserId; }
    public OffsetDateTime getStartAt() { return startAt; }
    public OffsetDateTime getEndAt() { return endAt; }
    public DoctorUnavailabilityType getBlockType() { return blockType; }
    public String getReason() { return reason; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
