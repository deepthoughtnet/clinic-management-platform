package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.carepilot.ai_call.orchestration.AiCallOrchestrationService;
import com.deepthoughtnet.clinic.carepilot.featureflag.service.FeatureFlagService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reconciles stale in-progress AI calls into retry/failure paths. */
@Component
@ConditionalOnProperty(prefix = "carepilot.ai-calls.reconciliation", name = "enabled", havingValue = "true")
public class CarePilotAiCallReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(CarePilotAiCallReconciliationScheduler.class);

    private final PlatformTenantManagementService tenantManagementService;
    private final FeatureFlagService featureFlagService;
    private final AiCallOrchestrationService orchestrationService;
    private final Duration staleAfter;

    public CarePilotAiCallReconciliationScheduler(
            PlatformTenantManagementService tenantManagementService,
            FeatureFlagService featureFlagService,
            AiCallOrchestrationService orchestrationService,
            @org.springframework.beans.factory.annotation.Value("${carepilot.ai-calls.reconciliation.stale-after:PT30M}") Duration staleAfter
    ) {
        this.tenantManagementService = tenantManagementService;
        this.featureFlagService = featureFlagService;
        this.orchestrationService = orchestrationService;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${carepilot.ai-calls.reconciliation.fixed-delay:PT5M}")
    public void reconcile() {
        for (var tenant : tenantManagementService.list()) {
            if (!featureFlagService.carePilotForTenant(tenant.id()).carePilotEnabled()) {
                continue;
            }
            try {
                orchestrationService.reconcileStaleExecutions(tenant.id(), staleAfter);
            } catch (RuntimeException ex) {
                log.warn("AI calls reconciliation failed. tenantId={}, reason={}", tenant.id(), ex.getMessage());
            }
        }
    }
}
