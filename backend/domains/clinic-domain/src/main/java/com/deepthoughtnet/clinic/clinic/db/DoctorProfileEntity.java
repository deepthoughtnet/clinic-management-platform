package com.deepthoughtnet.clinic.clinic.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "doctor_profiles",
        indexes = {
                @Index(name = "ix_doctor_profiles_tenant_doctor", columnList = "tenant_id,doctor_user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_doctor_profiles_tenant_doctor", columnNames = {"tenant_id", "doctor_user_id"})
        }
)
public class DoctorProfileEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Column(length = 32)
    private String mobile;

    @Column(length = 128)
    private String specialization;

    @Column(length = 256)
    private String qualification;

    @Column(name = "registration_number", length = 128)
    private String registrationNumber;

    @Column(name = "consultation_room", length = 128)
    private String consultationRoom;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(nullable = false)
    private int version;

    protected DoctorProfileEntity() {
    }

    public static DoctorProfileEntity create(UUID tenantId, UUID doctorUserId) {
        DoctorProfileEntity entity = new DoctorProfileEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.doctorUserId = doctorUserId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String mobile, String specialization, String qualification, String registrationNumber, String consultationRoom, Boolean active) {
        this.mobile = mobile;
        this.specialization = specialization;
        this.qualification = qualification;
        this.registrationNumber = registrationNumber;
        this.consultationRoom = consultationRoom;
        if (active != null) {
            this.active = active;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDoctorUserId() { return doctorUserId; }
    public String getMobile() { return mobile; }
    public String getSpecialization() { return specialization; }
    public String getQualification() { return qualification; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getConsultationRoom() { return consultationRoom; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
