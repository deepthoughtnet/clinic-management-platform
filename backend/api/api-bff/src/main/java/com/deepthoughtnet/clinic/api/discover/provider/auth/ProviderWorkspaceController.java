package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceApplicationResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.ProviderOnboardingAccessResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.ProviderWorkspaceStartRequest;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.ProviderWorkspaceStartResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceResponse;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderDashboardRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderOnboardingAccessRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderWorkspaceStartRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.ProviderWorkspaceApplicationRecord;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import java.util.List;
import java.util.UUID;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
@PreAuthorize("hasRole('PROVIDER')")
public class ProviderWorkspaceController {
    private final DiscoverVerificationService verificationService;
    private final ProviderOnboardingService onboardingService;
    private final ProviderOwnershipService providerOwnershipService;
    private final ClinicProfileService clinicProfileService;
    private final DoctorProfileService doctorProfileService;
    private final TenantUserManagementService tenantUserManagementService;
    private final ProviderPublicProfileService publicProfileService;
    private final ProviderLinkingService providerLinkingService;

    public ProviderWorkspaceController(
            DiscoverVerificationService verificationService,
            ProviderOnboardingService onboardingService,
            ProviderOwnershipService providerOwnershipService,
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            TenantUserManagementService tenantUserManagementService,
            ProviderPublicProfileService publicProfileService,
            ProviderLinkingService providerLinkingService
    ) {
        this.verificationService = verificationService;
        this.onboardingService = onboardingService;
        this.providerOwnershipService = providerOwnershipService;
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.publicProfileService = publicProfileService;
        this.providerLinkingService = providerLinkingService;
    }

    @GetMapping("/me")
    public ResponseEntity<WorkspaceResponse> me(Authentication authentication) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        DiscoverProviderAccountEntity account = verificationService.findAccountById(principal.providerAccountId())
                .orElseThrow(() -> new IllegalStateException("provider account is required"));
        List<WorkspaceApplicationResponse> summaries = loadApplications(principal.providerAccountId());
        List<WorkspaceApplicationResponse> activeApplications = summaries.stream()
                .filter(this::isActiveApplication)
                .toList();
        List<WorkspaceApplicationResponse> publishedProfiles = summaries.stream()
                .filter(application -> application.status() == ProviderLifecycleStatus.PUBLISHED)
                .toList();
        List<ProviderAuthModels.ProviderWorkspaceWorkItemResponse> workItems = loadWorkItems(principal.providerAccountId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new WorkspaceResponse(
                account.getNormalizedEmail(),
                account.getNormalizedPhone(),
                account.getEmailVerifiedAt(),
                account.getPhoneVerifiedAt(),
                workItems,
                activeApplications,
                publishedProfiles,
                (int) (activeApplications.stream().filter(WorkspaceApplicationResponse::requiresAttention).count()
                        + workItems.stream().filter(this::requiresAttention).count()),
                List.of(ProviderType.INDIVIDUAL_DOCTOR, ProviderType.CLINIC, ProviderType.HOSPITAL)
        ));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<WorkspaceApplicationResponse>> applications(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(loadApplications(requirePrincipal(authentication).providerAccountId()).stream()
                        .filter(this::isActiveApplication)
                        .toList());
    }

    @GetMapping("/applications/{referenceNumber}/dashboard")
    public ResponseEntity<ProviderDashboardRecord> applicationDashboard(
            Authentication authentication,
            @PathVariable String referenceNumber
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(onboardingService.dashboardForOwnedApplication(referenceNumber, principal.providerAccountId()));
    }

    @PostMapping("/applications/{referenceNumber}/discard")
    public ResponseEntity<ProviderApplicationRecord> discard(
            Authentication authentication,
            @PathVariable String referenceNumber,
            @RequestBody(required = false) DiscardRequest request
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(onboardingService.discardOwnedApplication(referenceNumber, principal.providerAccountId(), request == null ? null : request.reason()));
    }

