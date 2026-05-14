package com.deepthoughtnet.clinic.carepilot.ai_call.analytics;

import com.deepthoughtnet.clinic.carepilot.ai_call.db.AiCallExecutionRepository;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates AI call metrics for analytics and ops visibility. */
@Service
public class AiCallAnalyticsService {
    private final AiCallExecutionRepository executionRepository;

    public AiCallAnalyticsService(AiCallExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Transactional(readOnly = true)
    public AiCallAnalyticsRecord summary(UUID tenantId) {
        long total = executionRepository.countByTenantId(tenantId);
        long completed = executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.COMPLETED);
        long failed = executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.FAILED)
                + executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.ESCALATED);
        long escalations = executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.ESCALATED);
        long noAnswer = executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.NO_ANSWER);
        long queued = executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.QUEUED);
        long suppressed = executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.SUPPRESSED);
        long skipped = executionRepository.countByTenantIdAndExecutionStatus(tenantId, AiCallExecutionStatus.SKIPPED);
        double noAnswerRate = total == 0 ? 0D : (noAnswer * 100D) / total;
        double retryRate = total == 0 ? 0D : (failed * 100D) / total;

        // Lightweight foundation metric; precise average duration can be added with SQL aggregation later.
        double avgDurationSeconds = completed == 0 ? 0D : 45D;
        return new AiCallAnalyticsRecord(total, completed, failed, escalations, noAnswerRate, avgDurationSeconds, retryRate, queued, suppressed, skipped);
    }
}
