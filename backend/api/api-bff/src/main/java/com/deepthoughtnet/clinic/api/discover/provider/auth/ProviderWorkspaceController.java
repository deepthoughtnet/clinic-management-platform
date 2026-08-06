package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceApplicationResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceProfileResponse;
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
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationSubmissionRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfilePublicationRecord;
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
import java.time.OffsetDateTime;
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
    private final ProviderPublicProfileDraftService draftService;
    private final ProviderPublicProfileModerationService moderationService;
    private final ProviderLinkingService providerLinkingService;

    public ProviderWorkspaceController(
            DiscoverVerificationService verificationService,
            ProviderOnboardingService onboardingService,
            ProviderOwnershipService providerOwnershipService,
            ClinicProfileService clinicProfileService,
            DoctorProfileService doctorProfileService,
            TenantUserManagementService tenantUserManagementService,
            ProviderPublicProfileService publicProfileService,
            ProviderPublicProfileDraftService draftService,
            ProviderPublicProfileModerationService moderationService,
            ProviderLinkingService providerLinkingService
    ) {
        this.verificationService = verificationService;
        this.onboardingService = onboardingService;
        this.providerOwnershipService = providerOwnershipService;
        this.clinicProfileService = clinicProfileService;
        this.doctorProfileService = doctorProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
        this.publicProfileService = publicProfileService;
        this.draftService = draftService;
        this.moderationService = moderationService;
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
        List<WorkspaceProfileResponse> profiles = loadProfiles(principal.providerAccountId());
        List<WorkspaceApplicationResponse> publishedProfiles = summaries.stream()
                .filter(application -> application.status() == ProviderLifecycleStatus.PUBLISHED)
                .toList();
        List<ProviderAuthModels.ProviderWorkspaceWorkItemResponse> workItems = loadWorkItems(principal.providerAccountId());
        int activeProfileCount = profiles.size();
        int readyForReviewCount = (int) profiles.stream().filter(this::isReadyForReviewProfile).count();
        int underReviewCount = (int) profiles.stream().filter(this::isUnderReviewProfile).count();
        int publishedCount = (int) profiles.stream().filter(profile -> "PUBLISHED".equals(profile.publicationStatus())).count();
        int needsAttentionCount = (int) profiles.stream().filter(WorkspaceProfileResponse::providerActionRequired).count();
        int attentionCount = needsAttentionCount + (int) workItems.stream().filter(this::requiresAttention).count();
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
                profiles,
                attentionCount,
                activeProfileCount,
                readyForReviewCount,
                underReviewCount,
                publishedCount,
                needsAttentionCount,
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

    private List<WorkspaceProfileResponse> loadProfiles(UUID providerAccountId) {
        return draftService.listDraftLifecycle().stream()
                .filter(profile -> providerAccountId.equals(profile.providerAccountId()))
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .map(profile -> toWorkspaceProfile(providerAccountId, profile))
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

    private boolean isReadyForReviewProfile(WorkspaceProfileResponse profile) {
        return "READY_FOR_REVIEW".equals(profile.contentStatus())
                && "READY".equals(profile.readinessStatus())
                && profile.completenessPercentage() == 100
                && !"SUBMITTED".equals(profile.moderationStatus())
                && !"UNDER_REVIEW".equals(profile.moderationStatus())
                && !"CHANGES_REQUESTED".equals(profile.moderationStatus())
                && !"APPROVED".equals(profile.moderationStatus())
                && !"PUBLISHED".equals(profile.publicationStatus());
    }

    private boolean isUnderReviewProfile(WorkspaceProfileResponse profile) {
        return "SUBMITTED".equals(profile.moderationStatus())
                || "UNDER_REVIEW".equals(profile.moderationStatus())
                || "CHANGES_REQUESTED".equals(profile.moderationStatus());
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

    private WorkspaceProfileResponse toWorkspaceProfile(UUID providerAccountId, PublicProfileDraftWorkspaceRecord draft) {
        PublicProfileModerationSubmissionRecord submission = moderationService.findSubmission(draft.publicProfileReference()).orElse(null);
        PublicProfilePublicationRecord publication = moderationService.findCurrentPublication(draft.publicProfileReference()).orElse(null);
        Optional<BookingTargetResolution> bookingTarget = providerLinkingService.resolveBookingTarget(new PublicProviderReference(draft.publicProfileReference(), null));
        String moderationStatus = submission == null ? "NOT_SUBMITTED" : submission.moderationStatus();
        String publicationStatus = publication == null ? draft.publicProfileStatus() : publication.publicationStatus();
        String effectiveVisibility = publication == null ? "NOT_PUBLISHED" : publication.effectiveVisibility();
        String platformConnectionStatus = bookingTarget.map(BookingTargetResolution::connectionStatus).orElse(PlatformConnectionStatus.NOT_CONNECTED).name();
        String bookingCapability = bookingTarget.map(BookingTargetResolution::bookingCapability).orElse(com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability.NOT_AVAILABLE).name();
        String primaryAction = primaryProfileAction(draft, moderationStatus, publicationStatus);
        List<String> allowedActions = profileAllowedActions(draft, moderationStatus, publicationStatus, primaryAction);
        List<String> secondaryActions = allowedActions.stream().filter(action -> !action.equals(primaryAction)).toList();
        List<String> blockingReasons = draft.readiness() == null ? List.of() : draft.readiness().blockingReasons();
        String lifecycleLabel = lifecycleLabel(draft, moderationStatus, publicationStatus);
        String attentionLabel = attentionLabel(draft, moderationStatus, publicationStatus);
        String nextActionLabel = nextActionLabel(draft, moderationStatus, publicationStatus, primaryAction);
        boolean providerActionRequired = switch (primaryAction) {
            case "SUBMIT_FOR_REVIEW", "REVIEW_CHANGES", "CONTINUE_PROFILE", "OPEN_PROFILE" -> true;
            default -> !blockingReasons.isEmpty() && !"PUBLISHED".equals(publicationStatus);
        };
        OffsetDateTime lastUpdatedAt = draft.updatedAt();
        return new WorkspaceProfileResponse(
                draft.draftId(),
                draft.draftReference(),
                draft.publicProfileReference(),
                draft.publicProfileType(),
                draft.displayName(),
                draft.city(),
                draft.area(),
                draft.ownershipStatus(),
                draft.tenantConsentStatus(),
                draft.currentVersion(),
                draft.contentStatus(),
                draft.readinessStatus(),
                draft.completenessPercentage(),
                moderationStatus,
                submission == null ? null : submission.submissionReference(),
                publicationStatus,
                effectiveVisibility,
                platformConnectionStatus,
                bookingCapability,
                lastUpdatedAt,
                blockingReasons,
                allowedActions,
                primaryAction,
                secondaryActions,
                lifecycleLabel,
                attentionLabel,
                nextActionLabel,
                publication == null ? draft.publicProfilePath() : publication.publicPath(),
                providerActionRequired
        );
    }

    private String primaryProfileAction(PublicProfileDraftWorkspaceRecord draft, String moderationStatus, String publicationStatus) {
        if ("PUBLISHED".equals(publicationStatus)) {
            return "VIEW_PUBLIC_PROFILE";
        }
        if ("SUBMITTED".equals(moderationStatus) || "UNDER_REVIEW".equals(moderationStatus)) {
            return "VIEW_REVIEW_STATUS";
        }
        if ("CHANGES_REQUESTED".equals(moderationStatus)) {
            return "REVIEW_CHANGES";
        }
        if ("APPROVED".equals(moderationStatus)) {
            return "VIEW_APPROVAL_STATUS";
        }
        if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus()) && "ENABLED".equalsIgnoreCase(draft.tenantConsentStatus())) {
            return "SUBMIT_FOR_REVIEW";
        }
        if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus())) {
            return "CONTINUE_PROFILE";
        }
        return "CONTINUE_PROFILE";
    }

    private List<String> profileAllowedActions(PublicProfileDraftWorkspaceRecord draft, String moderationStatus, String publicationStatus, String primaryAction) {
        if ("PUBLISHED".equals(publicationStatus)) {
            return List.of("VIEW_PUBLIC_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS");
        }
        if ("SUBMITTED".equals(moderationStatus) || "UNDER_REVIEW".equals(moderationStatus)) {
            return List.of("VIEW_REVIEW_STATUS", "VIEW_PREVIEW", "VIEW_READINESS");
        }
        if ("CHANGES_REQUESTED".equals(moderationStatus)) {
            return List.of("REVIEW_CHANGES", "EDIT_PUBLIC_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS");
        }
        if ("APPROVED".equals(moderationStatus)) {
            return List.of("VIEW_APPROVAL_STATUS", "VIEW_PREVIEW", "VIEW_READINESS");
        }
        if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus()) && "ENABLED".equalsIgnoreCase(draft.tenantConsentStatus())) {
            return List.of("SUBMIT_FOR_REVIEW", "VIEW_PREVIEW", "VIEW_READINESS", "EDIT_PUBLIC_PROFILE");
        }
        return List.of("CONTINUE_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS", "EDIT_PUBLIC_PROFILE");
    }

    private String lifecycleLabel(PublicProfileDraftWorkspaceRecord draft, String moderationStatus, String publicationStatus) {
        if ("PUBLISHED".equals(publicationStatus)) {
            return "Published";
        }
        if ("APPROVED".equals(moderationStatus)) {
            return "Approved by Platform";
        }
        if ("SUBMITTED".equals(moderationStatus)) {
            return "Submitted for Platform Review";
        }
        if ("UNDER_REVIEW".equals(moderationStatus)) {
            return "Platform review in progress";
        }
        if ("CHANGES_REQUESTED".equals(moderationStatus)) {
            return "Changes requested";
        }
        if ("REJECTED".equals(moderationStatus)) {
            return "Rejected by Platform";
        }
        if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus())) {
            return "Ready for Platform Review";
        }
        return "Draft incomplete";
    }

    private String attentionLabel(PublicProfileDraftWorkspaceRecord draft, String moderationStatus, String publicationStatus) {
        if ("PUBLISHED".equals(publicationStatus)) {
            return "Published profile";
        }
        if ("APPROVED".equals(moderationStatus)) {
            return "Waiting for publication";
        }
        if ("SUBMITTED".equals(moderationStatus) || "UNDER_REVIEW".equals(moderationStatus)) {
            return "Platform review pending";
        }
        if ("CHANGES_REQUESTED".equals(moderationStatus)) {
            return "Resolve platform review findings";
        }
        if ("REJECTED".equals(moderationStatus)) {
            return "Start a new revision";
        }
        if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus()) && "ENABLED".equalsIgnoreCase(draft.tenantConsentStatus())) {
            return "Submit profile for review";
        }
        if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus())) {
            return "Enable Discover participation in Healthcare Admin";
        }
        return "Complete required profile sections";
    }

    private String nextActionLabel(PublicProfileDraftWorkspaceRecord draft, String moderationStatus, String publicationStatus, String primaryAction) {
        return switch (primaryAction) {
            case "SUBMIT_FOR_REVIEW" -> "Submit profile for review";
            case "VIEW_REVIEW_STATUS" -> "Platform review pending";
            case "REVIEW_CHANGES" -> "Resolve platform review findings";
            case "VIEW_APPROVAL_STATUS" -> "Waiting for publication";
            case "VIEW_PUBLIC_PROFILE" -> "View public profile";
            default -> {
                if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus()) && !"ENABLED".equalsIgnoreCase(draft.tenantConsentStatus())) {
                    yield "Enable Discover participation in Healthcare Admin";
                }
                if ("READY".equals(draft.readinessStatus()) && "READY_FOR_REVIEW".equals(draft.contentStatus())) {
                    yield "Continue editing";
                }
                yield "Continue editing";
            }
        };
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
                record.publicProfilePath(),
                allowedActions(record)
        );
    }

    private List<String> allowedActions(ProviderWorkspaceApplicationRecord record) {
        return switch (record.status()) {
            case DRAFT -> List.of("OPEN_PROFILE", "CONTINUE_PROFILE");
            case CONTACT_VERIFIED, PROFILE_INCOMPLETE -> List.of("CONTINUE_PROFILE", "ENABLE_DISCOVER");
            case READY_FOR_REVIEW -> List.of("SUBMIT_FOR_REVIEW", "CONTINUE_PROFILE");
            case SUBMITTED, UNDER_REVIEW -> List.of("VIEW_UNDER_REVIEW", "AWAITING_APPROVAL");
            case CHANGES_REQUESTED -> List.of("REVIEW_CHANGES", "CONTINUE_PROFILE");
            case APPROVED -> List.of("AWAITING_APPROVAL", "VIEW_REVIEW");
            case PUBLISHED -> List.of("VIEW_PUBLISHED_PROFILE");
            case DISCARDED, SUSPENDED, ARCHIVED -> List.of("VIEW_DETAILS");
        };
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
