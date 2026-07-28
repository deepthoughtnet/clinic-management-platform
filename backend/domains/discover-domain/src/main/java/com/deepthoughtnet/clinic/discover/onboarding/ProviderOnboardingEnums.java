package com.deepthoughtnet.clinic.discover.onboarding;

public final class ProviderOnboardingEnums {
    private ProviderOnboardingEnums() {
    }

    public enum ProviderType {
        INDIVIDUAL_DOCTOR,
        CLINIC,
        HOSPITAL
    }

    public enum ProviderLifecycleStatus {
        DRAFT,
        CONTACT_VERIFIED,
        PROFILE_INCOMPLETE,
        READY_FOR_REVIEW,
        SUBMITTED,
        UNDER_REVIEW,
        CHANGES_REQUESTED,
        APPROVED,
        PUBLISHED,
        SUSPENDED,
        ARCHIVED
    }

    public enum ProviderDocumentType {
        LOGO,
        COVER_IMAGE,
        DOCTOR_PHOTO,
        GALLERY_IMAGE,
        REGISTRATION_CERTIFICATE,
        ACCREDITATION,
        IDENTITY_PROOF,
        OTHER
    }

    public enum ProviderServiceType {
        CONSULTATIONS,
        VACCINATION,
        LAB,
        RADIOLOGY,
        TELECONSULTATION,
        PHARMACY,
        HEALTH_CHECKUPS,
        PROCEDURES
    }

    public enum ProviderOnboardingEventType {
        DRAFT_CREATED,
        DRAFT_SAVED,
        DOCUMENT_UPLOADED,
        SUBMITTED,
        CHANGES_REQUESTED,
        APPROVED,
        PUBLISHED
    }
}
