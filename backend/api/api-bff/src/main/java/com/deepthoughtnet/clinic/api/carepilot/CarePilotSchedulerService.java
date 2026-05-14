package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.platform.spring.lock.DistributedLockService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

/**
 * Lightweight scheduler that advances due CarePilot executions without external provider calls.
 */
@Service
@ConditionalOnProperty(prefix = "clinic.carepilot.scheduler", name = "enabled", havingValue = "true")
public class CarePilotSchedulerService {
    private static final String LOCK_KEY = "scheduler:carepilot-campaign-execution";
    private final CampaignExecutionService campaignExecutionService;
    private final int batchSize;
    private final DistributedLockService lockService;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final Duration lockWaitTimeout;

    public CarePilotSchedulerService(
            CampaignExecutionService campaignExecutionService,
            @Value("${clinic.carepilot.scheduler.batch-size:25}") int batchSize,
            DistributedLockService lockService,
            SchedulerLockMonitor schedulerLockMonitor,
            @Value("${platform.locks.scheduler-wait-timeout:PT2S}") Duration lockWaitTimeout
    ) {
        this.campaignExecutionService = campaignExecutionService;
        this.batchSize = Math.max(1, batchSize);
        this.lockService = lockService;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.lockWaitTimeout = lockWaitTimeout;
    }

    @Scheduled(fixedDelayString = "${clinic.carepilot.scheduler.fixedDelay:PT2M}")
    public void processDueExecutions() {
        boolean ran = lockService.executeWithLock(LOCK_KEY, lockWaitTimeout, () -> {
            schedulerLockMonitor.markAcquired("carepilot-campaign-execution-scheduler");
            // Processing stays in-domain so future outbox/provider dispatch can be inserted without API coupling.
            campaignExecutionService.processDueExecutions(batchSize);
            return null;
        });
        if (!ran) {
            schedulerLockMonitor.markSkipped("carepilot-campaign-execution-scheduler");
        }
    }
}
