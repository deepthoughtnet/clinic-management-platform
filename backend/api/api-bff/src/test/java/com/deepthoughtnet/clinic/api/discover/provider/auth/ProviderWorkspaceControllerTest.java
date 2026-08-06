package com.deepthoughtnet.clinic.api.discover.provider.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationSubmissionRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfilePublicationRecord;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
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
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
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
    void workspaceIncludesSubmittedOwnershipClaimAfterLogin() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
        UUID providerAccountId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        UUID appUserId = UUID.fromString("e9ca829d-69ee-4d1c-8403-4f04a03a6d30");
        Authentication authentication = authentication(providerAccountId);
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876502201");
        account.markPhoneVerified();
        when(verificationService.findAccountById(providerAccountId)).thenReturn(java.util.Optional.of(account));
        when(verificationService.findOwnedApplicationSummaries(providerAccountId)).thenReturn(List.of());
        when(providerOwnershipService.listClaimIntents(providerAccountId)).thenReturn(List.of(
                new ClaimIntentRecord(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "ebb7fb57-71a3-4dc6-bb4a-079164cafdf9",
                        PublicProfileType.CLINIC,
                        tenantId.toString(),
                        tenantId.toString(),
                        appUserId,
                        appUserId,
                        0L,
                        PublicProfileClaimIntentStatus.CLAIM_SUBMITTED,
                        OffsetDateTime.parse("2026-08-03T15:45:17.064035Z"),
                        null,
                        OffsetDateTime.parse("2026-08-03T03:55:45.516061Z"),
                        OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                        OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                        null,
                        null,
                        "Healthcare initiated connection",
                        "{}",
                        OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                        OffsetDateTime.parse("2026-08-03T03:59:50.327176Z")
                )
        ));
        when(providerOwnershipService.listOwnerships()).thenReturn(List.of(
                OwnershipRecordFromTest.claimPending(
                        tenantId.toString(),
                        PublicProfileType.CLINIC,
                        providerAccountId,
                        tenantId.toString(),
                        0L,
                        "Healthcare initiated connection",
                        OffsetDateTime.parse("2026-08-03T03:59:50.335138Z")
                )
        ));
        when(providerOwnershipService.claimWorkItemStatus(any(), any())).thenReturn("PLATFORM_REVIEW");
        when(providerOwnershipService.claimReviewStatus(any(), any())).thenReturn("PENDING_REVIEW");
        when(providerOwnershipService.workspaceAllowedActions(any(), any())).thenReturn(List.of("OPEN_CLAIM"));
        when(providerOwnershipService.listMemberships(tenantId.toString())).thenReturn(List.of(
                new MembershipRecord(
                        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                        tenantId.toString(),
                        providerAccountId,
                        com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole.OWNER,
                        "ACTIVE",
                        0L,
                        "Healthcare initiated connection",
                        OffsetDateTime.parse("2026-08-03T03:59:50.371285Z"),
                        OffsetDateTime.parse("2026-08-03T03:59:50.374363Z")
                )
        ));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(java.util.Optional.of(new com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
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
        )));
        when(providerLinkingService.resolveBookingTarget(org.mockito.ArgumentMatchers.any(PublicProviderReference.class))).thenReturn(java.util.Optional.empty());
        when(publicProfileService.findLifecycleByProviderId(tenantId)).thenReturn(java.util.Optional.empty());

        ResponseEntity<WorkspaceResponse> response = controller.me(authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().workItems()).hasSize(1);
        assertThat(response.getBody().workItems().get(0).displayName()).isEqualTo("Green Valley Family Clinic");
        assertThat(response.getBody().workItems().get(0).ownershipStatus()).isEqualTo("CLAIM_PENDING");
        assertThat(response.getBody().workItems().get(0).claimStatus()).isEqualTo("CLAIM_SUBMITTED");
        assertThat(response.getBody().workItems().get(0).reviewStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(response.getBody().workItems().get(0).workItemStatus()).isEqualTo("PLATFORM_REVIEW");
        assertThat(response.getBody().workItems().get(0).publicDiscoveryConsent()).isEqualTo("DISABLED");
        assertThat(response.getBody().workItems().get(0).membershipRole()).isEqualTo("OWNER:ACTIVE");
        assertThat(response.getBody().attentionCount()).isEqualTo(1);
    }

    @Test
    void workspaceUsesVerifiedOwnershipAsAuthoritativeLifecycleState() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
        UUID providerAccountId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        Authentication authentication = authentication(providerAccountId);
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876502201");
        account.markPhoneVerified();
        when(verificationService.findAccountById(providerAccountId)).thenReturn(java.util.Optional.of(account));
        when(verificationService.findOwnedApplicationSummaries(providerAccountId)).thenReturn(List.of());
        when(providerOwnershipService.listClaimIntents(providerAccountId)).thenReturn(List.of(
                new ClaimIntentRecord(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "ebb7fb57-71a3-4dc6-bb4a-079164cafdf9",
                        PublicProfileType.CLINIC,
                        tenantId.toString(),
                        tenantId.toString(),
                        providerAccountId,
                        providerAccountId,
                        2L,
                        PublicProfileClaimIntentStatus.CLAIM_SUBMITTED,
                        OffsetDateTime.parse("2026-08-03T15:45:17.064035Z"),
                        null,
                        OffsetDateTime.parse("2026-08-03T03:55:45.516061Z"),
                        OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                        null,
                        null,
                        null,
                        "Healthcare initiated connection",
                        "{}",
                        OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                        OffsetDateTime.parse("2026-08-03T04:05:00Z")
                )
        ));
        when(providerOwnershipService.listOwnerships()).thenReturn(List.of(
                new OwnershipRecord(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        tenantId.toString(),
                        PublicProfileType.CLINIC,
                        providerAccountId,
                        PublicProfileOwnershipStatus.VERIFIED,
                        "HEALTHCARE_INITIATED_CONNECTION",
                        tenantId.toString(),
                        2L,
                        OffsetDateTime.parse("2026-08-03T04:05:00Z"),
                        null,
                        null,
                        null,
                        true,
                        "Platform approval",
                        "{}",
                        OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                        OffsetDateTime.parse("2026-08-03T04:05:00Z")
                )
        ));
        when(providerOwnershipService.claimWorkItemStatus(any(), any())).thenReturn("OWNERSHIP_VERIFIED");
        when(providerOwnershipService.claimReviewStatus(any(), any())).thenReturn("APPROVED");
        when(providerOwnershipService.workspaceAllowedActions(any(), any())).thenReturn(List.of("VIEW_DETAILS"));
        when(providerOwnershipService.listMemberships(tenantId.toString())).thenReturn(List.of());
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(java.util.Optional.of(new com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
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
                OffsetDateTime.parse("2026-08-03T04:05:00Z")
        )));
        when(providerLinkingService.resolveBookingTarget(org.mockito.ArgumentMatchers.any(PublicProviderReference.class))).thenReturn(java.util.Optional.empty());
        when(publicProfileService.findLifecycleByProviderId(tenantId)).thenReturn(java.util.Optional.empty());

        ResponseEntity<WorkspaceResponse> response = controller.me(authentication);

        var item = response.getBody().workItems().get(0);
        assertThat(item.workItemStatus()).isEqualTo("OWNERSHIP_VERIFIED");
        assertThat(item.reviewStatus()).isEqualTo("APPROVED");
        assertThat(item.allowedActions()).containsExactly("VIEW_DETAILS");
        assertThat(item.publicDiscoveryConsent()).isEqualTo("DISABLED");
        assertThat(response.getBody().attentionCount()).isEqualTo(1);
    }

    @Test
    void workspaceUsesCanonicalProviderProfileProjectionForMetrics() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
        UUID providerAccountId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        Authentication authentication = authentication(providerAccountId);
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876502201");
        account.markPhoneVerified();
        when(verificationService.findAccountById(providerAccountId)).thenReturn(java.util.Optional.of(account));
        when(verificationService.findOwnedApplicationSummaries(providerAccountId)).thenReturn(List.of());
        when(draftService.listDraftLifecycle()).thenReturn(List.of(readyDraft(providerAccountId, tenantId)));
        when(moderationService.findSubmission(tenantId.toString())).thenReturn(java.util.Optional.empty());
        when(moderationService.findCurrentPublication(tenantId.toString())).thenReturn(java.util.Optional.empty());
        when(providerLinkingService.resolveBookingTarget(org.mockito.ArgumentMatchers.any(PublicProviderReference.class))).thenReturn(java.util.Optional.empty());
        when(publicProfileService.findLifecycleByProviderId(tenantId)).thenReturn(java.util.Optional.empty());

        ResponseEntity<WorkspaceResponse> response = controller.me(authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().profiles()).hasSize(1);
        assertThat(response.getBody().activeProfileCount()).isEqualTo(1);
        assertThat(response.getBody().readyForReviewCount()).isEqualTo(1);
        assertThat(response.getBody().underReviewCount()).isEqualTo(0);
        assertThat(response.getBody().publishedCount()).isEqualTo(0);
        assertThat(response.getBody().needsAttentionCount()).isEqualTo(1);
        assertThat(response.getBody().profiles().get(0).primaryAction()).isEqualTo("SUBMIT_FOR_REVIEW");
        assertThat(response.getBody().profiles().get(0).allowedActions()).contains("SUBMIT_FOR_REVIEW", "VIEW_PREVIEW", "VIEW_READINESS", "EDIT_PUBLIC_PROFILE");
        assertThat(response.getBody().profiles().get(0).providerActionRequired()).isTrue();
        assertThat(response.getBody().profiles().get(0).lifecycleLabel()).isEqualTo("Ready for Platform Review");
        assertThat(response.getBody().profiles().get(0).nextActionLabel()).isEqualTo("Submit profile for review");
    }

    @Test
    void workspaceCountsCurrentPublicationAsPublishedAndNotUnderReview() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService, onboardingService, providerOwnershipService, clinicProfileService,
                doctorProfileService, tenantUserManagementService, publicProfileService, draftService,
                moderationService, providerLinkingService
        );
        UUID providerAccountId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID profileId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876502201");
        account.markPhoneVerified();
        PublicProfilePublicationRecord publication = new PublicProfilePublicationRecord(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-20", profileId.toString(), "a01bc24b-3c5c-4038-8feb-02dd6f8de43a", 20, "PUBLISHED",
                "green-valley-family-clinic", "/discover/clinics/green-valley-family-clinic", "Published",
                OffsetDateTime.parse("2026-08-06T11:57:41Z"), null, true, "VISIBLE", "Published profile is publicly visible."
        );
        when(verificationService.findAccountById(providerAccountId)).thenReturn(java.util.Optional.of(account));
        when(verificationService.findOwnedApplicationSummaries(providerAccountId)).thenReturn(List.of());
        when(draftService.listDraftLifecycle()).thenReturn(List.of(readyDraft(providerAccountId, profileId)));
        when(moderationService.findSubmission(profileId.toString())).thenReturn(java.util.Optional.empty());
        when(moderationService.findCurrentPublication(profileId.toString())).thenReturn(java.util.Optional.of(publication));
        when(providerLinkingService.resolveBookingTarget(org.mockito.ArgumentMatchers.any(PublicProviderReference.class)))
                .thenReturn(java.util.Optional.empty());

        WorkspaceResponse workspace = controller.me(authentication(providerAccountId)).getBody();

        assertThat(workspace).isNotNull();
        assertThat(workspace.publishedCount()).isEqualTo(1);
        assertThat(workspace.underReviewCount()).isEqualTo(0);
        assertThat(workspace.profiles()).singleElement().satisfies(profile -> {
            assertThat(profile.publicationStatus()).isEqualTo("PUBLISHED");
            assertThat(profile.effectiveVisibility()).isEqualTo("VISIBLE");
            assertThat(profile.lifecycleLabel()).isEqualTo("Published");
            assertThat(profile.primaryAction()).isEqualTo("VIEW_PUBLIC_PROFILE");
        });
    }

    @Test
    void workspaceCountsChangesRequestedProfilesAsActiveModeration() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
        UUID providerAccountId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        Authentication authentication = authentication(providerAccountId);
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876502201");
        account.markPhoneVerified();
        when(verificationService.findAccountById(providerAccountId)).thenReturn(java.util.Optional.of(account));
        when(verificationService.findOwnedApplicationSummaries(providerAccountId)).thenReturn(List.of());
        when(draftService.listDraftLifecycle()).thenReturn(List.of(readyDraft(providerAccountId, tenantId)));
        when(moderationService.findSubmission(tenantId.toString())).thenReturn(java.util.Optional.of(changesRequestedSubmissionRecord(tenantId.toString())));
        when(moderationService.findCurrentPublication(tenantId.toString())).thenReturn(java.util.Optional.empty());
        when(providerLinkingService.resolveBookingTarget(org.mockito.ArgumentMatchers.any(PublicProviderReference.class))).thenReturn(java.util.Optional.empty());
        when(publicProfileService.findLifecycleByProviderId(tenantId)).thenReturn(java.util.Optional.empty());

        ResponseEntity<WorkspaceResponse> response = controller.me(authentication);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().readyForReviewCount()).isEqualTo(0);
        assertThat(response.getBody().underReviewCount()).isEqualTo(1);
        assertThat(response.getBody().profiles().get(0).moderationStatus()).isEqualTo("CHANGES_REQUESTED");
        assertThat(response.getBody().profiles().get(0).primaryAction()).isEqualTo("REVIEW_CHANGES");
        assertThat(response.getBody().profiles().get(0).allowedActions()).contains("REVIEW_CHANGES", "EDIT_PUBLIC_PROFILE", "VIEW_PREVIEW", "VIEW_READINESS");
    }

    @Test
    void onboardingAccessIssuesFreshTokenForExactOwnedApplication() {
        DiscoverVerificationService verificationService = Mockito.mock(DiscoverVerificationService.class);
        ProviderOnboardingService onboardingService = Mockito.mock(ProviderOnboardingService.class);
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
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
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
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
        ProviderOwnershipService providerOwnershipService = Mockito.mock(ProviderOwnershipService.class);
        ClinicProfileService clinicProfileService = Mockito.mock(ClinicProfileService.class);
        DoctorProfileService doctorProfileService = Mockito.mock(DoctorProfileService.class);
        TenantUserManagementService tenantUserManagementService = Mockito.mock(TenantUserManagementService.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        ProviderPublicProfileDraftService draftService = Mockito.mock(ProviderPublicProfileDraftService.class);
        ProviderPublicProfileModerationService moderationService = Mockito.mock(ProviderPublicProfileModerationService.class);
        ProviderLinkingService providerLinkingService = Mockito.mock(ProviderLinkingService.class);
        ProviderWorkspaceController controller = new ProviderWorkspaceController(
                verificationService,
                onboardingService,
                providerOwnershipService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                publicProfileService,
                draftService,
                moderationService,
                providerLinkingService
        );
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
        assertThat(response.getBody().profiles()).isEmpty();
        assertThat(response.getBody().workItems()).isEmpty();
    }

    private static final class OwnershipRecordFromTest {
        private static com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord claimPending(
                String publicProfileReference,
                PublicProfileType publicProfileType,
                UUID providerAccountId,
                String tenantReference,
                long sourceRevision,
                String reason,
                OffsetDateTime updatedAt
        ) {
            return new com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord(
                    UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    publicProfileReference,
                    publicProfileType,
                    providerAccountId,
                    PublicProfileOwnershipStatus.CLAIM_PENDING,
                    "HEALTHCARE_INITIATED_CONNECTION",
                    tenantReference,
                    sourceRevision,
                    null,
                    null,
                    null,
                    null,
                    false,
                    reason,
                    "{}",
                    OffsetDateTime.parse("2026-08-03T03:57:14.865084Z"),
                    updatedAt
            );
        }
    }

    private static PublicProfileDraftWorkspaceRecord readyDraft(UUID providerAccountId, UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T00:00:00Z");
        return new PublicProfileDraftWorkspaceRecord(
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                "draft-green-valley",
                tenantId.toString(),
                ProviderType.CLINIC,
                providerAccountId,
                "VERIFIED",
                "ENABLED",
                "UNPUBLISHED",
                "READY_FOR_REVIEW",
                "READY",
                100,
                15,
                now,
                now,
                now,
                now,
                "Green Valley Family Clinic",
                "green-valley-family-clinic",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "+91 98765 02201",
                "contact@greenvalleyclinic.in",
                "https://www.greenvalleyclinic.in",
                "+91 98765 02201",
                "PMC/CLINIC/2022/10458",
                2022,
                "HEALTHCARE_CLINIC_PROFILE",
                "HEALTHCARE_CLINIC_PROFILE",
                0L,
                now,
                "/discover/clinics/green-valley-family-clinic",
                List.of("SUBMIT_FOR_REVIEW", "VIEW_PREVIEW", "VIEW_READINESS", "EDIT_PUBLIC_PROFILE"),
                List.of(),
                new PublicProfileDraftReadinessRecord(
                        "READY",
                        true,
                        100,
                        List.of(),
                        List.of("gallery", "establishedYear", "facilities", "languages", "fees", "website", "whatsappNumber", "metaTitle", "metaDescription"),
                        List.of(),
                        List.of(),
                        List.of(),
                        now,
                        15
                ),
                List.of(),
                java.util.Map.of()
        );
    }

    private static PublicProfileModerationSubmissionRecord changesRequestedSubmissionRecord(String publicProfileReference) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-04T00:00:00Z");
        return new PublicProfileModerationSubmissionRecord(
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                "submission-req-changes",
                publicProfileReference,
                ProviderType.CLINIC,
                "draft-green-valley",
                15,
                "CHANGES_REQUESTED",
                "UNPUBLISHED",
                "ENABLED",
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                null,
                now,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "platform.admin@clinic.local",
                "Platform Admin",
                "platform.admin@clinic.local",
                now,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                now,
                "Please revise timings.",
                1L,
                false,
                null,
                null,
                null,
                now,
                now,
                "NOT_PUBLISHED",
                "Profile is not published.",
                null,
                java.util.List.of(),
                java.util.List.of("REVIEW_REQUESTED_CHANGES", "OPEN_EDITABLE_DRAFT", "BACK_TO_WORKSPACE"),
                java.util.List.of("VIEW_SUBMISSION", "VIEW_REVIEW_HISTORY")
        );
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
