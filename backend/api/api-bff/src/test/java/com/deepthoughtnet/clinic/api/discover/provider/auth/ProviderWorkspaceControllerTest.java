package com.deepthoughtnet.clinic.api.discover.provider.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.ProviderOnboardingAccessResponse;
import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderAuthModels.WorkspaceResponse;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderDashboardRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderOnboardingAccessRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class ProviderWorkspaceControllerTest {

    @Test
    void applicationDashboardLoadsOwnedApplicationByExactReference() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(verificationService, onboardingService);
        UUID providerAccountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Authentication authentication = authentication(providerAccountId);
        ProviderDashboardRecord dashboard = new ProviderDashboardRecord(
                new com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "JDR-2026-725068FC",
                        ProviderType.INDIVIDUAL_DOCTOR,
                        ProviderLifecycleStatus.DRAFT,
                        0L,
                        25,
                        "ACCOUNT",
                        "doctor.a@jeevanam.test",
                        "9876501401",
                        false,
                        true,
                        true,
                        "Doctor A",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        OffsetDateTime.parse("2026-07-30T00:00:00Z"),
                        null,
                        OffsetDateTime.parse("2026-07-30T00:00:00Z"),
                        OffsetDateTime.parse("2026-07-30T00:00:00Z"),
                        null
                ),
                new com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderCompletionRecord(
                        25,
                        List.of(),
                        List.of("Account"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        false,
                        "ACCOUNT",
                        "ACCOUNT",
                        false
                ),
                List.of(),
                List.of(),
                null,
                null,
                false,
                "Complete Account and contact"
        );
        when(onboardingService.dashboardForOwnedApplication("JDR-2026-725068FC", providerAccountId)).thenReturn(dashboard);

        ResponseEntity<ProviderDashboardRecord> response = controller.applicationDashboard(authentication, "JDR-2026-725068FC");

        verify(onboardingService).dashboardForOwnedApplication("JDR-2026-725068FC", providerAccountId);
        assertThat(response.getBody()).isSameAs(dashboard);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().getHeaderValue());
    }

    @Test
    void onboardingAccessIssuesFreshTokenForExactOwnedApplication() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(verificationService, onboardingService);
        UUID providerAccountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID applicationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Authentication authentication = authentication(providerAccountId);
        ProviderOnboardingAccessRecord access = new ProviderOnboardingAccessRecord(applicationId, "new-onboarding-token");
        when(onboardingService.issueOnboardingAccess("JDR-2026-725068FC", providerAccountId)).thenReturn(access);

        ResponseEntity<ProviderOnboardingAccessResponse> response = controller.onboardingAccess(authentication, "JDR-2026-725068FC");

        verify(onboardingService).issueOnboardingAccess("JDR-2026-725068FC", providerAccountId);
        assertThat(response.getBody()).isEqualTo(new ProviderOnboardingAccessResponse(applicationId, "new-onboarding-token"));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().getHeaderValue());
    }

    @Test
    void discardDelegatesToOwnedWorkspaceDiscardFlow() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(verificationService, onboardingService);
        UUID providerAccountId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        Authentication authentication = authentication(providerAccountId);
        when(onboardingService.discardOwnedApplication("JCL-2026-AA66F5CD", providerAccountId, "no longer needed")).thenReturn(null);

        ResponseEntity<ProviderApplicationRecord> response = controller.discard(
                authentication,
                "JCL-2026-AA66F5CD",
                new ProviderWorkspaceController.DiscardRequest("no longer needed")
        );

        verify(onboardingService).discardOwnedApplication("JCL-2026-AA66F5CD", providerAccountId, "no longer needed");
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().getHeaderValue());
    }

    @Test
    void meLoadsAuthenticatedPhoneSessionWithoutEmailRequirement() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(verificationService, onboardingService);
        UUID providerAccountId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        Authentication authentication = authentication(providerAccountId);
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876501402");
        account.markPhoneVerified();
        when(verificationService.findAccountById(providerAccountId)).thenReturn(java.util.Optional.of(account));
        when(verificationService.findOwnedApplicationSummaries(providerAccountId)).thenReturn(List.of());

        ResponseEntity<WorkspaceResponse> response = controller.me(authentication);

        verify(verificationService).findAccountById(providerAccountId);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contactEmail()).isNull();
        assertThat(response.getBody().contactPhone()).isEqualTo("9876501402");
        assertThat(response.getBody().phoneVerifiedAt()).isEqualTo(account.getPhoneVerifiedAt());
        assertThat(response.getBody().applications()).isEmpty();
    }

    private static Authentication authentication(UUID providerAccountId) {
        ProviderSessionPrincipal principal = new ProviderSessionPrincipal(
                providerAccountId,
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                Set.of("ROLE_PROVIDER")
        );
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
