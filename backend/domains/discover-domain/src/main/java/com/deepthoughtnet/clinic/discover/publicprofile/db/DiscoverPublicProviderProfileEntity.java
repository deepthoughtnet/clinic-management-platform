package com.deepthoughtnet.clinic.discover.publicprofile.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_public_provider_profiles")
public class DiscoverPublicProviderProfileEntity {
    @Id
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "source_entity_reference", nullable = false, length = 160)
    private String sourceEntityReference;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(name = "canonical_slug", nullable = false, unique = true, length = 256)
    private String canonicalSlug;

    @Column(name = "latest_published_version_number", nullable = false)
    private int latestPublishedVersionNumber;

    @Column(name = "latest_published_version_id", nullable = false)
    private UUID latestPublishedVersionId;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(name = "legal_name", length = 256)
    private String legalName;

    @Column(length = 2000)
    private String summary;

    @Column(name = "primary_speciality", length = 256)
    private String primarySpeciality;

    @Column(length = 1000)
    private String specialities;

    @Column(name = "sub_specialities", length = 1000)
    private String subSpecialities;

    @Column(length = 1000)
    private String services;

    @Column(length = 1000)
    private String departments;

    @Column(length = 1000)
    private String facilities;

    @Column(length = 1000)
    private String languages;

    @Column(name = "consultation_modes", length = 512)
    private String consultationModes;

    @Column(name = "logo_document_id")
    private UUID logoDocumentId;

    @Column(name = "cover_image_document_id")
    private UUID coverImageDocumentId;

    @Column(name = "doctor_photo_document_id")
    private UUID doctorPhotoDocumentId;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    @Column(name = "contact_email", length = 256)
    private String contactEmail;

    @Column(length = 256)
    private String website;

    @Column(length = 128)
    private String city;

    @Column(length = 128)
    private String area;

    @Column(length = 128)
    private String state;

    @Column(length = 128)
    private String country;

    @Column(length = 256)
    private String tagline;

    @Column(length = 128)
    private String ownership;

    @Column(name = "hospital_type", length = 128)
    private String hospitalType;

    @Column(name = "medical_director", length = 256)
    private String medicalDirector;

    private Integer beds;

    @Column(name = "emergency_available", nullable = false)
    private boolean emergencyAvailable;

    @Column(name = "doctor_count", nullable = false)
    private int doctorCount;

    @Column(name = "service_count", nullable = false)
    private int serviceCount;

    @Column(name = "department_count", nullable = false)
    private int departmentCount;

    @Column(name = "gallery_count", nullable = false)
    private int galleryCount;

    @Column(name = "booking_mode", nullable = false, length = 32)
    private String bookingMode;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "projected_at", nullable = false)
    private OffsetDateTime projectedAt;

    @Column(name = "publication_status", nullable = false, length = 32)
    private String publicationStatus;

    @Column(name = "connection_revision", nullable = false)
    private long connectionRevision;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DiscoverPublicProviderProfileEntity() {
    }

    public static DiscoverPublicProviderProfileEntity create(
            UUID providerId,
            ProviderType providerType,
            String sourceSystem,
            String sourceEntityReference,
            long sourceRevision,
            OffsetDateTime sourceUpdatedAt,
            String canonicalSlug,
            UUID latestPublishedVersionId,
            int latestPublishedVersionNumber,
            String displayName,
            String legalName,
            String summary,
            String primarySpeciality,
            String specialities,
            String subSpecialities,
            String services,
            String departments,
            String facilities,
            String languages,
            String consultationModes,
            UUID logoDocumentId,
            UUID coverImageDocumentId,
            UUID doctorPhotoDocumentId,
            String contactPhone,
            String contactEmail,
            String website,
            String city,
            String area,
            String state,
            String country,
            String tagline,
            String ownership,
            String hospitalType,
            String medicalDirector,
            Integer beds,
            boolean emergencyAvailable,
            int doctorCount,
            int serviceCount,
            int departmentCount,
            int galleryCount,
            String bookingMode,
            OffsetDateTime publishedAt
    ) {
        DiscoverPublicProviderProfileEntity entity = new DiscoverPublicProviderProfileEntity();
        entity.providerId = providerId;
        entity.providerType = providerType;
        entity.sourceSystem = sourceSystem;
        entity.sourceEntityReference = sourceEntityReference;
        entity.sourceRevision = sourceRevision;
        entity.sourceUpdatedAt = sourceUpdatedAt;
        entity.canonicalSlug = canonicalSlug;
        entity.latestPublishedVersionId = latestPublishedVersionId;
        entity.latestPublishedVersionNumber = latestPublishedVersionNumber;
        entity.displayName = displayName;
        entity.legalName = legalName;
        entity.summary = summary;
        entity.primarySpeciality = primarySpeciality;
        entity.specialities = specialities;
        entity.subSpecialities = subSpecialities;
        entity.services = services;
        entity.departments = departments;
        entity.facilities = facilities;
        entity.languages = languages;
        entity.consultationModes = consultationModes;
        entity.logoDocumentId = logoDocumentId;
        entity.coverImageDocumentId = coverImageDocumentId;
        entity.doctorPhotoDocumentId = doctorPhotoDocumentId;
        entity.contactPhone = contactPhone;
        entity.contactEmail = contactEmail;
        entity.website = website;
        entity.city = city;
        entity.area = area;
        entity.state = state;
        entity.country = country;
        entity.tagline = tagline;
        entity.ownership = ownership;
        entity.hospitalType = hospitalType;
        entity.medicalDirector = medicalDirector;
        entity.beds = beds;
        entity.emergencyAvailable = emergencyAvailable;
        entity.doctorCount = doctorCount;
        entity.serviceCount = serviceCount;
        entity.departmentCount = departmentCount;
        entity.galleryCount = galleryCount;
        entity.bookingMode = bookingMode;
        entity.publishedAt = publishedAt;
        entity.projectedAt = publishedAt;
        entity.publicationStatus = "PUBLISHED";
        entity.connectionRevision = 0L;
        entity.createdAt = publishedAt;
        entity.updatedAt = publishedAt;
        entity.rowVersion = 0L;
        return entity;
    }

    public UUID getProviderId() { return providerId; }
    public ProviderType getProviderType() { return providerType; }
    public String getSourceSystem() { return sourceSystem; }
    public String getSourceEntityReference() { return sourceEntityReference; }
    public long getSourceRevision() { return sourceRevision; }
    public OffsetDateTime getSourceUpdatedAt() { return sourceUpdatedAt; }
    public String getCanonicalSlug() { return canonicalSlug; }
    public int getLatestPublishedVersionNumber() { return latestPublishedVersionNumber; }
    public UUID getLatestPublishedVersionId() { return latestPublishedVersionId; }
    public String getDisplayName() { return displayName; }
    public String getLegalName() { return legalName; }
    public String getSummary() { return summary; }
    public String getPrimarySpeciality() { return primarySpeciality; }
    public String getSpecialities() { return specialities; }
    public String getSubSpecialities() { return subSpecialities; }
    public String getServices() { return services; }
    public String getDepartments() { return departments; }
    public String getFacilities() { return facilities; }
    public String getLanguages() { return languages; }
    public String getConsultationModes() { return consultationModes; }
    public UUID getLogoDocumentId() { return logoDocumentId; }
    public UUID getCoverImageDocumentId() { return coverImageDocumentId; }
    public UUID getDoctorPhotoDocumentId() { return doctorPhotoDocumentId; }
    public String getContactPhone() { return contactPhone; }
    public String getContactEmail() { return contactEmail; }
    public String getWebsite() { return website; }
    public String getCity() { return city; }
    public String getArea() { return area; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public String getTagline() { return tagline; }
    public String getOwnership() { return ownership; }
    public String getHospitalType() { return hospitalType; }
    public String getMedicalDirector() { return medicalDirector; }
    public Integer getBeds() { return beds; }
    public boolean isEmergencyAvailable() { return emergencyAvailable; }
    public int getDoctorCount() { return doctorCount; }
    public int getServiceCount() { return serviceCount; }
    public int getDepartmentCount() { return departmentCount; }
    public int getGalleryCount() { return galleryCount; }
    public String getBookingMode() { return bookingMode; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getProjectedAt() { return projectedAt; }
    public String getPublicationStatus() { return publicationStatus; }
    public long getConnectionRevision() { return connectionRevision; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }

    public void update(
            String canonicalSlug,
            UUID latestPublishedVersionId,
            int latestPublishedVersionNumber,
            String displayName,
            String legalName,
            String summary,
            String primarySpeciality,
            String specialities,
            String subSpecialities,
            String services,
            String departments,
            String facilities,
            String languages,
            String consultationModes,
            UUID logoDocumentId,
            UUID coverImageDocumentId,
            UUID doctorPhotoDocumentId,
            String contactPhone,
            String contactEmail,
            String website,
            String city,
            String area,
            String state,
            String country,
            String tagline,
            String ownership,
            String hospitalType,
            String medicalDirector,
            Integer beds,
            boolean emergencyAvailable,
            int doctorCount,
            int serviceCount,
            int departmentCount,
            int galleryCount,
            String bookingMode,
            OffsetDateTime publishedAt
    ) {
        this.canonicalSlug = canonicalSlug;
        this.latestPublishedVersionId = latestPublishedVersionId;
        this.latestPublishedVersionNumber = latestPublishedVersionNumber;
        this.displayName = displayName;
        this.legalName = legalName;
        this.summary = summary;
        this.primarySpeciality = primarySpeciality;
        this.specialities = specialities;
        this.subSpecialities = subSpecialities;
        this.services = services;
        this.departments = departments;
        this.facilities = facilities;
        this.languages = languages;
        this.consultationModes = consultationModes;
        this.logoDocumentId = logoDocumentId;
        this.coverImageDocumentId = coverImageDocumentId;
        this.doctorPhotoDocumentId = doctorPhotoDocumentId;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.website = website;
        this.city = city;
        this.area = area;
        this.state = state;
        this.country = country;
        this.tagline = tagline;
        this.ownership = ownership;
        this.hospitalType = hospitalType;
        this.medicalDirector = medicalDirector;
        this.beds = beds;
        this.emergencyAvailable = emergencyAvailable;
        this.doctorCount = doctorCount;
        this.serviceCount = serviceCount;
        this.departmentCount = departmentCount;
        this.galleryCount = galleryCount;
        this.bookingMode = bookingMode;
        this.publishedAt = publishedAt;
        this.projectedAt = publishedAt;
        this.updatedAt = publishedAt;
        this.publicationStatus = "PUBLISHED";
        this.connectionRevision = 0L;
    }

    public void updateDoctorCount(int doctorCount, OffsetDateTime observedAt) {
        this.doctorCount = doctorCount;
        if (observedAt != null) {
            this.updatedAt = observedAt;
        }
    }

    public void markUnpublished(OffsetDateTime unpublishedAt) {
        this.publicationStatus = "UNPUBLISHED";
        this.projectedAt = unpublishedAt;
        this.updatedAt = unpublishedAt;
    }

    public void applyLifecycleMetadata(
            String sourceSystem,
            String sourceEntityReference,
            long sourceRevision,
            OffsetDateTime sourceUpdatedAt,
            OffsetDateTime projectedAt,
            long connectionRevision,
            String publicationStatus
    ) {
        if (sourceSystem != null && !sourceSystem.isBlank()) {
            this.sourceSystem = sourceSystem;
        }
        if (sourceEntityReference != null && !sourceEntityReference.isBlank()) {
            this.sourceEntityReference = sourceEntityReference;
        }
        this.sourceRevision = sourceRevision;
        this.sourceUpdatedAt = sourceUpdatedAt;
        if (projectedAt != null) {
            this.projectedAt = projectedAt;
        }
        this.connectionRevision = connectionRevision;
        if (publicationStatus != null && !publicationStatus.isBlank()) {
            this.publicationStatus = publicationStatus;
        }
    }
}
