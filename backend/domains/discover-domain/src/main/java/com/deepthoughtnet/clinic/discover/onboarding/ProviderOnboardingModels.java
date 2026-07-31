package com.deepthoughtnet.clinic.discover.onboarding;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderServiceType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProviderOnboardingModels {
    private ProviderOnboardingModels() {
    }

    public record CreateProviderApplicationCommand(
            ProviderType providerType,
            String email,
            String phone,
            String password,
            Boolean termsAccepted,
            Boolean privacyAccepted
    ) {
    }

    public record UpdateProviderApplicationCommand(
            Long version,
            String email,
            String phone,
            Boolean contactVerified,
            Boolean termsAccepted,
            Boolean privacyAccepted,
            String displayName,
            String legalName,
            String organisationType,
            String registrationNumber,
            String gstNumber,
            String website,
            String gender,
            LocalDate dateOfBirth,
            List<String> languages,
            String biography,
            String medicalCouncil,
            String qualification,
            Integer yearsOfExperience,
            List<String> specialities,
            List<String> subSpecialities,
            BigDecimal consultationFee,
            Boolean onlineConsultation,
            Integer appointmentDurationMinutes,
            String ownership,
            String hospitalType,
            Integer beds,
            Boolean emergencyAvailable,
            String medicalDirector,
            List<String> departments,
            List<String> facilities,
            List<String> accreditations,
            List<LocationCommand> locations,
            List<ServiceCommand> services,
            BrandingCommand branding
    ) {
    }

    public record ResubmitProviderApplicationCommand(
            String providerResponseNote
    ) {
    }

    public record LocationCommand(
            UUID id,
            String label,
            String address,
            String city,
            String state,
            String country,
            String pinCode,
            String workingHours,
            Boolean parkingAvailable,
            Boolean accessibilityAvailable,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record ServiceCommand(
            UUID id,
            ProviderServiceType serviceType,
            String label,
            String description,
            Boolean enabled
    ) {
    }

    public record BrandingCommand(
            UUID logoDocumentId,
            UUID coverImageDocumentId,
            UUID doctorPhotoDocumentId,
            String primaryColor,
            String tagline
    ) {
    }

    public record ContactVerificationStatusRecord(
            String email,
            String emailStatus,
            OffsetDateTime emailVerifiedAt,
            String phone,
            String phoneStatus,
            OffsetDateTime phoneVerifiedAt,
            boolean requirementSatisfied
    ) {
    }

    public record VerificationChallengeRecord(
            String message,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String devCode,
            long expiresInSeconds,
            long resendAfterSeconds
    ) {
    }

    public record UploadedDocumentCommand(
            ProviderDocumentType documentType,
            String originalFilename,
            String contentType,
            long sizeBytes,
            byte[] bytes
    ) {
    }

    public record ProviderApplicationRecord(
            UUID id,
            String referenceNumber,
            ProviderType providerType,
            ProviderLifecycleStatus status,
            long version,
            int completionPercent,
            String currentStep,
            String email,
            String phone,
            boolean contactVerified,
            boolean termsAccepted,
            boolean privacyAccepted,
            String displayName,
            String legalName,
            String organisationType,
            String registrationNumber,
            String gstNumber,
            String website,
            String gender,
            LocalDate dateOfBirth,
            List<String> languages,
            String biography,
            String medicalCouncil,
            String qualification,
            Integer yearsOfExperience,
            List<String> specialities,
            List<String> subSpecialities,
            BigDecimal consultationFee,
            boolean onlineConsultation,
            Integer appointmentDurationMinutes,
            String ownership,
            String hospitalType,
            Integer beds,
            boolean emergencyAvailable,
            String medicalDirector,
            List<String> departments,
            List<String> facilities,
            List<String> accreditations,
            BrandingRecord branding,
            List<LocationRecord> locations,
            List<ServiceRecord> services,
            List<DocumentRecord> documents,
            List<StatusHistoryRecord> statusHistory,
            List<String> missingItems,
            ContactVerificationStatusRecord contactVerification,
            OffsetDateTime lastSavedAt,
            OffsetDateTime submittedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String onboardingToken
    ) {
    }

    public record ProviderCompletionRecord(
            int completionPercentage,
            List<String> completedSteps,
            List<String> incompleteSteps,
            List<String> missingRequiredFields,
            List<String> missingRequiredDocuments,
            List<String> validationWarnings,
            List<String> blockingErrors,
            boolean canSubmit,
            boolean previewReady,
            String recommendedNextStep,
            String currentStep,
            boolean readOnly
    ) {
    }

    public record ProviderChangeRequestRecord(
            UUID id,
            Integer submissionVersionNumber,
            List<String> requestedSections,
            String reviewerMessage,
            String providerResponseNote,
            OffsetDateTime requestedAt,
            OffsetDateTime resolvedAt,
            boolean resolved
    ) {
    }

    public record ProviderTimelineEventRecord(
            String label,
            String description,
            String actorCategory,
            OffsetDateTime timestamp
    ) {
    }

    public record ProviderDashboardRecord(
            ProviderApplicationRecord application,
            ProviderCompletionRecord completion,
            List<ProviderTimelineEventRecord> timeline,
            List<ProviderChangeRequestRecord> changeRequests,
            boolean readOnly,
            String nextRecommendedAction
    ) {
    }

    public record ProviderOnboardingAccessRecord(
            UUID applicationId,
            String onboardingToken
    ) {
    }

    public record ProviderWorkspaceStartRecord(
            UUID applicationId,
            String referenceNumber,
            ProviderType providerType,
            ProviderLifecycleStatus status,
            String currentStep,
            String onboardingToken,
            String publicProfilePath
    ) {
    }

    public record ProviderReviewSummaryRecord(
            UUID id,
            String referenceNumber,
            ProviderType providerType,
            ProviderLifecycleStatus status,
            long version,
            String displayName,
            String registrationNumber,
            String email,
            String phone,
            boolean contactVerified,
            String city,
            String state,
            String country,
            OffsetDateTime submittedAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record ProviderReviewDetailRecord(
            ProviderApplicationRecord application,
            ProviderCompletionRecord completion,
            ProviderPreviewRecord preview,
            List<ProviderTimelineEventRecord> timeline,
            List<ProviderChangeRequestRecord> changeRequests,
            String publicProfilePath,
            boolean published
    ) {
    }

    public record LocationRecord(
            UUID id,
            String label,
            String address,
            String city,
            String state,
            String country,
            String pinCode,
            String workingHours,
            boolean parkingAvailable,
            boolean accessibilityAvailable,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record ServiceRecord(
            UUID id,
            ProviderServiceType serviceType,
            String label,
            String description,
            boolean enabled
    ) {
    }

    public record BrandingRecord(
            UUID logoDocumentId,
            UUID coverImageDocumentId,
            UUID doctorPhotoDocumentId,
            String primaryColor,
            String tagline,
            List<UUID> galleryDocumentIds
    ) {
    }

    public record DocumentRecord(
            UUID id,
            ProviderDocumentType documentType,
            String originalFilename,
            String contentType,
            long sizeBytes,
            OffsetDateTime uploadedAt,
            String virusScanStatus
    ) {
    }

    public record DocumentContentRecord(
            UUID documentId,
            String contentType,
            String originalFilename,
            byte[] bytes
    ) {
    }

    public record StatusHistoryRecord(
            UUID id,
            ProviderLifecycleStatus fromStatus,
            ProviderLifecycleStatus toStatus,
            String reason,
            OffsetDateTime createdAt
    ) {
    }

    public record ProviderPreviewRecord(
            UUID providerId,
            ProviderType providerType,
            String displayName,
            String subtitle,
            String locationSummary,
            List<String> services,
            List<String> specialities,
            String biography,
            BrandingRecord branding,
            int completionPercent,
            boolean readyForSubmission,
            List<String> missingItems
    ) {
    }
}
