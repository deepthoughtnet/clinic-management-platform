package com.deepthoughtnet.clinic.clinic.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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

    @Column(name = "specializations_json", columnDefinition = "text")
    private String specializationsJson;

    @Column(length = 256)
    private String qualification;

    @Column(name = "registration_number", length = 128)
    private String registrationNumber;

    @Column(name = "consultation_room", length = 128)
    private String consultationRoom;

    @Column(name = "consultation_fee", precision = 12, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "opd_fee", precision = 12, scale = 2)
    private BigDecimal opdFee;

    @Column(name = "follow_up_fee", precision = 12, scale = 2)
    private BigDecimal followUpFee;

    @Column(name = "emergency_fee", precision = 12, scale = 2)
    private BigDecimal emergencyFee;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "age")
    private Integer age;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "public_listing_enabled", nullable = false)
    private boolean publicListingEnabled = false;

    @Column(name = "slug", length = 192)
    private String slug;

    @Column(name = "photo_storage_key", length = 512)
    private String photoStorageKey;

    @Column(name = "photo_content_type", length = 128)
    private String photoContentType;

    @Column(name = "photo_size_bytes")
    private Long photoSizeBytes;

    @Column(name = "photo_original_filename", length = 256)
    private String photoOriginalFilename;

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

    public void update(
            String mobile,
            String specialization,
            List<String> specializations,
            String qualification,
            String registrationNumber,
            String consultationRoom,
            BigDecimal consultationFee,
            BigDecimal opdFee,
            BigDecimal followUpFee,
            BigDecimal emergencyFee,
            Integer yearsOfExperience,
            Integer age,
            Boolean active,
            Boolean publicListingEnabled,
            String slug
    ) {
        this.mobile = mobile;
        this.specialization = specialization;
        this.specializationsJson = serializeSpecializations(specializations, specialization);
        this.qualification = qualification;
        this.registrationNumber = registrationNumber;
        this.consultationRoom = consultationRoom;
        this.consultationFee = consultationFee;
        this.opdFee = opdFee != null ? opdFee : consultationFee;
        this.followUpFee = followUpFee;
        this.emergencyFee = emergencyFee;
        this.yearsOfExperience = yearsOfExperience;
        this.age = age;
        if (active != null) {
            this.active = active;
        }
        if (publicListingEnabled != null) {
            this.publicListingEnabled = publicListingEnabled;
        }
        this.slug = slug;
        this.updatedAt = OffsetDateTime.now();
    }

    public void updatePhoto(String storageKey, String contentType, Long sizeBytes, String originalFilename) {
        this.photoStorageKey = storageKey;
        this.photoContentType = contentType;
        this.photoSizeBytes = sizeBytes;
        this.photoOriginalFilename = originalFilename;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getDoctorUserId() { return doctorUserId; }
    public String getMobile() { return mobile; }
    public String getSpecialization() { return specialization; }
    public String getSpecializationsJson() { return specializationsJson; }
    public String getQualification() { return qualification; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getConsultationRoom() { return consultationRoom; }
    public BigDecimal getConsultationFee() { return consultationFee; }
    public BigDecimal getOpdFee() { return opdFee; }
    public BigDecimal getFollowUpFee() { return followUpFee; }
    public BigDecimal getEmergencyFee() { return emergencyFee; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public Integer getAge() { return age; }
    public boolean isActive() { return active; }
    public boolean isPublicListingEnabled() { return publicListingEnabled; }
    public String getSlug() { return slug; }
    public String getPhotoStorageKey() { return photoStorageKey; }
    public String getPhotoContentType() { return photoContentType; }
    public Long getPhotoSizeBytes() { return photoSizeBytes; }
    public String getPhotoOriginalFilename() { return photoOriginalFilename; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    private String serializeSpecializations(List<String> specializations, String fallback) {
        List<String> values = specializations == null || specializations.isEmpty()
                ? (fallback == null || fallback.isBlank() ? List.of() : List.of(fallback.trim()))
                : specializations.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        if (values.isEmpty()) {
            return null;
        }
        return String.join("|", values);
    }
}
