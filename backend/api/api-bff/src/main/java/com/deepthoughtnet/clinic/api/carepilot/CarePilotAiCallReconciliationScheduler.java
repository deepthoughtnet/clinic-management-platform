package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import com.deepthoughtnet.clinic.carepilot.ai_call.orchestration.AiCallOrchestrationService;
import com.deepthoughtnet.clinic.carepilot.featureflag.service.FeatureFlagService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.platform.spring.lock.DistributedLockService;
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
    private static final String LOCK_KEY = "scheduler:carepilot-ai-calls-reconciliation";

    private final PlatformTenantManagementService tenantManagementService;
    private final FeatureFlagService featureFlagService;
    private final AiCallOrchestrationService orchestrationService;
    private final Duration staleAfter;
    private final DistributedLockService lockService;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final Duration lockWaitTimeout;

    public CarePilotAiCallReconciliationScheduler(
            PlatformTenantManagementService tenantManagementService,
            FeatureFlagService featureFlagService,
            AiCallOrchestrationService orchestrationService,
            DistributedLockService lockService,
            SchedulerLockMonitor schedulerLockMonitor,
            @org.springframework.beans.factory.annotation.Value("${platform.locks.scheduler-wait-timeout:PT2S}") Duration lockWaitTimeout,
            @org.springframework.beans.factory.annotation.Value("${carepilot.ai-calls.reconciliation.stale-after:PT30M}") Duration staleAfter
    ) {
        this.tenantManagementService = tenantManagementService;
        this.featureFlagService = featureFlagService;
        this.orchestrationService = orchestrationService;
        this.lockService = lockService;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.lockWaitTimeout = lockWaitTimeout;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${carepilot.ai-calls.reconciliation.fixed-delay:PT5M}")
    public void reconcile() {
        boolean ran = lockService.executeWithLock(LOCK_KEY, lockWaitTimeout, () -> {
            schedulerLockMonitor.markAcquired("ai-call-reconciliation-scheduler");
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
            return null;
        });
        if (!ran) {
            schedulerLockMonitor.markSkipped("ai-call-reconciliation-scheduler");
            log.debug("Skipped AI calls reconciliation scheduler run because lock is held.");
        }
    }
}
