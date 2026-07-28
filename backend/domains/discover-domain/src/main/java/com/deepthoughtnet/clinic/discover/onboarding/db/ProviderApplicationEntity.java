package com.deepthoughtnet.clinic.discover.onboarding.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_applications")
public class ProviderApplicationEntity {
    @Id
    private UUID id;
    @Column(name = "reference_number", nullable = false, unique = true, length = 32)
    private String referenceNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProviderLifecycleStatus status;
    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;
    @Column(nullable = false, length = 256)
    private String email;
    @Column(nullable = false, length = 32)
    private String phone;
    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;
    @Column(name = "contact_verified", nullable = false)
    private boolean contactVerified;
    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;
    @Column(name = "privacy_accepted", nullable = false)
    private boolean privacyAccepted;
    @Column(name = "display_name", length = 256)
    private String displayName;
    @Column(name = "legal_name", length = 256)
    private String legalName;
    @Column(name = "organisation_type", length = 128)
    private String organisationType;
    @Column(name = "registration_number", length = 128)
    private String registrationNumber;
    @Column(name = "gst_number", length = 64)
    private String gstNumber;
    @Column(length = 256)
    private String website;
    @Column(length = 32)
    private String gender;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(length = 512)
    private String languages;
    @Column(length = 2000)
    private String biography;
    @Column(name = "medical_council", length = 128)
    private String medicalCouncil;
    @Column(length = 256)
    private String qualification;
    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;
    @Column(length = 512)
    private String specialities;
    @Column(name = "sub_specialities", length = 512)
    private String subSpecialities;
    @Column(name = "consultation_fee", precision = 12, scale = 2)
    private BigDecimal consultationFee;
    @Column(name = "online_consultation", nullable = false)
    private boolean onlineConsultation;
    @Column(name = "appointment_duration_minutes")
    private Integer appointmentDurationMinutes;
    @Column(length = 128)
    private String ownership;
    @Column(name = "hospital_type", length = 128)
    private String hospitalType;
    private Integer beds;
    @Column(name = "emergency_available", nullable = false)
    private boolean emergencyAvailable;
    @Column(name = "medical_director", length = 256)
    private String medicalDirector;
    @Column(length = 512)
    private String departments;
    @Column(length = 512)
    private String facilities;
    @Column(length = 512)
    private String accreditations;
    @Column(name = "logo_document_id")
    private UUID logoDocumentId;
    @Column(name = "cover_image_document_id")
    private UUID coverImageDocumentId;
    @Column(name = "doctor_photo_document_id")
    private UUID doctorPhotoDocumentId;
    @Column(name = "primary_color", length = 24)
    private String primaryColor;
    @Column(length = 256)
    private String tagline;
    @Column(name = "completion_percent", nullable = false)
    private int completionPercent;
    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep;
    @jakarta.persistence.Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;
    @Column(name = "last_saved_at", nullable = false)
    private OffsetDateTime lastSavedAt;
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProviderApplicationEntity() {
    }

    public static ProviderApplicationEntity create(UUID id, String referenceNumber, ProviderType providerType, String tokenHash, String email, String phone, String passwordHash, boolean termsAccepted, boolean privacyAccepted) {
        OffsetDateTime now = OffsetDateTime.now();
        ProviderApplicationEntity entity = new ProviderApplicationEntity();
        entity.id = id;
        entity.referenceNumber = referenceNumber;
        entity.providerType = providerType;
        entity.status = ProviderLifecycleStatus.DRAFT;
        entity.tokenHash = tokenHash;
        entity.email = email;
        entity.phone = phone;
        entity.passwordHash = passwordHash;
        entity.termsAccepted = termsAccepted;
        entity.privacyAccepted = privacyAccepted;
        entity.currentStep = "ACCOUNT";
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.lastSavedAt = now;
        return entity;
    }

    public void touch(int completionPercent, String currentStep) {
        this.completionPercent = completionPercent;
        this.currentStep = currentStep;
        this.updatedAt = OffsetDateTime.now();
        this.lastSavedAt = this.updatedAt;
    }

