package com.deepthoughtnet.clinic.clinic.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "clinic_profiles",
        indexes = {
                @Index(name = "ix_clinic_profiles_tenant", columnList = "tenant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_clinic_profiles_tenant", columnNames = {"tenant_id"})
        }
)
public class ClinicProfileEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "clinic_name", nullable = false, length = 256)
    private String clinicName;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(length = 64)
    private String phone;

    @Column(length = 256)
    private String email;

    @Column(name = "address_line1", nullable = false, length = 256)
    private String addressLine1;

    @Column(name = "address_line2", length = 256)
    private String addressLine2;

    @Column(length = 128)
    private String city;

    @Column(length = 128)
    private String state;

    @Column(length = 128)
    private String country;

    @Column(name = "postal_code", length = 32)
    private String postalCode;

    @Column(name = "registration_number", length = 128)
    private String registrationNumber;

    @Column(name = "gst_number", length = 128)
    private String gstNumber;

    @Column(name = "logo_document_id")
    private UUID logoDocumentId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected ClinicProfileEntity() {
    }

    public static ClinicProfileEntity create(UUID tenantId) {
        ClinicProfileEntity entity = new ClinicProfileEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(
            String clinicName,
            String displayName,
            String phone,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String country,
            String postalCode,
            String registrationNumber,
            String gstNumber,
            UUID logoDocumentId,
            boolean active
    ) {
        this.clinicName = clinicName;
        this.displayName = displayName;
        this.phone = phone;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.country = country;
        this.postalCode = postalCode;
        this.registrationNumber = registrationNumber;
        this.gstNumber = gstNumber;
        this.logoDocumentId = logoDocumentId;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getClinicName() {
        return clinicName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getGstNumber() {
        return gstNumber;
    }

    public UUID getLogoDocumentId() {
        return logoDocumentId;
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
