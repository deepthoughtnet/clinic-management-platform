package com.deepthoughtnet.clinic.api.discover.provider.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderSessionPrincipal;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.DisputeRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class ProviderClaimsControllerTest {

    @Test
    void providerClaimDetailsReturnsVerifiedLifecycleAfterApproval() {
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderClaimsController controller = new ProviderClaimsController(
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                providerLinkingService
        );

        UUID providerAccountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        String connectionReference = "ebb7fb57-71a3-4dc6-bb4a-079164cafdf9";
        Authentication authentication = authentication(providerAccountId, actorId);
        ClinicProfileRecord clinic = clinic(tenantId);

        ClaimIntentRecord intent = claimIntent(connectionReference, tenantId, providerAccountId);
        OwnershipRecord ownership = ownership(tenantId.toString(), providerAccountId, PublicProfileOwnershipStatus.VERIFIED, OffsetDateTime.parse("2026-08-03T03:59:50.335138Z"));

        when(providerOwnershipService.authenticateClaimIntent(connectionReference, providerAccountId)).thenReturn(intent);
        when(providerOwnershipService.findLatestOwnership(tenantId.toString())).thenReturn(Optional.of(ownership));
        when(providerOwnershipService.listMemberships(tenantId.toString())).thenReturn(List.of(membership(tenantId.toString(), providerAccountId)));
        when(providerOwnershipService.listDisputes(tenantId.toString())).thenReturn(List.of());
        when(providerOwnershipService.maskedProviderMobile(providerAccountId)).thenReturn(Optional.of("******2201"));
        when(providerOwnershipService.claimPageMode(intent, ownership)).thenReturn("OWNERSHIP_VERIFIED");
        when(providerOwnershipService.claimWorkItemStatus(intent, ownership)).thenReturn("OWNERSHIP_VERIFIED");
        when(providerOwnershipService.claimReviewStatus(intent, ownership)).thenReturn("APPROVED");
        when(providerOwnershipService.claimReviewAllowedActions(intent, ownership)).thenReturn(List.of("BACK_TO_DASHBOARD", "VIEW_OWNERSHIP"));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(clinic));
        when(publicProfileService.findLifecycleByProviderId(clinic.id())).thenReturn(Optional.of(lifecycle(clinic.id())));
        when(providerLinkingService.resolveBookingTarget(any(PublicProviderReference.class))).thenReturn(Optional.empty());

        ProviderClaimsController.ProviderClaimReviewResponse response = controller.review(authentication, connectionReference);

        assertThat(response.pageMode()).isEqualTo("OWNERSHIP_VERIFIED");
        assertThat(response.workItemStatus()).isEqualTo("OWNERSHIP_VERIFIED");
        assertThat(response.reviewStatus()).isEqualTo("APPROVED");
        assertThat(response.ownershipStatus()).isEqualTo("VERIFIED");
        assertThat(response.tenantConsentStatus()).isEqualTo("DISABLED");
        assertThat(response.publicProfileStatus()).isEqualTo("UNPUBLISHED");
        assertThat(response.platformConnectionStatus()).isEqualTo("NOT_CONNECTED");
        assertThat(response.bookingCapability()).isEqualTo("NOT_AVAILABLE");
        assertThat(response.submittedAt()).isEqualTo(OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"));
        assertThat(response.reviewedAt()).isEqualTo(OffsetDateTime.parse("2026-08-03T03:59:50.335138Z"));
        assertThat(response.ownershipUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-08-03T03:59:50.335138Z"));
        assertThat(response.claimNote()).isEqualTo("Healthcare initiated connection");
    }

    @Test
    void verifiedClaimAllowedActionsExcludeSubmit() {
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderClaimsController controller = new ProviderClaimsController(
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                providerLinkingService
        );

        UUID providerAccountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID actorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        String connectionReference = "ebb7fb57-71a3-4dc6-bb4a-079164cafdf9";
        Authentication authentication = authentication(providerAccountId, actorId);
        ClinicProfileRecord clinic = clinic(tenantId);

        ClaimIntentRecord intent = claimIntent(connectionReference, tenantId, providerAccountId);
        OwnershipRecord ownership = ownership(tenantId.toString(), providerAccountId, PublicProfileOwnershipStatus.VERIFIED, OffsetDateTime.parse("2026-08-03T03:59:50.335138Z"));

        when(providerOwnershipService.authenticateClaimIntent(connectionReference, providerAccountId)).thenReturn(intent);
        when(providerOwnershipService.findLatestOwnership(tenantId.toString())).thenReturn(Optional.of(ownership));
        when(providerOwnershipService.listMemberships(tenantId.toString())).thenReturn(List.of(membership(tenantId.toString(), providerAccountId)));
        when(providerOwnershipService.listDisputes(tenantId.toString())).thenReturn(List.of());
        when(providerOwnershipService.maskedProviderMobile(providerAccountId)).thenReturn(Optional.of("******2201"));
        when(providerOwnershipService.claimPageMode(intent, ownership)).thenReturn("OWNERSHIP_VERIFIED");
        when(providerOwnershipService.claimWorkItemStatus(intent, ownership)).thenReturn("OWNERSHIP_VERIFIED");
        when(providerOwnershipService.claimReviewStatus(intent, ownership)).thenReturn("APPROVED");
        when(providerOwnershipService.claimReviewAllowedActions(intent, ownership)).thenReturn(List.of("BACK_TO_DASHBOARD", "VIEW_OWNERSHIP"));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(clinic));
        when(publicProfileService.findLifecycleByProviderId(clinic.id())).thenReturn(Optional.of(lifecycle(clinic.id())));
        when(providerLinkingService.resolveBookingTarget(any(PublicProviderReference.class))).thenReturn(Optional.empty());

        ProviderClaimsController.ProviderClaimReviewResponse response = controller.review(authentication, connectionReference);

        assertThat(response.allowedActions()).contains("BACK_TO_DASHBOARD", "VIEW_OWNERSHIP");
        assertThat(response.allowedActions()).doesNotContain("SUBMIT_CLAIM");
    }

    private static Authentication authentication(UUID providerAccountId, UUID actorId) {
        ProviderSessionPrincipal principal = new ProviderSessionPrincipal(providerAccountId, actorId, Set.of("PROVIDER"));
        return new UsernamePasswordAuthenticationToken(principal, "N/A", List.of());
    }

    private static ClaimIntentRecord claimIntent(String connectionReference, UUID tenantId, UUID providerAccountId) {
        return new ClaimIntentRecord(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                connectionReference,
                PublicProfileType.CLINIC,
                tenantId.toString(),
                tenantId.toString(),
                providerAccountId,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                0L,
                PublicProfileClaimIntentStatus.CLAIM_SUBMITTED,
                OffsetDateTime.parse("2026-08-03T15:45:17.064035Z"),
                OffsetDateTime.parse("2026-08-03T03:55:45.516061Z"),
                OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                null,
                null,
                "Healthcare initiated connection",
                "{}",
                OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                OffsetDateTime.parse("2026-08-03T03:59:50.327176Z")
        );
    }

    private static OwnershipRecord ownership(String publicProfileReference, UUID providerAccountId, PublicProfileOwnershipStatus status, OffsetDateTime updatedAt) {
        return new OwnershipRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                publicProfileReference,
                PublicProfileType.CLINIC,
                providerAccountId,
                status,
                "HEALTHCARE_INITIATED_CONNECTION",
                publicProfileReference,
                0L,
                status == PublicProfileOwnershipStatus.VERIFIED ? updatedAt : null,
                null,
                null,
                null,
                status == PublicProfileOwnershipStatus.VERIFIED,
                "Healthcare initiated connection",
                "{}",
                OffsetDateTime.parse("2026-08-03T03:59:50.331079Z"),
                updatedAt
        );
    }

    private static MembershipRecord membership(String publicProfileReference, UUID providerAccountId) {
        return new MembershipRecord(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                publicProfileReference,
                providerAccountId,
                PublicProfileMembershipRole.OWNER,
                "ACTIVE",
                0L,
                "Healthcare initiated connection",
                OffsetDateTime.parse("2026-08-03T03:59:50.371285Z"),
                OffsetDateTime.parse("2026-08-03T03:59:50.374363Z")
        );
    }

    private static ClinicProfileRecord clinic(UUID tenantId) {
        return new ClinicProfileRecord(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                tenantId,
                "Green Valley Family Clinic",
                "Green Valley Family Clinic",
                "9876502201",
                null,
                "Wakad",
                null,
                "Pune",
                "Maharashtra",
                "India",
                "411057",
                null,
                null,
                null,
                true,
                false,
                "green-valley-family-clinic",
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                OffsetDateTime.parse("2026-08-03T03:00:00Z")
        );
    }

    private static PublicProfileLifecycleRecord lifecycle(UUID providerId) {
        return new PublicProfileLifecycleRecord(
                providerId,
                ProviderType.CLINIC,
                "DISCOVER_PROVIDER",
                providerId.toString(),
                0L,
                OffsetDateTime.parse("2026-08-03T03:00:00Z"),
                "green-valley-family-clinic",
                "Green Valley Family Clinic",
                "Pune",
                "Wakad",
                "CALL_TO_BOOK",
                "UNPUBLISHED",
                OffsetDateTime.parse("2026-08-03T03:29:00Z"),
                null,
                0L,
                "/provider/green-valley-family-clinic"
        );
    }
}
