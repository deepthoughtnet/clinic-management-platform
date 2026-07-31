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
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.ProviderWorkspaceApplicationRecord;
import java.util.List;
import java.util.UUID;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
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

    public ProviderWorkspaceController(
            DiscoverVerificationService verificationService,
            ProviderOnboardingService onboardingService
    ) {
        this.verificationService = verificationService;
        this.onboardingService = onboardingService;
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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new WorkspaceResponse(
                account.getNormalizedEmail(),
                account.getNormalizedPhone(),
                account.getEmailVerifiedAt(),
                account.getPhoneVerifiedAt(),
                activeApplications,
                publishedProfiles,
                (int) activeApplications.stream().filter(WorkspaceApplicationResponse::requiresAttention).count(),
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
