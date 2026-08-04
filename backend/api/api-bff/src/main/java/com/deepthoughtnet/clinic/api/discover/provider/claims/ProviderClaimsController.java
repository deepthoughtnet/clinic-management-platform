package com.deepthoughtnet.clinic.api.discover.provider.claims;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.DisputeRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipSnapshot;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderSessionPrincipal;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/provider/claims")
@PreAuthorize("hasRole('PROVIDER')")
public class ProviderClaimsController {
    private final ProviderOwnershipService providerOwnershipService;
    private final ClinicProfileService clinicProfileService;
    private final DoctorProfileService doctorProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final ProviderPublicProfileService publicProfileService;
    private final ProviderLinkingService providerLinkingService;

    public ProviderClaimsController(
            ProviderOwnershipService providerOwnershipService,
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            TenantUserManagementService tenantUserManagementService,
            ProviderPublicProfileService publicProfileService,
            ProviderLinkingService providerLinkingService
    ) {
        this.providerOwnershipService = providerOwnershipService;
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.publicProfileService = publicProfileService;
        this.providerLinkingService = providerLinkingService;
    }

    @GetMapping("/{connectionReference}")
    public ProviderClaimReviewResponse review(Authentication authentication, @PathVariable String connectionReference) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        ClaimIntentRecord intent = providerOwnershipService.authenticateClaimIntent(connectionReference, principal.providerAccountId());
        return toReview(intent, principal.providerAccountId(), principal.sessionId());
    }

    @PostMapping("/{connectionReference}/submit")
    public ProviderClaimReviewResponse submit(
            Authentication authentication,
            @PathVariable String connectionReference,
            @RequestBody(required = false) ProviderClaimSubmissionRequest request
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        String reason = request == null ? null : request.reason();
        String evidence = request == null ? null : request.evidenceSnapshotJson();
        OwnershipSnapshot snapshot = providerOwnershipService.submitClaim(connectionReference, principal.providerAccountId(), principal.sessionId(), evidence, reason);
        ClaimIntentRecord intent = providerOwnershipService.findClaimIntent(connectionReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim intent not found"));
        return toReview(intent, principal.providerAccountId(), principal.sessionId(), snapshot);
    }

    private ProviderClaimReviewResponse toReview(ClaimIntentRecord intent, UUID providerAccountId, UUID actorId) {
        return toReview(intent, providerAccountId, actorId, null);
    }

    private ProviderClaimReviewResponse toReview(ClaimIntentRecord intent, UUID providerAccountId, UUID actorId, OwnershipSnapshot snapshot) {
        PublicProfileType profileType = intent.publicProfileType();
        String tenantReference = intent.tenantReference();
        ClinicProfileRecord clinic = null;
        DoctorProfileRecord doctor = null;
        TenantUserRecord doctorUser = null;
        if (profileType == PublicProfileType.CLINIC) {
            clinic = clinicProfileService.findByTenantId(UUID.fromString(tenantReference)).orElse(null);
        } else if (profileType == PublicProfileType.DOCTOR) {
            UUID tenantId = UUID.fromString(tenantReference);
            UUID doctorUserId = UUID.fromString(intent.publicProfileReference());
            doctor = doctorProfileService.findByDoctorUserId(tenantId, doctorUserId).orElse(null);
            doctorUser = tenantUserManagementService.list(tenantId).stream()
                    .filter(item -> doctorUserId.equals(item.appUserId()))
                    .findFirst()
                    .orElse(null);
            clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        }
        PublicProfileLifecycleRecord lifecycle = profileType == PublicProfileType.CLINIC
                ? publicProfileService.findLifecycleByProviderId(clinic == null ? null : clinic.id()).orElse(null)
                : doctor == null ? null : publicProfileService.findLifecycleByProviderId(doctor.doctorUserId()).orElse(null);
        OwnershipRecord ownership = snapshot == null
                ? providerOwnershipService.findLatestOwnership(intent.publicProfileReference()).orElse(null)
                : snapshot.ownership();
        List<MembershipRecord> memberships = snapshot == null
                ? providerOwnershipService.listMemberships(intent.publicProfileReference())
                : snapshot.memberships();
        List<DisputeRecord> disputes = snapshot == null
                ? providerOwnershipService.listDisputes(intent.publicProfileReference())
                : snapshot.disputes();
        Optional<BookingTargetResolution> bookingTarget = providerLinkingService.resolveBookingTarget(
                new PublicProviderReference(
                        intent.publicProfileReference(),
                        profileType == PublicProfileType.DOCTOR ? doctor == null ? null : doctor.slug() : null
                )
        );
        String bookingCapability = bookingTarget.map(BookingTargetResolution::bookingCapability).orElse(BookingCapability.NOT_AVAILABLE).name();
        String connectionStatus = bookingTarget.map(BookingTargetResolution::connectionStatus).orElse(PlatformConnectionStatus.NOT_CONNECTED).name();
        String publicProfileStatus = lifecycle == null ? "UNPUBLISHED" : lifecycle.publicationStatus();
        String ownershipStatus = ownership == null ? "UNCLAIMED" : ownership.status().name();
        String pageMode = providerOwnershipService.claimPageMode(intent, ownership);
        String workItemStatus = providerOwnershipService.claimWorkItemStatus(intent, ownership);
        String reviewStatus = providerOwnershipService.claimReviewStatus(intent, ownership);
        String tenantConsentStatus = clinic == null || !clinic.publicListingEnabled() ? "DISABLED" : "ENABLED";
        OffsetDateTime ownershipUpdatedAt = ownership == null ? intent.updatedAt() : ownership.updatedAt();
        OffsetDateTime reviewedAt = ownership == null ? null : ownership.verifiedAt();
        OffsetDateTime submittedAt = intent.claimSubmittedAt();
        List<String> allowedActions = providerOwnershipService.claimReviewAllowedActions(intent, ownership);
        return new ProviderClaimReviewResponse(
                intent.connectionReference(),
                intent.status().name(),
                pageMode,
                workItemStatus,
                reviewStatus,
                submittedAt,
                reviewedAt,
                ownershipUpdatedAt,
                intent.reason(),
                intent.reason(),
                providerOwnershipService.maskedProviderMobile(providerAccountId).orElse(null),
                profileType.name(),
                clinic == null ? null : clinic.displayName(),
                clinic == null ? null : clinic.city(),
                clinic == null ? null : clinic.addressLine1(),
                doctor == null ? null : doctor.qualification(),
                doctor == null ? null : doctor.specialization(),
                doctor == null ? null : doctor.yearsOfExperience(),
                tenantConsentStatus,
                publicProfileStatus,
                connectionStatus,
                bookingCapability,
                ownershipStatus,
                memberships.stream().map(MembershipRecord::role).map(Enum::name).toList(),
                disputes.stream().map(DisputeRecord::status).map(Enum::name).toList(),
                doctorUser == null ? null : doctorUser.displayName(),
                allowedActions
        );
    }

    private ProviderSessionPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ProviderSessionPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Provider session is required");
        }
        return principal;
    }

    public record ProviderClaimSubmissionRequest(
            String reason,
            String evidenceSnapshotJson
    ) {
    }

    public record ProviderClaimReviewResponse(
            String connectionReference,
            String status,
            String pageMode,
            String workItemStatus,
            String reviewStatus,
            OffsetDateTime submittedAt,
            OffsetDateTime reviewedAt,
            OffsetDateTime ownershipUpdatedAt,
            String reason,
            String claimNote,
            String maskedProviderMobile,
            String publicProfileType,
            String displayName,
            String city,
            String area,
            String qualification,
            String specialty,
            Integer yearsOfExperience,
            String tenantConsentStatus,
            String publicProfileStatus,
            String platformConnectionStatus,
            String bookingCapability,
            String ownershipStatus,
            List<String> membershipRoles,
            List<String> disputeStatuses,
            String doctorUserDisplayName,
            List<String> allowedActions
    ) {
    }

}
