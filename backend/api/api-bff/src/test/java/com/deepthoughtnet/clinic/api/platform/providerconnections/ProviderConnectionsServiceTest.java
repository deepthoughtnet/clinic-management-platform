package com.deepthoughtnet.clinic.api.platform.providerconnections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicationReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventQueryService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.port.DiscoverCatalogPort;
import com.deepthoughtnet.clinic.platform.providerintegration.db.ProviderConnectionSuggestionRejectionRepository;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderConnectionsServiceTest {

    @Mock
    private DiscoverCatalogPort discoverCatalogPort;
    @Mock
    private LocalHealthcareProviderFactsAdapter healthcareFactsAdapter;
    @Mock
    private ProviderLinkingService providerLinkingService;
    @Mock
    private ProviderPublicProfileService publicProfileService;
    @Mock
    private ProviderPublicProfileModerationService moderationService;
    @Mock
    private ProviderPublicProfileDraftService draftService;
    @Mock
    private PlatformTenantManagementService tenantManagementService;
    @Mock
    private ClinicProfileService clinicProfileService;
    @Mock
    private DoctorProfileService doctorProfileService;
    @Mock
    private TenantUserManagementService tenantUserManagementService;
    @Mock
    private ProviderOwnershipService providerOwnershipService;
    @Mock
    private AuditEventQueryService auditEventQueryService;
    @Mock
    private ProviderConnectionSuggestionRejectionRepository suggestionRejectionRepository;

    private ProviderConnectionsService service;

    @BeforeEach
    void setUp() {
        service = new ProviderConnectionsService(
                discoverCatalogPort,
                healthcareFactsAdapter,
                providerLinkingService,
                publicProfileService,
                moderationService,
                draftService,
                tenantManagementService,
                clinicProfileService,
                doctorProfileService,
                tenantUserManagementService,
                providerOwnershipService,
                auditEventQueryService,
                suggestionRejectionRepository,
                new ObjectMapper()
        );
    }

    @Test
    void publicProfileLifecycleReturnsDiscoverReadinessMetadata() {
        UUID providerId = UUID.fromString("8e5a6d56-08f8-47f1-99f4-f79b22aaef48");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-02T10:15:30Z");
        PublicProfileLifecycleRecord record = new PublicProfileLifecycleRecord(
                providerId,
                ProviderType.CLINIC,
                SourceSystem.HEALTHCARE_CLINIC.name(),
                "clinic-source-123",
                42L,
                updatedAt,
                "jeevanam-family-clinic-local",
                "Jeevanam Family Clinic Local",
                "Pune",
                "Hinjewadi",
                "CALL_TO_BOOK",
                "DRAFT",
                updatedAt,
                updatedAt,
                3L,
                "/discover/clinics/jeevanam-family-clinic-local"
        );

        when(publicProfileService.listLifecycleProfiles(eq(ProviderType.CLINIC), anyString(), anyString())).thenReturn(List.of(record));
        when(publicProfileService.publicationReadiness(providerId)).thenReturn(new PublicationReadinessRecord(
                false,
                88,
                List.of("PUBLIC_CONTACT"),
                List.of(),
                List.of("SLUG_PENDING"),
                "DRAFT",
                42L,
                updatedAt
        ));

        List<ProviderConnectionsLifecycleResponse> result = service.publicProfileLifecycle(PublicProfileType.CLINIC, "Family", "Pune");

        assertThat(result).hasSize(1);
        ProviderConnectionsLifecycleResponse row = result.get(0);
        assertThat(row.publicProfileType()).isEqualTo(PublicProfileType.CLINIC);
        assertThat(row.sourceSystem()).isEqualTo(SourceSystem.HEALTHCARE_CLINIC.name());
        assertThat(row.sourceEntityReference()).isEqualTo("clinic-source-123");
        assertThat(row.displayName()).isEqualTo("Jeevanam Family Clinic Local");
        assertThat(row.canonicalSlug()).isEqualTo("jeevanam-family-clinic-local");
        assertThat(row.publicPath()).isEqualTo("/discover/clinics/jeevanam-family-clinic-local");
        assertThat(row.publicationStatus()).isEqualTo("DRAFT");
        assertThat(row.ready()).isFalse();
        assertThat(row.missingFields()).contains("PUBLIC_CONTACT");
        assertThat(row.warnings()).contains("SLUG_PENDING");
        assertThat(row.connectionRevision()).isEqualTo(3L);
    }

    @Test
    void auditEventsReturnsGlobalProviderConnectionHistory() {
        UUID eventId = UUID.fromString("f2e0f0ef-1ef8-4cf7-b1c6-0e2c4f0f6c51");
        UUID actorId = UUID.fromString("18b7f39c-7dfc-4f1f-9f93-3d7b2f0d2f3b");
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-02T10:20:30Z");
        when(auditEventQueryService.listRecentForEntityTypes(eq(List.of("PUBLIC_CLINIC_PLATFORM_LINK", "PUBLIC_DOCTOR_PRACTICE_PLATFORM_LINK")), eq(50))).thenReturn(List.of(
                new AuditEventRecord(
                        eventId,
                        UUID.fromString("a8a1d1aa-6f8b-4f53-9e7c-8d3f6b0d1234"),
                        "PUBLIC_CLINIC_PLATFORM_LINK",
                        UUID.fromString("0e43a3d0-4f07-4d5b-995c-4dbbc9d7c8d1"),
                        "LINKED",
                        actorId,
                        occurredAt,
                        "Clinic link activated",
                        "{\"providerType\":\"Clinic\",\"tenantReference\":\"tenant-1\",\"platformClinicReference\":\"platform-1\",\"previousState\":\"PROPOSED\",\"newState\":\"LINKED\",\"result\":\"OK\",\"correlationId\":\"corr-123\"}"
                )
        ));

        List<ProviderConnectionsAuditResponse> result = service.auditEvents(null, null, null, null, null);

        assertThat(result).hasSize(1);
        ProviderConnectionsAuditResponse row = result.get(0);
        assertThat(row.id()).isEqualTo(eventId);
        assertThat(row.actorAppUserId()).isEqualTo(actorId);
        assertThat(row.providerType()).isEqualTo("Clinic");
        assertThat(row.tenantReference()).isEqualTo("tenant-1");
        assertThat(row.platformClinicReference()).isEqualTo("platform-1");
        assertThat(row.previousState()).isEqualTo("PROPOSED");
        assertThat(row.newState()).isEqualTo("LINKED");
        assertThat(row.result()).isEqualTo("OK");
        assertThat(row.correlationId()).isEqualTo("corr-123");
        assertThat(row.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void ownershipsFallsBackToHealthcareDisplayNameWhenPublicProfileMissing() {
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        UUID providerAccountId = UUID.fromString("e9ca829d-69ee-4d1c-8403-4f04a03a6d30");
        when(providerLinkingService.listClinicLinks()).thenReturn(List.of());
        when(providerLinkingService.listDoctorPracticeLinks()).thenReturn(List.of());
        when(providerOwnershipService.ownershipAllowedActions(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of("APPROVE_OWNERSHIP", "REJECT_OWNERSHIP", "DISPUTE_OWNERSHIP", "REVOKE_CLAIM", "VIEW_OWNERSHIP"));
        when(providerOwnershipService.listOwnerships()).thenReturn(List.of(new OwnershipRecord(
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
        )));
        when(providerOwnershipService.listMemberships(tenantId.toString())).thenReturn(List.of());
        when(providerOwnershipService.listDisputes(tenantId.toString())).thenReturn(List.of());
        when(discoverCatalogPort.searchPublishedProviders(null, null, PublicProfileType.CLINIC)).thenReturn(List.of());
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(java.util.Optional.of(new ClinicProfileRecord(
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

        List<ProviderConnectionsOwnershipResponse> ownerships = service.ownerships();

        assertThat(ownerships).hasSize(1);
        assertThat(ownerships.get(0).displayName()).isEqualTo("Green Valley Family Clinic");
        assertThat(ownerships.get(0).ownershipStatus()).isEqualTo("CLAIM_PENDING");
        assertThat(ownerships.get(0).platformConnectionStatus()).isEqualTo("NOT_CONNECTED");
        assertThat(ownerships.get(0).allowedActions()).containsExactly("APPROVE_OWNERSHIP", "REJECT_OWNERSHIP", "DISPUTE_OWNERSHIP", "REVOKE_CLAIM", "VIEW_OWNERSHIP");
    }

    @Test
    void verifiedOwnershipExposesOnlyPostApprovalActions() {
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        UUID providerAccountId = UUID.fromString("e9ca829d-69ee-4d1c-8403-4f04a03a6d30");
        when(providerLinkingService.listClinicLinks()).thenReturn(List.of());
        when(providerLinkingService.listDoctorPracticeLinks()).thenReturn(List.of());
        when(providerOwnershipService.ownershipAllowedActions(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(List.of("VIEW_OWNERSHIP", "DISPUTE_OWNERSHIP", "REVOKE_OWNERSHIP"));
        when(providerOwnershipService.listOwnerships()).thenReturn(List.of(new OwnershipRecord(
                UUID.fromString("898d8b7c-199e-491b-b508-1fd9849c1e7c"),
                tenantId.toString(),
                PublicProfileType.CLINIC,
                providerAccountId,
                PublicProfileOwnershipStatus.VERIFIED,
                "HEALTHCARE_INITIATED_CONNECTION",
                tenantId.toString(),
                0L,
                OffsetDateTime.parse("2026-08-03T05:40:23.787793Z"),
                null,
                null,
                null,
                true,
                "Approved from console",
                "{}",
                OffsetDateTime.parse("2026-08-03T03:59:50.331079Z"),
                OffsetDateTime.parse("2026-08-03T05:40:23.787793Z")
        )));
        when(providerOwnershipService.listMemberships(tenantId.toString())).thenReturn(List.of());
        when(providerOwnershipService.listDisputes(tenantId.toString())).thenReturn(List.of());
        when(discoverCatalogPort.searchPublishedProviders(null, null, PublicProfileType.CLINIC)).thenReturn(List.of());
        when(clinicProfileService.findByTenantId(tenantId)).thenReturn(java.util.Optional.of(new ClinicProfileRecord(
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

        List<ProviderConnectionsOwnershipResponse> ownerships = service.ownerships();

        assertThat(ownerships).hasSize(1);
        assertThat(ownerships.get(0).allowedActions()).containsExactly("VIEW_OWNERSHIP", "DISPUTE_OWNERSHIP", "REVOKE_OWNERSHIP");
    }
}
