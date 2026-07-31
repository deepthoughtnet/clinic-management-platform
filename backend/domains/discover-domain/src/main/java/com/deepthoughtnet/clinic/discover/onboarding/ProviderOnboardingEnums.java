package com.deepthoughtnet.clinic.discover.onboarding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;

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
        DISCARDED,
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
        CONSULTATION,
        TELECONSULTATION,
        HEALTH_CHECKUPS,
        VACCINATION,
        MINOR_PROCEDURES,
        HOME_VISIT,
        LAB_COLLECTION,
        CHRONIC_DISEASE_MANAGEMENT,
        PREVENTIVE_CARE;

        private static final Map<String, ProviderServiceType> LOOKUP = Map.ofEntries(
                Map.entry("CONSULTATION", CONSULTATION),
                Map.entry("CONSULTATIONS", CONSULTATION),
                Map.entry("TELECONSULTATION", TELECONSULTATION),
                Map.entry("HEALTH_CHECKUPS", HEALTH_CHECKUPS),
                Map.entry("VACCINATION", VACCINATION),
                Map.entry("MINOR_PROCEDURES", MINOR_PROCEDURES),
                Map.entry("PROCEDURES", MINOR_PROCEDURES),
                Map.entry("HOME_VISIT", HOME_VISIT),
                Map.entry("LAB_COLLECTION", LAB_COLLECTION),
                Map.entry("LAB", LAB_COLLECTION),
                Map.entry("CHRONIC_DISEASE_MANAGEMENT", CHRONIC_DISEASE_MANAGEMENT),
                Map.entry("PREVENTIVE_CARE", PREVENTIVE_CARE)
        );

        @JsonCreator
        public static ProviderServiceType fromCode(String code) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("service type is required");
            }
            ProviderServiceType type = LOOKUP.get(code.trim().toUpperCase());
            if (type == null) {
                throw new IllegalArgumentException("Unknown provider service type: " + code);
            }
            return type;
        }

        @JsonValue
        public String toCode() {
            return name();
        }
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
