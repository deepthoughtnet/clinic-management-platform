package com.deepthoughtnet.clinic.api.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfilePublicationRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class DiscoverPresenceControllerTest {
    @Mock
    private ClinicProfileService clinicProfileService;
    @Mock
    private DoctorProfileService doctorProfileService;
    @Mock
    private TenantUserManagementService tenantUserManagementService;
    @Mock
    private ProviderPublicProfileModerationService moderationService;
    @Mock
    private ProviderPublicProfileDraftService draftService;
    @Mock
    private ProviderOwnershipService providerOwnershipService;
    @Mock
    private ProviderLinkingService providerLinkingService;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.clear();
    }

    @Test
    void clinicPresenceUsesLatestOwnershipProjectionInsteadOfReportingUnclaimed() {
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        UUID providerAccountId = UUID.fromString("e9ca829d-69ee-4d1c-8403-4f04a03a6d30");
        ClinicProfileRecord clinic = clinic();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.fromString("99999999-9999-9999-9999-999999999999"), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-1"));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(clinic));
        when(moderationService.findCurrentPublication(tenantId.toString())).thenReturn(Optional.empty());
        when(providerOwnershipService.findLatestOwnership(tenantId.toString())).thenReturn(Optional.of(ownership(tenantId, providerAccountId)));
        when(providerOwnershipService.findActiveClaimIntent(tenantId.toString())).thenReturn(Optional.of(claimIntent(tenantId, providerAccountId, "ebb7fb57-71a3-4dc6-bb4a-079164cafdf9")));
        when(providerOwnershipService.maskedProviderMobile(providerAccountId)).thenReturn(Optional.of("******2201"));
        when(providerOwnershipService.presenceAllowedActions(any(), any())).thenReturn(List.of("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP"));
        when(providerLinkingService.resolveBookingTarget(any(PublicProviderReference.class))).thenReturn(Optional.empty());

        DiscoverPresenceController controller = new DiscoverPresenceController(
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                moderationService,
                draftService,
                providerOwnershipService,
                providerLinkingService
        );

        DiscoverPresenceController.ClinicDiscoverPresenceResponse response = controller.clinicPresence();

        assertThat(response.ownershipStatus()).isEqualTo("CLAIM_PENDING");
        assertThat(response.publicDiscoveryConsent()).isEqualTo("DISABLED");
        assertThat(response.publicProfileStatus()).isEqualTo("UNPUBLISHED");
        assertThat(response.platformConnectionStatus()).isEqualTo("NOT_CONNECTED");
        assertThat(response.bookingCapability()).isEqualTo("NOT_AVAILABLE");
        assertThat(response.maskedProviderMobile()).isEqualTo("******2201");
        assertThat(response.connectionReference()).isEqualTo("ebb7fb57-71a3-4dc6-bb4a-079164cafdf9");
        assertThat(response.ownershipUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-08-03T03:59:50.335138Z"));
        assertThat(response.lastPublishedAt()).isNull();
        assertThat(response.publicProfileSynchronizedAt()).isNull();
        assertThat(response.allowedActions()).contains("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP");
    }

    @Test
    void healthcarePresenceReturnsExistingConnectionReference() {
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        UUID providerAccountId = UUID.fromString("e9ca829d-69ee-4d1c-8403-4f04a03a6d30");
        ClinicProfileRecord clinic = clinic();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.fromString("99999999-9999-9999-9999-999999999999"), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-1"));
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(Optional.of(clinic));
        when(moderationService.findCurrentPublication(tenantId.toString())).thenReturn(Optional.of(new PublicProfilePublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "publication-1",
                tenantId.toString(),
                "submission-1",
                20,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Published",
                OffsetDateTime.parse("2026-08-03T03:29:00Z"),
                null,
                true,
                "VISIBLE",
                "Published"
        )));
        when(providerOwnershipService.findLatestOwnership(tenantId.toString())).thenReturn(Optional.of(verifiedOwnership(tenantId, providerAccountId)));
        when(providerOwnershipService.findActiveClaimIntent(tenantId.toString())).thenReturn(Optional.of(claimIntent(tenantId, providerAccountId, "ebb7fb57-71a3-4dc6-bb4a-079164cafdf9")));
        when(providerOwnershipService.maskedProviderMobile(providerAccountId)).thenReturn(Optional.of("******2201"));
        when(providerOwnershipService.presenceAllowedActions(any(), any())).thenReturn(List.of("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP"));
        when(providerLinkingService.resolveBookingTarget(any(PublicProviderReference.class))).thenReturn(Optional.empty());

        DiscoverPresenceController controller = new DiscoverPresenceController(
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                moderationService,
                draftService,
                providerOwnershipService,
                providerLinkingService
        );

        DiscoverPresenceController.ClinicDiscoverPresenceResponse response = controller.clinicPresence();

        assertThat(response.ownershipStatus()).isEqualTo("VERIFIED");
        assertThat(response.connectionReference()).isEqualTo("ebb7fb57-71a3-4dc6-bb4a-079164cafdf9");
        assertThat(response.ownershipUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-08-03T03:59:50.335138Z"));
        assertThat(response.lastPublishedAt()).isEqualTo(OffsetDateTime.parse("2026-08-03T03:29:00Z"));
        assertThat(response.publicProfileSynchronizedAt()).isEqualTo(OffsetDateTime.parse("2026-08-03T03:29:00Z"));
        assertThat(response.allowedActions()).contains("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP");
    }

    private ClinicProfileRecord clinic() {
        return new ClinicProfileRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78"),
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

    private OwnershipRecord ownership(UUID tenantId, UUID providerAccountId) {
        return new OwnershipRecord(
                UUID.fromString("898d8b7c-199e-491b-b508-1fd9849c1e7c"),
                tenantId.toString(),
                PublicProfileType.CLINIC,
                providerAccountId,
                PublicProfileOwnershipStatus.CLAIM_PENDING,
                "HEALTHCARE_INITIATED_CONNECTION",
                tenantId.toString(),
                0L,
                null,
                null,
                null,
                null,
                false,
                "Healthcare initiated connection",
                "{}",
                OffsetDateTime.parse("2026-08-03T03:59:50.331079Z"),
                OffsetDateTime.parse("2026-08-03T03:59:50.335138Z")
        );
    }

    private ClaimIntentRecord claimIntent(UUID tenantId, UUID providerAccountId, String connectionReference) {
        return new ClaimIntentRecord(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                connectionReference,
                PublicProfileType.CLINIC,
                tenantId.toString(),
                tenantId.toString(),
                providerAccountId,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
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

    private OwnershipRecord verifiedOwnership(UUID tenantId, UUID providerAccountId) {
        return new OwnershipRecord(
                UUID.fromString("c898d8b7-199e-491b-b508-1fd9849c1e7c"),
                tenantId.toString(),
                PublicProfileType.CLINIC,
                providerAccountId,
                PublicProfileOwnershipStatus.VERIFIED,
                "HEALTHCARE_INITIATED_CONNECTION",
                tenantId.toString(),
                0L,
                OffsetDateTime.parse("2026-08-03T03:59:50.335138Z"),
                null,
                null,
                null,
                true,
                "Healthcare initiated connection",
                "{}",
                OffsetDateTime.parse("2026-08-03T03:59:50.331079Z"),
                OffsetDateTime.parse("2026-08-03T03:59:50.335138Z")
        );
    }
}
