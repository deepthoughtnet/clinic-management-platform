package com.deepthoughtnet.clinic.api.platform.providerconnections;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchConfidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchMethod;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicationStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderMatchEvidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record ProviderConnectionsOverviewResponse(
        List<ProviderConnectionsMetricResponse> metrics
) {
}

record ProviderConnectionsMetricResponse(
        String key,
        String label,
        long value,
        String helperText,
        String path
) {
}

record ProviderConnectionsPublicProfileResponse(
        PublicProfileType publicProfileType,
        String publicReference,
        String publicPracticeReference,
        String displayName,
        String slug,
        String publicPath,
        String city,
        String area,
        String publicPhone,
        String publicFee,
        BookingCapability bookingCapability,
        AvailabilityState availabilityState,
        PublicationStatus publicationStatus,
        SourceSystem sourceSystem,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt,
        OffsetDateTime projectedAt,
        boolean connected,
        LinkLifecycleStatus linkStatus,
        PlatformConnectionStatus connectionStatus,
        String platformClinicReference,
        String tenantReference,
        List<String> tags
) {
}

record ProviderConnectionsPlatformEntityResponse(
        String entityType,
        UUID tenantId,
        String tenantReference,
        String tenantCode,
        String tenantName,
        String displayName,
        String city,
        String area,
        String phone,
        String email,
        String specialty,
        String qualification,
        String registrationNumber,
        Integer yearsOfExperience,
        boolean active,
        boolean publicListingEnabled,
        String publicListingConsent,
        String slug,
        String platformClinicReference,
        String tenantDoctorUserReference,
        String tenantDoctorProfileReference,
        String bookingCapability,
        String platformBookingSetup,
        String currentDiscoverCapability,
        String currentAvailability,
        String capabilityReason,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt,
        String linkedPublicReference,
        String linkStatus,
        String connectionStatus
) {
}

record ProviderConnectionsLinkResponse(
        UUID id,
        PublicProfileType publicProfileType,
        String publicReference,
        String publicPracticeReference,
        String tenantReference,
        String tenantName,
        String platformClinicReference,
        String tenantDoctorUserReference,
        String tenantDoctorProfileReference,
        LinkLifecycleStatus linkStatus,
        PlatformConnectionStatus connectionStatus,
        BookingCapability bookingCapability,
        AvailabilityState availabilityState,
        MatchMethod matchMethod,
        MatchConfidence matchConfidence,
        String reason,
        String bookingReferenceMasked,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String publicDisplayName,
        String publicCity,
        String publicArea,
        String publicPath,
        String sourceSystem,
        List<ProviderMatchEvidence> evidence
) {
}

record ProviderConnectionsLinkDetailResponse(
        ProviderConnectionsLinkResponse link,
        List<ProviderConnectionsComparisonRowResponse> comparison,
        List<ProviderConnectionsAuditResponse> audit
) {
}

record ProviderConnectionsComparisonRowResponse(
        String key,
        String label,
        String publicValue,
        String platformValue,
        String status
) {
}

record ProviderConnectionsAuditResponse(
        UUID id,
        String action,
        String summary,
        UUID actorAppUserId,
        OffsetDateTime occurredAt,
        String detailsJson,
        String providerType,
        String tenantReference,
        String platformClinicReference,
        String previousState,
        String newState,
        String result,
        String correlationId
) {
}

record ProviderConnectionsSuggestionResponse(
        String id,
        PublicProfileType publicProfileType,
        String publicReference,
        String publicPracticeReference,
        String publicDisplayName,
        String platformDisplayName,
        String tenantReference,
        String platformClinicReference,
        String tenantDoctorUserReference,
        String tenantDoctorProfileReference,
        String platformCity,
        String platformArea,
        String platformPhone,
        String platformEmail,
        String platformSpecialty,
        String platformQualification,
        String platformRegistrationNumber,
        Integer platformYearsOfExperience,
        String platformBookingSetup,
        String currentDiscoverCapability,
        String currentAvailability,
        String bookingReference,
        MatchMethod matchMethod,
        MatchConfidence confidence,
        List<ProviderMatchEvidence> evidence,
        String reason,
        String status,
        OffsetDateTime lastEvaluatedAt,
        long sourceRevision
) {
}

record ProviderConnectionsConflictResponse(
        String id,
        String severity,
        String title,
        String details,
        String linkId,
        String publicReference,
        String tenantReference
) {
}

record ProviderConnectionsSuggestionDecisionRequest(
        String reason
) {
}

record ProviderConnectionsLifecycleResponse(
        PublicProfileType publicProfileType,
        String sourceSystem,
        String sourceEntityReference,
        String displayName,
        String canonicalSlug,
        String publicPath,
        String city,
        String area,
        String publicationStatus,
        String ownershipStatus,
        String tenantConsentStatus,
        String draftReference,
        String draftStatus,
        String draftReadinessStatus,
        int draftCompletenessPercentage,
        int draftVersionNumber,
        OffsetDateTime draftLastSavedAt,
        List<String> draftAllowedActions,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt,
        OffsetDateTime projectedAt,
        long connectionRevision,
        boolean ready,
        List<String> missingFields,
        List<String> invalidFields,
        List<String> warnings,
        String moderationStatus,
        String submissionReference,
        OffsetDateTime submittedAt,
        String assignedReviewer,
        OffsetDateTime assignedAt,
        long ageInQueueDays,
        String sourceType,
        List<String> moderationAllowedActions
) {
}

record ProviderConnectionsLinkProposalRequest(
        PublicProfileType publicProfileType,
        String publicReference,
        String publicPracticeReference,
        String tenantReference,
        String platformClinicReference,
        String tenantDoctorUserReference,
        String tenantDoctorProfileReference,
        long platformEntityRevision,
        SourceSystem sourceSystem,
        String sourceEntityReference,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt,
        LinkLifecycleStatus linkStatus,
        PlatformConnectionStatus connectionStatus,
        MatchMethod matchMethod,
        MatchConfidence matchConfidence,
        String reason,
        List<String> evidence
) {
}

record ProviderConnectionsLinkUpdateRequest(
        String reason
) {
}

record ProviderConnectionsReconcileRequest(
        PublicProfileType publicProfileType,
        UUID linkId,
        String tenantReference
) {
}

record ProviderConnectionsOwnershipResponse(
        UUID ownershipId,
        PublicProfileType publicProfileType,
        String publicProfileReference,
        String displayName,
        String city,
        String area,
        String maskedProviderMobile,
        String consentState,
        String publicProfileStatus,
        String platformConnectionStatus,
        String bookingCapability,
        String ownershipStatus,
        String ownershipMethod,
        String reason,
        boolean active,
        long sourceRevision,
        OffsetDateTime verifiedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<String> membershipRoles,
        List<String> disputeStatuses,
        List<String> allowedActions
) {
}

record ProviderConnectionsOwnershipDecisionRequest(
        String reason
) {
}

record HealthcareProviderFactsRow(
        String entityType,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        String displayName,
        String city,
        String area,
        String phone,
        String email,
        String specialty,
        String qualification,
        String registrationNumber,
        Integer yearsOfExperience,
        boolean active,
        boolean publicListingEnabled,
        String slug,
        UUID doctorUserId,
        UUID doctorProfileId,
        String tenantDoctorUserReference,
        String tenantDoctorProfileReference,
        OffsetDateTime updatedAt,
        long sourceRevision
) {
}
