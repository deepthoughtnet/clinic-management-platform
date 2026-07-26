package com.deepthoughtnet.clinic.commercial.entitlement;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementModels.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionModels.SubscriptionSummaryResponse;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionService;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialTenantRuntimeContextService {
    public record RuntimeContext(
            UUID tenantId,
            SubscriptionSummaryResponse activeSubscription,
            EffectiveEntitlementSnapshotResponse snapshot,
            String runtimeSource,
            long activeOverrideCount,
            List<String> integrityWarnings,
            String readinessStatus,
            List<String> blockers,
            List<String> warnings,
            String recommendedAction,
            String targetRoute
    ) {
    }

    private final CommercialSubscriptionService subscriptionService;
    private final CommercialEffectiveEntitlementService entitlementService;
    private final CommercialRuntimeProperties runtimeProperties;

    public CommercialTenantRuntimeContextService(
            CommercialSubscriptionService subscriptionService,
            CommercialEffectiveEntitlementService entitlementService,
            CommercialRuntimeProperties runtimeProperties
    ) {
        this.subscriptionService = subscriptionService;
        this.entitlementService = entitlementService;
        this.runtimeProperties = runtimeProperties;
    }

    @Transactional(readOnly = true)
    public RuntimeContext resolve(UUID tenantId) {
        SubscriptionSummaryResponse activeSubscription = subscriptionService.getActiveSubscription(tenantId);
        EffectiveEntitlementSnapshotResponse snapshot = entitlementService.getCurrentSnapshot(tenantId);
        List<String> integrityWarnings = new ArrayList<>();
        if (activeSubscription == null) {
            integrityWarnings.add("No active commercial subscription was found for this tenant.");
        }
        if (snapshot == null) {
            integrityWarnings.add("No current effective entitlement snapshot is available.");
        } else if (activeSubscription != null && snapshot.subscriptionId() != null && !snapshot.subscriptionId().equals(activeSubscription.id())) {
            integrityWarnings.add("The current snapshot is not sourced from the active commercial subscription.");
        }

        long activeOverrideCount = snapshot == null ? 0 : snapshot.overrides().stream().filter(override -> "ACTIVE".equals(override.status()) || "APPROVED".equals(override.status()) || "SCHEDULED".equals(override.status())).count();
        String runtimeSource = runtimeSource(tenantId);
        Readiness readiness = readiness(activeSubscription, snapshot, integrityWarnings);
        return new RuntimeContext(
                tenantId,
                activeSubscription,
                snapshot,
                runtimeSource,
                activeOverrideCount,
                integrityWarnings,
                readiness.status(),
                readiness.blockers(),
                readiness.warnings(),
                readiness.recommendation(),
                readiness.targetRoute()
        );
    }

    @Transactional(readOnly = true)
    public String runtimeSource(UUID tenantId) {
        return runtimeSource(tenantId, runtimeProperties.isEnabled(), runtimeProperties.isShadowCompareEnabled(), runtimeProperties.getTenantAllowlist());
    }

    private String runtimeSource(UUID tenantId, boolean enabled, boolean shadowCompareEnabled, java.util.Set<String> allowlist) {
        if (enabled && (allowlist == null || allowlist.isEmpty() || allowlist.contains(String.valueOf(tenantId)))) {
            return "Commercial Runtime — Authoritative";
        }
        if (!enabled && shadowCompareEnabled) {
            return "Shadow Comparison — Enabled";
        }
        return "Legacy Runtime — Authoritative";
    }

    private Readiness readiness(SubscriptionSummaryResponse activeSubscription, EffectiveEntitlementSnapshotResponse snapshot, List<String> integrityWarnings) {
        if (activeSubscription == null) {
            return new Readiness(
                    "BLOCKED",
                    List.of("No active commercial subscription"),
                    integrityWarnings,
                    "Assign and activate a commercial subscription",
                    "/platform/commercial/subscriptions"
            );
        }
        if (snapshot == null) {
            return new Readiness(
                    "BLOCKED",
                    List.of("No current effective entitlement snapshot"),
                    integrityWarnings,
                    "Generate an effective entitlement snapshot",
                    "/platform/commercial/entitlements"
            );
        }
        if (!"CURRENT".equals(snapshot.snapshotStatus()) || !"VALID".equalsIgnoreCase(snapshot.validationState())) {
            String blocker = "INVALID".equals(snapshot.snapshotStatus()) ? "Snapshot generation failed" : "Snapshot is not current";
            String recommendation = snapshot.validationFindings().isEmpty()
                    ? "Resolve snapshot validation findings and regenerate"
                    : snapshot.validationFindings().get(0).remediation();
            return new Readiness(
                    "BLOCKED",
                    List.of(blocker),
                    integrityWarnings,
                    recommendation == null || recommendation.isBlank() ? "Resolve snapshot validation findings and regenerate" : recommendation,
                    "/platform/commercial/entitlements"
            );
        }
        List<String> warnings = new ArrayList<>(integrityWarnings);
        String status = warnings.isEmpty() ? "READY" : "READY_WITH_WARNINGS";
        String recommendation = warnings.isEmpty() ? "Ready for shadow mode" : "Review integrity warnings before enabling commercial runtime";
        return new Readiness(status, List.of(), warnings, recommendation, "/platform/commercial/runtime-diff");
    }

    private record Readiness(String status, List<String> blockers, List<String> warnings, String recommendation, String targetRoute) {
    }
}
