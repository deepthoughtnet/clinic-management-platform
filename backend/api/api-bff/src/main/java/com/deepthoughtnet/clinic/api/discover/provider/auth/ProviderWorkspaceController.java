package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceApplicationResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.ProviderOnboardingAccessResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceResponse;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderDashboardRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderOnboardingAccessRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.ProviderWorkspaceApplicationRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new WorkspaceResponse(
                account.getNormalizedEmail(),
                account.getNormalizedPhone(),
                account.getEmailVerifiedAt(),
                account.getPhoneVerifiedAt(),
                loadApplications(principal.providerAccountId())
        ));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<WorkspaceApplicationResponse>> applications(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(loadApplications(requirePrincipal(authentication).providerAccountId()));
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
                record.updatedAt(),
                record.submittedAt(),
                record.publicProfilePath()
        );
    }

    private ProviderSessionPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ProviderSessionPrincipal principal)) {
            throw new IllegalStateException("provider session is required");
        }
        return principal;
    }
}
