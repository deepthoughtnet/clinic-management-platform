package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.carepilot.ai_call.orchestration.AiCallOrchestrationService;
import com.deepthoughtnet.clinic.carepilot.featureflag.service.FeatureFlagService;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
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

    private final PlatformTenantManagementService tenantManagementService;
    private final FeatureFlagService featureFlagService;
    private final AiCallOrchestrationService orchestrationService;
    private final CarePilotAiCallSchedulerMonitor monitor;

    public CarePilotAiCallScheduler(
            PlatformTenantManagementService tenantManagementService,
            FeatureFlagService featureFlagService,
            AiCallOrchestrationService orchestrationService,
            CarePilotAiCallSchedulerMonitor monitor
    ) {
        this.tenantManagementService = tenantManagementService;
        this.featureFlagService = featureFlagService;
        this.orchestrationService = orchestrationService;
        this.monitor = monitor;
    }

    @Scheduled(fixedDelayString = "${carepilot.ai-calls.scheduler.fixed-delay:PT1M}")
    public void dispatchDueCalls() {
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
    }
}
