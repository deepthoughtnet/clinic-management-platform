package com.deepthoughtnet.clinic.discover.onboarding;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderCompletionRecord;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderLocationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderServiceEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class ProviderCompletionProjectionSupport {
    private ProviderCompletionProjectionSupport() {
    }

    public static ProviderCompletionRecord calculateCompletion(
            ProviderApplicationEntity entity,
            List<ProviderLocationEntity> locationRecords,
            List<ProviderServiceEntity> serviceRecords,
            List<ProviderDocumentEntity> documentRecords
    ) {
        List<String> missingFields = missingFieldCodes(entity, locationRecords, serviceRecords);
        List<String> missingDocuments = missingDocumentCodes(entity, documentRecords);
        boolean accountComplete = StringUtils.hasText(entity.getEmail())
                && StringUtils.hasText(entity.getPhone())
                && entity.isContactVerified()
                && entity.isTermsAccepted()
                && entity.isPrivacyAccepted();
        boolean profileComplete = StringUtils.hasText(firstText(entity.getDisplayName(), entity.getLegalName()))
                && (entity.getProviderType() != ProviderType.INDIVIDUAL_DOCTOR || StringUtils.hasText(entity.getBiography()));
        boolean detailsComplete = switch (entity.getProviderType()) {
            case INDIVIDUAL_DOCTOR -> StringUtils.hasText(entity.getQualification())
                    && StringUtils.hasText(entity.getMedicalCouncil())
                    && StringUtils.hasText(entity.getSpecialities())
                    && entity.getYearsOfExperience() != null;
            case CLINIC -> StringUtils.hasText(entity.getOrganisationType())
                    && StringUtils.hasText(entity.getRegistrationNumber())
                    && StringUtils.hasText(entity.getFacilities());
            case HOSPITAL -> StringUtils.hasText(entity.getOwnership())
                    && StringUtils.hasText(entity.getHospitalType())
                    && entity.getBeds() != null && entity.getBeds() > 0
                    && StringUtils.hasText(entity.getDepartments())
                    && StringUtils.hasText(entity.getMedicalDirector())
                    && entity.isEmergencyAvailable();
        };
        boolean servicesComplete = serviceRecords.stream().anyMatch(ProviderServiceEntity::isEnabled);
        boolean locationsComplete = !locationRecords.isEmpty();
        boolean brandingComplete = switch (entity.getProviderType()) {
            case INDIVIDUAL_DOCTOR -> entity.getDoctorPhotoDocumentId() != null;
            case CLINIC, HOSPITAL -> entity.getLogoDocumentId() != null;
        };
        boolean documentsComplete = missingDocuments.isEmpty();
        boolean previewReady = accountComplete && profileComplete && detailsComplete && servicesComplete && locationsComplete && brandingComplete;
        boolean referenceDataAvailable = !missingFields.contains("REFERENCE_DATA_UNAVAILABLE");
        boolean canSubmit = previewReady && documentsComplete && missingFields.isEmpty() && referenceDataAvailable && isSubmitEligible(entity.getStatus());

        List<StepState> steps = List.of(
                new StepState("ACCOUNT", "Account and contact", accountComplete, 10),
                new StepState("PROFILE", "Provider profile", profileComplete, 15),
                new StepState(entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR ? "PROFESSIONAL_DETAILS" : "ORGANISATION_DETAILS", entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR ? "Professional details" : "Organisation details", detailsComplete, 20),
                new StepState("SERVICES", "Services and specialities", servicesComplete, 15),
                new StepState("LOCATIONS", "Locations and working hours", locationsComplete, 15),
                new StepState("BRANDING", "Branding and media", brandingComplete, 10),
                new StepState("DOCUMENTS", "Verification documents", documentsComplete, 10),
                new StepState("PREVIEW", "Profile preview", previewReady, 5),
                new StepState("REVIEW", "Review and submit", canSubmit, 0)
        );

        List<String> completedSteps = steps.stream().filter(StepState::complete).map(StepState::label).toList();
        List<String> incompleteSteps = steps.stream().filter(step -> !step.complete()).map(StepState::label).toList();
        String recommendedNextStep = incompleteSteps.isEmpty() ? "Review and submit" : incompleteSteps.get(0);
        String currentStep = steps.stream()
                .filter(step -> !step.complete())
                .findFirst()
                .map(StepState::code)
                .orElse("REVIEW");
        int completionPercent = steps.stream()
                .filter(StepState::complete)
                .mapToInt(StepState::weight)
                .sum();
        List<String> blockingErrors = new ArrayList<>(missingFields);
        blockingErrors.addAll(missingDocuments);
        if (!referenceDataAvailable) {
            blockingErrors.add("REFERENCE_DATA_UNAVAILABLE");
        }
        List<String> warnings = new ArrayList<>();
        if (!entity.isContactVerified()) {
            warnings.add("CONTACT_VERIFICATION_PENDING");
        }
        if (!StringUtils.hasText(entity.getWebsite())) {
            warnings.add("WEBSITE_OPTIONAL");
        }
        return new ProviderCompletionRecord(
                completionPercent,
                completedSteps,
                incompleteSteps,
                missingFields,
                missingDocuments,
                warnings,
                blockingErrors,
                canSubmit,
                previewReady,
                recommendedNextStep,
                currentStep,
                isReadOnly(entity.getStatus())
        );
    }

    public static boolean requiresAttention(ProviderApplicationEntity entity, ProviderCompletionRecord completion) {
        return completion.completionPercentage() < 100
                || !completion.blockingErrors().isEmpty()
                || entity.getStatus() == ProviderLifecycleStatus.DRAFT
                || entity.getStatus() == ProviderLifecycleStatus.CONTACT_VERIFIED
                || entity.getStatus() == ProviderLifecycleStatus.PROFILE_INCOMPLETE
                || entity.getStatus() == ProviderLifecycleStatus.READY_FOR_REVIEW
                || entity.getStatus() == ProviderLifecycleStatus.CHANGES_REQUESTED;
    }

    private static boolean isSubmitEligible(ProviderLifecycleStatus status) {
        return status == ProviderLifecycleStatus.DRAFT
                || status == ProviderLifecycleStatus.CONTACT_VERIFIED
                || status == ProviderLifecycleStatus.PROFILE_INCOMPLETE
                || status == ProviderLifecycleStatus.READY_FOR_REVIEW
                || status == ProviderLifecycleStatus.CHANGES_REQUESTED
                || status == ProviderLifecycleStatus.SUBMITTED;
    }

    private static boolean isReadOnly(ProviderLifecycleStatus status) {
        return status == ProviderLifecycleStatus.SUBMITTED
                || status == ProviderLifecycleStatus.UNDER_REVIEW
                || status == ProviderLifecycleStatus.APPROVED
                || status == ProviderLifecycleStatus.PUBLISHED
                || status == ProviderLifecycleStatus.DISCARDED
                || status == ProviderLifecycleStatus.SUSPENDED
                || status == ProviderLifecycleStatus.ARCHIVED;
    }

    private static List<String> missingFieldCodes(ProviderApplicationEntity entity, List<ProviderLocationEntity> locationRecords, List<ProviderServiceEntity> serviceRecords) {
        Set<String> missing = new LinkedHashSet<>();
        if (!StringUtils.hasText(entity.getEmail())) missing.add("EMAIL_REQUIRED");
        if (!StringUtils.hasText(entity.getPhone())) missing.add("PHONE_REQUIRED");
        if (!entity.isContactVerified()) missing.add("CONTACT_VERIFICATION_REQUIRED");
        if (!entity.isTermsAccepted()) missing.add("TERMS_ACCEPTANCE_REQUIRED");
        if (!entity.isPrivacyAccepted()) missing.add("PRIVACY_ACCEPTANCE_REQUIRED");
        if (!StringUtils.hasText(firstText(entity.getDisplayName(), entity.getLegalName()))) missing.add(providerNameRequiredCode(entity.getProviderType()));
        if (!StringUtils.hasText(entity.getRegistrationNumber())) missing.add(registrationRequiredCode(entity.getProviderType()));
        if (locationRecords.isEmpty()) missing.add("PRIMARY_LOCATION_REQUIRED");
        if (!serviceRecords.stream().anyMatch(ProviderServiceEntity::isEnabled)) missing.add("SERVICES_REQUIRED");
        if (entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR) {
            if (!StringUtils.hasText(entity.getQualification())) missing.add("DOCTOR_QUALIFICATION_REQUIRED");
            if (!StringUtils.hasText(entity.getMedicalCouncil())) missing.add("DOCTOR_REGISTRATION_COUNCIL_REQUIRED");
            if (!StringUtils.hasText(entity.getSpecialities())) missing.add("PRIMARY_SPECIALITY_REQUIRED");
            if (entity.getYearsOfExperience() == null) missing.add("PRACTISING_SINCE_REQUIRED");
        } else if (entity.getProviderType() == ProviderType.CLINIC) {
            if (!StringUtils.hasText(entity.getOrganisationType())) missing.add("CLINIC_ORGANISATION_TYPE_REQUIRED");
            if (!StringUtils.hasText(entity.getFacilities())) missing.add("CLINIC_FACILITIES_REQUIRED");
        } else {
            if (!StringUtils.hasText(entity.getOwnership())) missing.add("HOSPITAL_OWNERSHIP_REQUIRED");
            if (!StringUtils.hasText(entity.getHospitalType())) missing.add("HOSPITAL_TYPE_REQUIRED");
            if (entity.getBeds() == null || entity.getBeds() <= 0) missing.add("HOSPITAL_BEDS_REQUIRED");
            if (!StringUtils.hasText(entity.getDepartments())) missing.add("HOSPITAL_DEPARTMENTS_REQUIRED");
            if (!StringUtils.hasText(entity.getMedicalDirector())) missing.add("HOSPITAL_MEDICAL_DIRECTOR_REQUIRED");
            if (!entity.isEmergencyAvailable()) missing.add("HOSPITAL_EMERGENCY_STATUS_REQUIRED");
        }
        return new ArrayList<>(missing);
    }

    private static List<String> missingDocumentCodes(ProviderApplicationEntity entity, List<ProviderDocumentEntity> documentRecords) {
        Set<ProviderDocumentType> available = new LinkedHashSet<>();
        for (ProviderDocumentEntity record : documentRecords) {
            available.add(record.getDocumentType());
        }
        List<String> missing = new ArrayList<>();
        if (entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR) {
            if (!available.contains(ProviderDocumentType.DOCTOR_PHOTO)) missing.add("DOCTOR_PHOTO_REQUIRED");
            if (!available.contains(ProviderDocumentType.REGISTRATION_CERTIFICATE)) missing.add("DOCTOR_REGISTRATION_CERTIFICATE_REQUIRED");
        } else if (entity.getProviderType() == ProviderType.CLINIC) {
            if (!available.contains(ProviderDocumentType.LOGO)) missing.add("CLINIC_LOGO_REQUIRED");
            if (!available.contains(ProviderDocumentType.REGISTRATION_CERTIFICATE)) missing.add("CLINIC_REGISTRATION_DOCUMENT_REQUIRED");
        } else {
            if (!available.contains(ProviderDocumentType.LOGO)) missing.add("HOSPITAL_LOGO_REQUIRED");
            if (!available.contains(ProviderDocumentType.REGISTRATION_CERTIFICATE)) missing.add("HOSPITAL_REGISTRATION_DOCUMENT_REQUIRED");
        }
        return missing;
    }

    private static String providerNameRequiredCode(ProviderType providerType) {
        return switch (providerType) {
            case INDIVIDUAL_DOCTOR -> "DOCTOR_NAME_REQUIRED";
            case CLINIC -> "CLINIC_NAME_REQUIRED";
            case HOSPITAL -> "HOSPITAL_NAME_REQUIRED";
        };
    }

    private static String registrationRequiredCode(ProviderType providerType) {
        return switch (providerType) {
            case INDIVIDUAL_DOCTOR -> "DOCTOR_REGISTRATION_NUMBER_REQUIRED";
            case CLINIC -> "CLINIC_REGISTRATION_NUMBER_REQUIRED";
            case HOSPITAL -> "HOSPITAL_REGISTRATION_NUMBER_REQUIRED";
        };
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private record StepState(String code, String label, boolean complete, int weight) {
    }
}
