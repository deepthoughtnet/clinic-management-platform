package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

/**
 * Lightweight scheduler that advances due CarePilot executions without external provider calls.
 */
@Service
@ConditionalOnProperty(prefix = "clinic.carepilot.scheduler", name = "enabled", havingValue = "true")
public class CarePilotSchedulerService {
    private final CampaignExecutionService campaignExecutionService;
    private final int batchSize;

    public CarePilotSchedulerService(
            CampaignExecutionService campaignExecutionService,
            @Value("${clinic.carepilot.scheduler.batch-size:25}") int batchSize
    ) {
        this.campaignExecutionService = campaignExecutionService;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${clinic.carepilot.scheduler.fixedDelay:PT2M}")
    public void processDueExecutions() {
        // Processing stays in-domain so future outbox/provider dispatch can be inserted without API coupling.
        campaignExecutionService.processDueExecutions(batchSize);
    }
}
