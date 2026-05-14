package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import com.deepthoughtnet.clinic.carepilot.ai_call.orchestration.AiCallOrchestrationService;
import com.deepthoughtnet.clinic.carepilot.featureflag.service.FeatureFlagService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.platform.spring.lock.DistributedLockService;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled dispatcher for due AI calls with bounded, tenant-safe processing. */
@Component
@ConditionalOnProperty(prefix = "carepilot.ai-calls.scheduler", name = "enabled", havingValue = "true")
public class CarePilotAiCallScheduler {
    private static final Logger log = LoggerFactory.getLogger(CarePilotAiCallScheduler.class);
    private static final String LOCK_KEY = "scheduler:carepilot-ai-calls-dispatch";

    private final PlatformTenantManagementService tenantManagementService;
    private final FeatureFlagService featureFlagService;
    private final AiCallOrchestrationService orchestrationService;
    private final CarePilotAiCallSchedulerMonitor monitor;
    private final DistributedLockService lockService;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final Duration lockWaitTimeout;

    public CarePilotAiCallScheduler(
            PlatformTenantManagementService tenantManagementService,
            FeatureFlagService featureFlagService,
            AiCallOrchestrationService orchestrationService,
            CarePilotAiCallSchedulerMonitor monitor,
            DistributedLockService lockService,
            SchedulerLockMonitor schedulerLockMonitor,
            @org.springframework.beans.factory.annotation.Value("${platform.locks.scheduler-wait-timeout:PT2S}") Duration lockWaitTimeout
    ) {
        this.tenantManagementService = tenantManagementService;
        this.featureFlagService = featureFlagService;
        this.orchestrationService = orchestrationService;
        this.monitor = monitor;
        this.lockService = lockService;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.lockWaitTimeout = lockWaitTimeout;
    }

    @Scheduled(fixedDelayString = "${carepilot.ai-calls.scheduler.fixed-delay:PT1M}")
    public void dispatchDueCalls() {
        boolean ran = lockService.executeWithLock(LOCK_KEY, lockWaitTimeout, () -> {
            schedulerLockMonitor.markAcquired("ai-call-dispatch-scheduler");
            OffsetDateTime startedAt = OffsetDateTime.now();
            int processed = 0;
            int dispatched = 0;
            int failed = 0;
            int skipped = 0;

            for (var tenant : tenantManagementService.list()) {
                var tenantId = tenant.id();
                if (!featureFlagService.carePilotForTenant(tenantId).carePilotEnabled()) {
                    continue;
                }
                try {
                    var result = orchestrationService.dispatchDueExecutions(tenantId);
                    processed += result.processed();
                    dispatched += result.dispatched();
                    failed += result.failed();
                    skipped += result.skipped();
                } catch (RuntimeException ex) {
                    failed += 1;
                    log.warn("AI calls scheduler tenant dispatch failed. tenantId={}, reason={}", tenantId, ex.getMessage());
                }
            }

            long durationMs = Duration.between(startedAt, OffsetDateTime.now()).toMillis();
            monitor.markRun(OffsetDateTime.now(), processed, dispatched, failed, skipped, durationMs);
            return null;
        });
        if (!ran) {
            schedulerLockMonitor.markSkipped("ai-call-dispatch-scheduler");
            log.debug("Skipped AI call dispatch scheduler run because lock is held.");
        }
    }
}