    public void markSubmitted() {
        this.status = ProviderLifecycleStatus.SUBMITTED;
        this.submittedAt = OffsetDateTime.now();
        touch(100, "SUBMIT");
    }

    public UUID getId() { return id; }
    public String getReferenceNumber() { return referenceNumber; }
    public ProviderType getProviderType() { return providerType; }
    public ProviderLifecycleStatus getStatus() { return status; }
    public void setStatus(ProviderLifecycleStatus status) { this.status = status; }
    public String getTokenHash() { return tokenHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isContactVerified() { return contactVerified; }
    public void setContactVerified(boolean contactVerified) { this.contactVerified = contactVerified; }
    public boolean isTermsAccepted() { return termsAccepted; }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }
    public boolean isPrivacyAccepted() { return privacyAccepted; }
    public void setPrivacyAccepted(boolean privacyAccepted) { this.privacyAccepted = privacyAccepted; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getOrganisationType() { return organisationType; }
    public void setOrganisationType(String organisationType) { this.organisationType = organisationType; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getGstNumber() { return gstNumber; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }
    public String getBiography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }
    public String getMedicalCouncil() { return medicalCouncil; }
    public void setMedicalCouncil(String medicalCouncil) { this.medicalCouncil = medicalCouncil; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public Integer getYearsOfExperience() { return yearsOfExperience; }
    public void setYearsOfExperience(Integer yearsOfExperience) { this.yearsOfExperience = yearsOfExperience; }
    public String getSpecialities() { return specialities; }
    public void setSpecialities(String specialities) { this.specialities = specialities; }
    public String getSubSpecialities() { return subSpecialities; }
    public void setSubSpecialities(String subSpecialities) { this.subSpecialities = subSpecialities; }
    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }
    public boolean isOnlineConsultation() { return onlineConsultation; }
    public void setOnlineConsultation(boolean onlineConsultation) { this.onlineConsultation = onlineConsultation; }
    public Integer getAppointmentDurationMinutes() { return appointmentDurationMinutes; }
    public void setAppointmentDurationMinutes(Integer appointmentDurationMinutes) { this.appointmentDurationMinutes = appointmentDurationMinutes; }
    public String getOwnership() { return ownership; }
    public void setOwnership(String ownership) { this.ownership = ownership; }
    public String getHospitalType() { return hospitalType; }
    public void setHospitalType(String hospitalType) { this.hospitalType = hospitalType; }
    public Integer getBeds() { return beds; }
    public void setBeds(Integer beds) { this.beds = beds; }
    public boolean isEmergencyAvailable() { return emergencyAvailable; }
    public void setEmergencyAvailable(boolean emergencyAvailable) { this.emergencyAvailable = emergencyAvailable; }
    public String getMedicalDirector() { return medicalDirector; }
    public void setMedicalDirector(String medicalDirector) { this.medicalDirector = medicalDirector; }
    public String getDepartments() { return departments; }
    public void setDepartments(String departments) { this.departments = departments; }
    public String getFacilities() { return facilities; }
    public void setFacilities(String facilities) { this.facilities = facilities; }
    public String getAccreditations() { return accreditations; }
    public void setAccreditations(String accreditations) { this.accreditations = accreditations; }
    public UUID getLogoDocumentId() { return logoDocumentId; }
    public void setLogoDocumentId(UUID logoDocumentId) { this.logoDocumentId = logoDocumentId; }
    public UUID getCoverImageDocumentId() { return coverImageDocumentId; }
    public void setCoverImageDocumentId(UUID coverImageDocumentId) { this.coverImageDocumentId = coverImageDocumentId; }
    public UUID getDoctorPhotoDocumentId() { return doctorPhotoDocumentId; }
    public void setDoctorPhotoDocumentId(UUID doctorPhotoDocumentId) { this.doctorPhotoDocumentId = doctorPhotoDocumentId; }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public int getCompletionPercent() { return completionPercent; }
    public String getCurrentStep() { return currentStep; }
    public long getRowVersion() { return rowVersion; }
    public OffsetDateTime getLastSavedAt() { return lastSavedAt; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
