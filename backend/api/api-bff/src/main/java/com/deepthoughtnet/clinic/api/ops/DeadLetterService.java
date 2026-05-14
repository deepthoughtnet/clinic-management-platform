package com.deepthoughtnet.clinic.api.ops;

import com.deepthoughtnet.clinic.api.ops.db.DeadLetterEventEntity;
import com.deepthoughtnet.clinic.api.ops.db.DeadLetterEventRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.orchestration.AiCallOrchestrationService;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dead-letter listing and replay foundation for operational recovery workflows. */
@Service
public class DeadLetterService {
    private final DeadLetterEventRepository deadLetterRepository;
    private final CampaignExecutionService campaignExecutionService;
    private final AiCallOrchestrationService aiCallOrchestrationService;

    public DeadLetterService(
            DeadLetterEventRepository deadLetterRepository,
            CampaignExecutionService campaignExecutionService,
            AiCallOrchestrationService aiCallOrchestrationService
    ) {
        this.deadLetterRepository = deadLetterRepository;
        this.campaignExecutionService = campaignExecutionService;
        this.aiCallOrchestrationService = aiCallOrchestrationService;
    }

    @Transactional
    public List<DeadLetterEventEntity> list(UUID tenantId) {
        seedFromCurrentFailures(tenantId);
        return deadLetterRepository.findTop200ByTenantIdOrderByDeadLetteredAtDesc(tenantId);
    }

    @Transactional
    public DeadLetterEventEntity replay(UUID tenantId, UUID deadLetterId) {
        DeadLetterEventEntity row = deadLetterRepository.findByTenantIdAndId(tenantId, deadLetterId)
                .orElseThrow(() -> new IllegalArgumentException("Dead-letter event not found"));
        try {
            if (row.getSourceType() == DeadLetterEventEntity.SourceType.CAMPAIGN_EXECUTION) {
                campaignExecutionService.retryExecution(tenantId, row.getSourceExecutionId());
            } else {
                aiCallOrchestrationService.retry(tenantId, row.getSourceExecutionId());
            }
            row.markReplayed();
        } catch (RuntimeException ex) {
            row.markReplayFailed(ex.getMessage());
            throw ex;
        }
        return deadLetterRepository.save(row);
    }

    private void seedFromCurrentFailures(UUID tenantId) {
        List<CampaignExecutionRecord> campaignFailed = campaignExecutionService.listFailed(tenantId);
        for (CampaignExecutionRecord row : campaignFailed) {
            if (deadLetterRepository.existsByTenantIdAndSourceTypeAndSourceExecutionId(
                    tenantId,
                    DeadLetterEventEntity.SourceType.CAMPAIGN_EXECUTION,
                    row.id()
            )) {
                continue;
            }
            deadLetterRepository.save(DeadLetterEventEntity.create(
                    tenantId,
                    DeadLetterEventEntity.SourceType.CAMPAIGN_EXECUTION,
                    row.id(),
                    row.failureReason(),
                    "status=" + row.status() + ", channel=" + row.channelType(),
                    row.attemptCount()
            ));
        }

        var aiRows = aiCallOrchestrationService.search(
                tenantId,
                new com.deepthoughtnet.clinic.carepilot.ai_call.service.model.AiCallExecutionSearchCriteria(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                0,
                200
        ).getContent();

        for (var row : aiRows) {
            if (!(row.executionStatus() == AiCallExecutionStatus.FAILED
                    || row.executionStatus() == AiCallExecutionStatus.ESCALATED
                    || row.executionStatus() == AiCallExecutionStatus.SUPPRESSED)) {
                continue;
            }
            if (deadLetterRepository.existsByTenantIdAndSourceTypeAndSourceExecutionId(
                    tenantId,
                    DeadLetterEventEntity.SourceType.AI_CALL_EXECUTION,
                    row.id()
            )) {
                continue;
            }
            deadLetterRepository.save(DeadLetterEventEntity.create(
                    tenantId,
                    DeadLetterEventEntity.SourceType.AI_CALL_EXECUTION,
                    row.id(),
                    row.failureReason(),
                    "status=" + row.executionStatus() + ", provider=" + row.providerName(),
                    row.retryCount()
            ));
        }
    }
}
