package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffTenantResponse;
import com.deepthoughtnet.clinic.api.platform.service.PlatformTenantService;
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
    private final CommercialRuntimeProperties runtimeProperties;

    public CommercialRuntimeDiffApiService(
            PlatformTenantService platformTenantService,
            CommercialEffectiveEntitlementApiService entitlementApiService,
            CommercialRuntimeProperties runtimeProperties
    ) {
        this.platformTenantService = platformTenantService;
        this.entitlementApiService = entitlementApiService;
        this.runtimeProperties = runtimeProperties;
    }

    public RuntimeDiffSummaryResponse summary() {
        List<RuntimeDiffTenantResponse> tenants = tenants();
        long activeCommercialSubscriptions = tenants.stream().filter(row -> row.currentSubscription() != null && row.currentSubscription().startsWith("ACTIVE")).count();
        long currentValidSnapshots = tenants.stream().filter(row -> "CURRENT".equals(row.snapshotStatus()) && "VALID".equalsIgnoreCase(extractValidationState(row))).count();
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
        PlatformTenantService.PlatformTenantDetail detail = platformTenantService.getTenant(tenant.id());
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
        long activeOverrides = snapshot == null ? 0 : snapshot.overrides().stream().filter(item -> "ACTIVE".equals(item.status()) || "APPROVED".equals(item.status()) || "SCHEDULED".equals(item.status())).count();
        List<String> differences = new ArrayList<>();
        if (comparison != null) {
            differences.addAll(comparison.modules().stream().filter(item -> !"MATCH".equals(item.category())).map(this::differenceText).toList());
            differences.addAll(comparison.features().stream().filter(item -> !"MATCH".equals(item.category())).map(this::differenceText).toList());
            differences.addAll(comparison.limits().stream().filter(item -> !"MATCH".equals(item.category())).map(this::differenceText).toList());
        }
        return new RuntimeDiffTenantResponse(
                tenant.id(),
                tenant.name(),
                tenant.code(),
                describeSubscription(detail),
                snapshot == null || snapshot.publishedVersionNumber() == null ? "—" : "Version " + snapshot.publishedVersionNumber(),
                snapshotStatus,
                snapshot == null ? null : snapshot.generatedAt(),
                comparisonStatus,
                moduleDifferences,
                featureDifferences,
                limitDifferences,
                activeOverrides,
                runtimeSource(tenant.id(), snapshotStatus),
                rolloutReadiness(detail, snapshot, comparison),
                recommendation(detail, snapshot, comparison),
                differences
        );
    }

    private String extractValidationState(RuntimeDiffTenantResponse row) {
        return "CURRENT".equals(row.snapshotStatus()) ? "VALID" : row.snapshotStatus();
    }

    private String describeSubscription(PlatformTenantService.PlatformTenantDetail detail) {
        if (detail.latestSubscription() == null) {
            return "None";
        }
        return detail.latestSubscription().getStatus() + " · " + detail.latestSubscription().getPlanId();
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

    private String runtimeSource(UUID tenantId, String snapshotStatus) {
        if (runtimeProperties.isEnabled() && (runtimeProperties.getTenantAllowlist() == null || runtimeProperties.getTenantAllowlist().isEmpty() || runtimeProperties.getTenantAllowlist().contains(String.valueOf(tenantId)))) {
            return "Commercial Runtime — Authoritative";
        }
        if (!runtimeProperties.isEnabled() && runtimeProperties.isShadowCompareEnabled()) {
            return "Shadow Comparison — Enabled";
        }
        return "Legacy Runtime — Authoritative";
    }

    private String rolloutReadiness(PlatformTenantService.PlatformTenantDetail detail, EffectiveEntitlementSnapshotResponse snapshot, LegacyComparisonResponse comparison) {
        if (detail.latestSubscription() == null || !"ACTIVE".equalsIgnoreCase(detail.latestSubscription().getStatus())) {
            return "BLOCKED";
        }
        if (snapshot == null || !"CURRENT".equals(snapshot.snapshotStatus()) || !"VALID".equalsIgnoreCase(snapshot.validationState())) {
            return "BLOCKED";
        }
        if (comparison == null) {
            return "NOT_EVALUATED";
        }
        boolean anyDifferences = comparison.modules().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.features().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.limits().stream().anyMatch(item -> !"MATCH".equals(item.category()));
        return anyDifferences ? "READY_WITH_WARNINGS" : "READY";
    }

    private String recommendation(PlatformTenantService.PlatformTenantDetail detail, EffectiveEntitlementSnapshotResponse snapshot, LegacyComparisonResponse comparison) {
        if (detail.latestSubscription() == null || !"ACTIVE".equalsIgnoreCase(detail.latestSubscription().getStatus())) {
            return "Assign Subscription";
        }
        if (snapshot == null || !"CURRENT".equals(snapshot.snapshotStatus()) || !"VALID".equalsIgnoreCase(snapshot.validationState())) {
            return "Regenerate Snapshot";
        }
        if (comparison == null) {
            return "Review Differences";
        }
        boolean anyDifferences = comparison.modules().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.features().stream().anyMatch(item -> !"MATCH".equals(item.category()))
                || comparison.limits().stream().anyMatch(item -> !"MATCH".equals(item.category()));
        return anyDifferences ? "Enable Shadow Compare" : "Ready for shadow mode";
    }
}
