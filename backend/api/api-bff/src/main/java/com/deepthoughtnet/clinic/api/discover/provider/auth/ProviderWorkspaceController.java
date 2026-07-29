package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceApplicationResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceResponse;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.ProviderWorkspaceApplicationRecord;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/provider")
@PreAuthorize("hasRole('PROVIDER')")
public class ProviderWorkspaceController {
    private final DiscoverVerificationService verificationService;

    public ProviderWorkspaceController(DiscoverVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/me")
    public WorkspaceResponse me(Authentication authentication) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return new WorkspaceResponse(principal.providerAccountId(), loadApplications(principal.providerAccountId()));
    }

    @GetMapping("/applications")
    public List<WorkspaceApplicationResponse> applications(Authentication authentication) {
        return loadApplications(requirePrincipal(authentication).providerAccountId());
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