    @PostMapping("/applications/{referenceNumber}/onboarding-access")
    public ResponseEntity<ProviderOnboardingAccessResponse> onboardingAccess(
            Authentication authentication,
            @PathVariable String referenceNumber
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        ProviderOnboardingAccessRecord access = onboardingService.issueOnboardingAccess(referenceNumber, principal.providerAccountId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ProviderOnboardingAccessResponse(access.applicationId(), access.onboardingToken()));
    }

    @PostMapping("/applications/start")
    public ResponseEntity<ProviderWorkspaceStartResponse> start(
            Authentication authentication,
            @RequestBody ProviderWorkspaceStartRequest request
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        ProviderWorkspaceStartRecord start = onboardingService.startOrResumeOwnedApplication(
                request.providerType(),
                principal.providerAccountId(),
                Boolean.TRUE.equals(request.createNew())
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ProviderWorkspaceStartResponse(
                        start.applicationId(),
                        start.referenceNumber(),
                        start.providerType(),
                        start.status(),
                        start.currentStep(),
                        start.onboardingToken(),
                        start.publicProfilePath()
                ));
    }

    private List<WorkspaceApplicationResponse> loadApplications(UUID providerAccountId) {
        return verificationService.findOwnedApplicationSummaries(providerAccountId).stream()
                .map(this::toResponse)
                .toList();
    }

    private List<ProviderAuthModels.ProviderWorkspaceWorkItemResponse> loadWorkItems(UUID providerAccountId) {
        List<ClaimIntentRecord> claimIntents = providerOwnershipService.listClaimIntents(providerAccountId);
        List<OwnershipRecord> ownerships = providerOwnershipService.listOwnerships();
        return claimIntents.stream()
                .map(claim -> toWorkItem(claim, ownerships, providerAccountId))
                .toList();
    }

    private ProviderAuthModels.ProviderWorkspaceWorkItemResponse toWorkItem(ClaimIntentRecord claim, List<OwnershipRecord> ownerships, UUID providerAccountId) {
        OwnershipRecord ownership = ownerships.stream()
                .filter(item -> providerAccountId.equals(item.providerAccountId()) && claim.publicProfileReference().equals(item.publicProfileReference()))
                .findFirst()
                .orElse(null);
        ProviderContext context = resolveProviderContext(claim.publicProfileType(), claim.tenantReference(), claim.publicProfileReference());
        PublicProfileLifecycleRecord lifecycle = context.providerId() == null
                ? null
                : publicProfileService.findLifecycleByProviderId(context.providerId()).orElse(null);
        Optional<BookingTargetResolution> bookingTarget = context.publicProviderReference() == null
                ? Optional.empty()
                : providerLinkingService.resolveBookingTarget(context.publicProviderReference());
        String membershipRole = ownership == null ? null : providerOwnershipService.listMemberships(ownership.publicProfileReference()).stream()
                .filter(membership -> providerAccountId.equals(membership.providerAccountId()))
                .findFirst()
                .map(membership -> membership.role().name() + ":" + membership.status())
                .orElse(null);
        String claimStatus = claim.status().name();
        String ownershipStatus = ownership == null ? "UNCLAIMED" : ownership.status().name();
        String workItemStatus = providerOwnershipService.claimWorkItemStatus(claim, ownership);
        return new ProviderAuthModels.ProviderWorkspaceWorkItemResponse(
                "OWNERSHIP_CLAIM",
                claim.publicProfileType().name(),
                claim.connectionReference(),
                claim.publicProfileReference(),
                claim.connectionReference(),
                context.displayName(),
                context.city(),
                context.area(),
                claimStatus,
                ownershipStatus,
                providerOwnershipService.claimReviewStatus(claim, ownership),
                workItemStatus,
                context.publicDiscoveryConsent() ? "ENABLED" : "DISABLED",
                bookingTarget.map(BookingTargetResolution::connectionStatus).orElse(PlatformConnectionStatus.NOT_CONNECTED).name(),
                lifecycle == null ? "UNPUBLISHED" : lifecycle.publicationStatus(),
                membershipRole,
                claim.updatedAt(),
                providerOwnershipService.workspaceAllowedActions(claim, ownership)
        );
    }

