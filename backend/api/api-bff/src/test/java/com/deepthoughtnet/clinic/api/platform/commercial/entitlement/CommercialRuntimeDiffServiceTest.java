package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.ComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffTenantResponse;
import com.deepthoughtnet.clinic.api.platform.service.PlatformTenantService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialTenantRuntimeContextService;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialTenantRuntimeContextService.RuntimeContext;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionSummaryResponse;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommercialRuntimeDiffServiceTest {
    private PlatformTenantService platformTenantService;
    private CommercialEffectiveEntitlementApiService entitlementApiService;
    private CommercialTenantRuntimeContextService runtimeContextService;
    private CommercialRuntimeProperties runtimeProperties;
    private CommercialRuntimeDiffApiService service;

    @BeforeEach
    void setUp() {
        platformTenantService = Mockito.mock(PlatformTenantService.class);
        entitlementApiService = Mockito.mock(CommercialEffectiveEntitlementApiService.class);
        runtimeContextService = Mockito.mock(CommercialTenantRuntimeContextService.class);
        runtimeProperties = new CommercialRuntimeProperties();
        service = new CommercialRuntimeDiffApiService(platformTenantService, entitlementApiService, runtimeContextService, runtimeProperties);
    }

    @Test
    void summaryAndTenantProjectionReflectCurrentCommercialState() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID subscriptionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID snapshotId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID overrideId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID actorId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        PlatformTenantRecord tenant = new PlatformTenantRecord(tenantId, "demo-clinic", "Demo Clinic", "solo-clinic", "ACTIVE", true, null, OffsetDateTime.parse("2026-07-25T00:00:00Z"), OffsetDateTime.parse("2026-07-25T00:00:00Z"));
        SubscriptionSummaryResponse activeSubscription = new SubscriptionSummaryResponse(
                subscriptionId,
                tenantId,
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "SOLO_CLINIC",
                "Solo Clinic",
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                2,
                "Version 2",
                com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus.ACTIVE,
                LocalDate.parse("2026-07-01"),
                null,
                true,
                "Demo Clinic Subscription",
                "SUB-001",
                "Commercial subscription for Demo Clinic",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                OffsetDateTime.parse("2026-07-25T00:00:00Z")
        );

        var domainSnapshot = new CommercialEffectiveEntitlementModels.EffectiveEntitlementSnapshotResponse(
                snapshotId,
                tenantId,
                subscriptionId,
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                2,
                "ACTIVE",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new CommercialEffectiveEntitlementModels.OverrideResponse(
                        overrideId,
                        "MODULE",
                        "AI_COPILOT",
                        "ENABLE",
                        null,
                        null,
                        LocalDate.parse("2026-07-25"),
                        null,
                        "ACTIVE",
                        "Pilot access",
                        null,
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                        actorId,
                        OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                        actorId,
                        0L
                )),
                List.of(),
                "source-hash",
                "content-hash",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                actorId.toString(),
                "MANUAL_REGENERATE",
                "CURRENT",
                "VALID",
                List.of()
        );

        EffectiveEntitlementSnapshotResponse snapshot = new EffectiveEntitlementSnapshotResponse(
                snapshotId,
                tenantId,
                subscriptionId,
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                UUID.fromString("77777777-7777-7777-7777-777777777777"),
                2,
                "ACTIVE",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "source-hash",
                "content-hash",
                OffsetDateTime.parse("2026-07-25T00:00:00Z"),
                actorId.toString(),
                "MANUAL_REGENERATE",
                "CURRENT",
                "VALID",
                List.of()
        );

        RuntimeContext context = new RuntimeContext(
                tenantId,
                activeSubscription,
                domainSnapshot,
                "Legacy Runtime — Authoritative",
                1L,
                List.of(),
                "READY",
                List.of(),
                List.of(),
                "Ready for allowlisted commercial runtime pilot",
                "/platform/commercial/runtime-diff"
        );

        var comparison = new LegacyComparisonResponse(
                tenantId,
                List.of(new ComparisonResponse("APPOINTMENTS", "Appointments", "MATCH", "true", "true", "module")),
                List.of(new ComparisonResponse("AI_REASONING", "AI Reasoning", "MATCH", "false", "false", "feature")),
                List.of()
        );

        runtimeProperties.setEnabled(false);
        runtimeProperties.setShadowCompareEnabled(false);

        when(platformTenantService.listTenants()).thenReturn(List.of(tenant));
        when(platformTenantService.getTenant(tenantId)).thenReturn(new PlatformTenantService.PlatformTenantDetail(tenant, null, null, Map.of("APPOINTMENTS", true), 4, 1L));
        when(runtimeContextService.resolve(tenantId)).thenReturn(context);
        when(entitlementApiService.getCurrentSnapshot(tenantId)).thenReturn(snapshot);
        when(entitlementApiService.compareWithLegacy(eq(tenantId), anyMap())).thenReturn(comparison);

        RuntimeDiffSummaryResponse summary = service.summary();
        RuntimeDiffTenantResponse row = service.tenant(tenantId);

        assertThat(summary.tenantsWithActiveCommercialSubscriptions()).isEqualTo(1);
        assertThat(summary.tenantsWithCurrentValidSnapshots()).isEqualTo(1);
        assertThat(summary.exactMatches()).isEqualTo(1);
        assertThat(summary.tenantsWithDifferences()).isEqualTo(0);
        assertThat(summary.activeOverrides()).isEqualTo(1);
        assertThat(summary.commercialRuntimeEnabled()).isFalse();
        assertThat(summary.shadowComparisonEnabled()).isFalse();
        assertThat(summary.allowlistedTenants()).isZero();

        assertThat(row.tenantId()).isEqualTo(tenantId);
        assertThat(row.currentSubscription()).contains("ACTIVE");
        assertThat(row.subscriptionName()).isEqualTo("Demo Clinic Subscription");
        assertThat(row.subscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(row.planTemplateName()).isEqualTo("Solo Clinic");
        assertThat(row.publishedVersion()).isEqualTo("Version 2");
        assertThat(row.snapshotStatus()).isEqualTo("CURRENT");
        assertThat(row.validationState()).isEqualTo("VALID");
        assertThat(row.comparisonStatus()).isEqualTo("MATCH");
        assertThat(row.rolloutReadiness()).isEqualTo("READY");
        assertThat(row.readinessStatus()).isEqualTo("READY");
        assertThat(row.readinessBlockers()).isEmpty();
        assertThat(row.readinessWarnings()).isEmpty();
        assertThat(row.targetRoute()).isEqualTo("/platform/commercial/runtime-diff");
        assertThat(row.runtimeSource()).isEqualTo("Legacy Runtime — Authoritative");
    }
}
