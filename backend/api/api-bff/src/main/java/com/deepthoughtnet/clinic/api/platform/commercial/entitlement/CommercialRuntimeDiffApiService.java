package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffTenantResponse;
import com.deepthoughtnet.clinic.api.platform.service.PlatformTenantService;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialTenantRuntimeContextService;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialTenantRuntimeContextService.RuntimeContext;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CommercialRuntimeDiffApiService {
    private final PlatformTenantService platformTenantService;
    private final CommercialEffectiveEntitlementApiService entitlementApiService;
    private final CommercialTenantRuntimeContextService runtimeContextService;
    private final CommercialRuntimeProperties runtimeProperties;

    public CommercialRuntimeDiffApiService(
            PlatformTenantService platformTenantService,
            CommercialEffectiveEntitlementApiService entitlementApiService,
            CommercialTenantRuntimeContextService runtimeContextService,
            CommercialRuntimeProperties runtimeProperties
    ) {
        this.platformTenantService = platformTenantService;
        this.entitlementApiService = entitlementApiService;
        this.runtimeContextService = runtimeContextService;
        this.runtimeProperties = runtimeProperties;
    }

    public RuntimeDiffSummaryResponse summary() {
        List<RuntimeDiffTenantResponse> tenants = tenants();
        long activeCommercialSubscriptions = tenants.stream().filter(row -> "ACTIVE".equalsIgnoreCase(row.subscriptionStatus())).count();
        long currentValidSnapshots = tenants.stream().filter(row -> "CURRENT".equals(row.snapshotStatus()) && "VALID".equalsIgnoreCase(row.validationState())).count();
        long missingSnapshots = tenants.stream().filter(row -> "MISSING".equals(row.snapshotStatus())).count();
        long invalidSnapshots = tenants.stream().filter(row -> "INVALID".equals(row.snapshotStatus())).count();
        long exactMatches = tenants.stream().filter(row -> "MATCH".equals(row.comparisonStatus())).count();
        long differences = tenants.stream().filter(row -> "DIFFERENT".equals(row.comparisonStatus()) || "LEGACY_ONLY".equals(row.comparisonStatus()) || "COMMERCIAL_ONLY".equals(row.comparisonStatus())).count();
        long legacyOnly = tenants.stream().mapToLong(row -> row.differences().stream().filter(diff -> diff.startsWith("Legacy only")).count()).sum();
        long commercialOnly = tenants.stream().mapToLong(row -> row.differences().stream().filter(diff -> diff.startsWith("Commercial only")).count()).sum();
        long activeOverrides = tenants.stream().mapToLong(RuntimeDiffTenantResponse::activeOverrides).sum();
        long generationFailures = tenants.stream().filter(row -> "INVALID".equals(row.snapshotStatus()) || "PENDING_REGENERATION".equals(row.snapshotStatus())).count();
        long allowlisted = runtimeProperties.getTenantAllowlist() == null ? 0 : runtimeProperties.getTenantAllowlist().size();
        return new RuntimeDiffSummaryResponse(
                activeCommercialSubscriptions,
                currentValidSnapshots,
                missingSnapshots,
                invalidSnapshots,
                exactMatches,
                differences,
                legacyOnly,
                commercialOnly,
                activeOverrides,
                generationFailures,
                runtimeProperties.isEnabled(),
                runtimeProperties.isShadowCompareEnabled(),
                allowlisted
        );
    }

    public List<RuntimeDiffTenantResponse> tenants() {
        return platformTenantService.listTenants().stream()
                .map(this::toRow)
                .sorted(Comparator.comparing(RuntimeDiffTenantResponse::tenantName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public RuntimeDiffTenantResponse tenant(UUID tenantId) {
        return toRow(platformTenantService.getTenant(tenantId).tenant());
    }

    public LegacyComparisonResponse compare(UUID tenantId) {
        PlatformTenantService.PlatformTenantDetail detail = platformTenantService.getTenant(tenantId);
        Map<String, Boolean> legacyModules = detail.modules();
        return entitlementApiService.compareWithLegacy(tenantId, legacyModules);
    }

    private RuntimeDiffTenantResponse toRow(com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord tenant) {
        RuntimeContext context = runtimeContextService.resolve(tenant.id());
        EffectiveEntitlementSnapshotResponse snapshot;
        try {
            snapshot = entitlementApiService.getCurrentSnapshot(tenant.id());
        } catch (Exception ex) {
            snapshot = null;
        }
        LegacyComparisonResponse comparison = null;
        try {
            comparison = compare(tenant.id());
        } catch (Exception ex) {
            comparison = null;
        }

        String snapshotStatus = snapshot == null ? "MISSING" : snapshot.snapshotStatus();
        String validationState = snapshot == null ? "MISSING" : snapshot.validationState();
        String comparisonStatus = deriveComparisonStatus(comparison, snapshotStatus, validationState);
        long moduleDifferences = comparison == null ? 0 : comparison.modules().stream().filter(item -> !"MATCH".equals(item.category())).count();
        long featureDifferences = comparison == null ? 0 : comparison.features().stream().filter(item -> !"MATCH".equals(item.category())).count();
        long limitDifferences = comparison == null ? 0 : comparison.limits().stream().filter(item -> !"MATCH".equals(item.category())).count();
        long activeOverrides = context.activeOverrideCount();
        List<String> differences = new ArrayList<>();
        if (comparison != null) {
            differences.addAll(comparison.modules().stream().filter(item -> !"MATCH".equals(item.category())).map(this::differenceText).toList());
            differences.addAll(comparison.features().stream().filter(item -> !"MATCH".equals(item.category())).map(this::differenceText).toList());
            differences.addAll(comparison.limits().stream().filter(item -> !"MATCH".equals(item.category())).map(this::differenceText).toList());
        }
        ReadinessProjection readiness = readiness(context, snapshot, comparison);
        return new RuntimeDiffTenantResponse(
                tenant.id(),
                tenant.name(),
                tenant.code(),
                describeSubscription(context.activeSubscription(), snapshot),
                context.activeSubscription() == null ? null : context.activeSubscription().displayName(),
                context.activeSubscription() == null ? "NONE" : String.valueOf(context.activeSubscription().subscriptionStatus()),
                context.activeSubscription() == null ? null : context.activeSubscription().planTemplateName(),
                context.activeSubscription() == null ? (snapshot == null || snapshot.publishedVersionNumber() == null ? "—" : "Version " + snapshot.publishedVersionNumber()) : context.activeSubscription().publishedVersionLabel(),
                snapshotStatus,
                validationState,
                snapshot == null ? null : snapshot.generatedAt(),
                comparisonStatus,
                moduleDifferences,
                featureDifferences,
                limitDifferences,
                activeOverrides,
                context.runtimeSource(),
                readiness.status(),
                readiness.status(),
                readiness.blockers(),
                readiness.warnings(),
                readiness.recommendation(),
                readiness.targetRoute(),
                differences
        );
    }

    private String extractValidationState(RuntimeDiffTenantResponse row) {
        return "CURRENT".equals(row.snapshotStatus()) ? "VALID" : row.snapshotStatus();
    }

    private String describeSubscription(com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionSummaryResponse subscription, EffectiveEntitlementSnapshotResponse snapshot) {
        if (subscription == null) {
            return "None";
        }
        String version = subscription.publishedVersionLabel() != null ? subscription.publishedVersionLabel() : (snapshot != null && snapshot.publishedVersionNumber() != null ? "Version " + snapshot.publishedVersionNumber() : "—");
        return subscription.displayName() + " · " + subscription.planTemplateName() + " · " + version + " · " + subscription.subscriptionStatus();
    }

    private String deriveComparisonStatus(LegacyComparisonResponse comparison, String snapshotStatus, String validationState) {
        if ("MISSING".equals(snapshotStatus)) {
            return "SNAPSHOT_MISSING";
        }
        if ("INVALID".equals(snapshotStatus) || "INVALID".equalsIgnoreCase(validationState)) {
            return "SNAPSHOT_INVALID";
        }
        if (comparison == null) {
            return "NOT_EVALUATED";
        }
        boolean anyDifferences = comparison.modules().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.features().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.limits().stream().anyMatch(item -> !"MATCH".equals(item.category()));
        return anyDifferences ? "DIFFERENT" : "MATCH";
    }

    private String differenceText(com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.ComparisonResponse item) {
        return switch (String.valueOf(item.category())) {
            case "LEGACY_ONLY" -> "Legacy only: " + item.label();
            case "COMMERCIAL_ONLY" -> "Commercial only: " + item.label();
            case "LIMIT_DIFFERENCE", "DIFFERENT" -> "Different: " + item.label();
            default -> "Match: " + item.label();
        };
    }

    private ReadinessProjection readiness(RuntimeContext context, EffectiveEntitlementSnapshotResponse snapshot, LegacyComparisonResponse comparison) {
        if (context.activeSubscription() == null) {
            return new ReadinessProjection("BLOCKED", List.of("No active commercial subscription"), List.copyOf(context.integrityWarnings()), "Assign and activate a commercial subscription", "/platform/commercial/subscriptions");
        }
        if (snapshot == null) {
            return new ReadinessProjection("BLOCKED", List.of("No current effective entitlement snapshot"), List.copyOf(context.integrityWarnings()), "Generate an effective entitlement snapshot", "/platform/commercial/entitlements");
        }
        if (!"CURRENT".equals(snapshot.snapshotStatus()) || !"VALID".equalsIgnoreCase(snapshot.validationState())) {
            String remediation = snapshot.validationFindings().isEmpty() ? "Resolve snapshot validation findings and regenerate" : snapshot.validationFindings().get(0).remediation();
            return new ReadinessProjection("BLOCKED", List.of(snapshotIssue(snapshot)), List.copyOf(context.integrityWarnings()), remediation == null || remediation.isBlank() ? "Resolve snapshot validation findings and regenerate" : remediation, "/platform/commercial/entitlements");
        }
        if (comparison == null) {
            return new ReadinessProjection("NOT_EVALUATED", List.of("Legacy comparison not run"), List.copyOf(context.integrityWarnings()), "Run legacy comparison", "/platform/commercial/runtime-diff");
        }
        boolean anyDifferences = comparison.modules().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.features().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.limits().stream().anyMatch(item -> !"MATCH".equals(item.category()));
        if (anyDifferences) {
            return new ReadinessProjection("READY_WITH_WARNINGS", List.of(), List.copyOf(context.integrityWarnings()), "Review differences before rollout", "/platform/commercial/runtime-diff");
        }
        return new ReadinessProjection("READY", List.of(), List.copyOf(context.integrityWarnings()), runtimeProperties.isEnabled() ? "Commercial runtime is authoritative" : "Ready for allowlisted commercial runtime pilot", "/platform/commercial/runtime-diff");
    }

    private String snapshotIssue(EffectiveEntitlementSnapshotResponse snapshot) {
        if (snapshot == null) {
            return "No current effective entitlement snapshot";
        }
        if ("INVALID".equals(snapshot.snapshotStatus())) {
            return snapshot.validationFindings().isEmpty() ? "Snapshot generation failed" : snapshot.validationFindings().get(0).message();
        }
        return "Snapshot is not current";
    }

    private record ReadinessProjection(String status, List<String> blockers, List<String> warnings, String recommendation, String targetRoute) {
    }
}