    private boolean requiresAttention(ProviderAuthModels.ProviderWorkspaceWorkItemResponse workItem) {
        return "OWNERSHIP_CLAIM".equals(workItem.workItemType())
                && (
                        "CREATED".equals(workItem.claimStatus())
                                || "OPENED".equals(workItem.claimStatus())
                                || "PROVIDER_AUTHENTICATED".equals(workItem.claimStatus())
                                || "CLAIM_SUBMITTED".equals(workItem.claimStatus())
                                || "CLAIM_PENDING".equals(workItem.ownershipStatus())
                                || ("OWNERSHIP_VERIFIED".equals(workItem.workItemStatus())
                                && "DISABLED".equals(workItem.publicDiscoveryConsent()))
                );
    }

    private ProviderContext resolveProviderContext(PublicProfileType type, String tenantReference, String publicProfileReference) {
        if (type == PublicProfileType.DOCTOR) {
            UUID tenantId = parseUuid(tenantReference);
            UUID doctorUserId = parseUuid(publicProfileReference);
            if (tenantId == null || doctorUserId == null) {
                return ProviderContext.empty();
            }
            DoctorProfileRecord doctor = doctorProfileService.findByDoctorUserId(tenantId, doctorUserId).orElse(null);
            ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
            TenantUserRecord doctorUser = tenantUserManagementService.list(tenantId).stream()
                    .filter(item -> doctorUserId.equals(item.appUserId()))
                    .findFirst()
                    .orElse(null);
            return new ProviderContext(
                    doctorUser == null ? null : doctorUser.displayName(),
                    clinic == null ? null : clinic.city(),
                    doctor == null ? null : doctor.consultationRoom(),
                    doctor == null ? null : doctor.doctorUserId(),
                    new PublicProviderReference(publicProfileReference, doctor == null ? null : doctor.slug()),
                    doctor != null && doctor.publicListingEnabled()
            );
        }
        UUID tenantId = parseUuid(publicProfileReference);
        if (tenantId == null) {
            return ProviderContext.empty();
        }
        ClinicProfileRecord clinic = clinicProfileService.findByTenantId(tenantId).orElse(null);
        return new ProviderContext(
                clinic == null ? null : clinic.displayName(),
                clinic == null ? null : clinic.city(),
                clinic == null ? null : clinic.addressLine1(),
                tenantId,
                new PublicProviderReference(publicProfileReference, null),
                clinic != null && clinic.publicListingEnabled()
        );
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record ProviderContext(String displayName, String city, String area, UUID providerId, PublicProviderReference publicProviderReference, boolean publicDiscoveryConsent) {
        static ProviderContext empty() {
            return new ProviderContext(null, null, null, null, null, false);
        }
    }

    private WorkspaceApplicationResponse toResponse(ProviderWorkspaceApplicationRecord record) {
        return new WorkspaceApplicationResponse(
                record.id(),
                record.referenceNumber(),
                record.providerType(),
                record.status(),
                record.displayName(),
                record.completionPercent(),
                record.currentStep(),
                record.contactVerified(),
                record.requiresAttention(),
                record.missingRequirementCount(),
                record.previewReady(),
                record.updatedAt(),
                record.submittedAt(),
                record.publicProfilePath()
        );
    }

    private boolean isActiveApplication(WorkspaceApplicationResponse application) {
        return application.status() != ProviderLifecycleStatus.PUBLISHED
                && application.status() != ProviderLifecycleStatus.DISCARDED
                && application.status() != ProviderLifecycleStatus.ARCHIVED;
    }

    private ProviderSessionPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ProviderSessionPrincipal principal)) {
            throw new IllegalStateException("provider session is required");
        }
        return principal;
    }

    public record DiscardRequest(String reason) {
    }
}
