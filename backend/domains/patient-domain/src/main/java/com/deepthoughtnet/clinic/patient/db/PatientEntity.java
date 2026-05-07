package com.deepthoughtnet.clinic.patient.db;

import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "patients",
        indexes = {
                @Index(name = "ix_patients_tenant_patient_number", columnList = "tenant_id,patient_number"),
                @Index(name = "ix_patients_tenant_mobile", columnList = "tenant_id,mobile"),
                @Index(name = "ix_patients_tenant_name", columnList = "tenant_id,last_name,first_name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_patients_tenant_patient_number", columnNames = {"tenant_id", "patient_number"})
        }
)
public class PatientEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_number", nullable = false, length = 64)
    private String patientNumber;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 128)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PatientGender gender = PatientGender.UNKNOWN;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "age_years")
    private Integer ageYears;

    @Column(nullable = false, length = 64)
    private String mobile;

    @Column(length = 256)
    private String email;

    @Column(name = "address_line1", length = 256)
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

    @Column(name = "emergency_contact_name", length = 128)
    private String emergencyContactName;

    @Column(name = "emergency_contact_mobile", length = 64)
    private String emergencyContactMobile;

    @Column(name = "blood_group", length = 16)
    private String bloodGroup;

    @Column(length = 512)
    private String allergies;

    @Column(name = "existing_conditions", length = 512)
    private String existingConditions;

    @Column(name = "long_term_medications", columnDefinition = "text")
    private String longTermMedications;

    @Column(name = "surgical_history", columnDefinition = "text")
    private String surgicalHistory;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected PatientEntity() {
    }

    public static PatientEntity create(UUID tenantId, String patientNumber) {
        PatientEntity entity = new PatientEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientNumber = patientNumber;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(
            String firstName,
            String lastName,
            PatientGender gender,
            LocalDate dateOfBirth,
            Integer ageYears,
            String mobile,
            String email,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String country,
            String postalCode,
            String emergencyContactName,
            String emergencyContactMobile,
            String bloodGroup,
            String allergies,
            String existingConditions,
            String longTermMedications,
            String surgicalHistory,
            String notes,
            boolean active
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender == null ? PatientGender.UNKNOWN : gender;
        this.dateOfBirth = dateOfBirth;
        this.ageYears = ageYears;
        this.mobile = mobile;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.country = country;
        this.postalCode = postalCode;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactMobile = emergencyContactMobile;
        this.bloodGroup = bloodGroup;
        this.allergies = allergies;
        this.existingConditions = existingConditions;
        this.longTermMedications = longTermMedications;
        this.surgicalHistory = surgicalHistory;
        this.notes = notes;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPatientNumber() {
        return patientNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public PatientGender getGender() {
        return gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Integer getAgeYears() {
        return ageYears;
    }

    public String getMobile() {
        return mobile;
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

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactMobile() {
        return emergencyContactMobile;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getAllergies() {
        return allergies;
    }

    public String getExistingConditions() {
        return existingConditions;
    }

    public String getLongTermMedications() {
        return longTermMedications;
    }

    public String getSurgicalHistory() {
        return surgicalHistory;
    }

    public String getNotes() {
        return notes;
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
