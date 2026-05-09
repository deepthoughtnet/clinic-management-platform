package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiClinicalAnalyticsResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiUsageAggregationService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiUsageSummaryRecord;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiClinicalAnalyticsService {
    private final AiUsageAggregationService usageAggregationService;
    private final ClinicalAiJobRepository jobRepository;
    private final ClinicalDocumentRepository documentRepository;

    public AiClinicalAnalyticsService(AiUsageAggregationService usageAggregationService,
                                      ClinicalAiJobRepository jobRepository,
                                      ClinicalDocumentRepository documentRepository) {
        this.usageAggregationService = usageAggregationService;
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
    }

    public AiClinicalAnalyticsResponse summarize(UUID tenantId) {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.minusDays(30);
        AiUsageSummaryRecord usage = usageAggregationService.summarize(tenantId, AiProductCode.CLINIC, from, to);
        long documentCount = documentRepository.countByTenantIdAndCreatedAtBetween(tenantId, from, to);
        long reviewRequiredCount = documentRepository.countByTenantIdAndAiExtractionStatusAndCreatedAtBetween(tenantId, "REVIEW_REQUIRED", from, to);
        long approvedCount = documentRepository.countByTenantIdAndAiExtractionStatusAndCreatedAtBetween(tenantId, "APPROVED", from, to);
        long rejectedCount = documentRepository.countByTenantIdAndAiExtractionStatusAndCreatedAtBetween(tenantId, "REJECTED", from, to);
        long retryCount = jobRepository.countByTenantIdAndAttemptCountGreaterThanAndCreatedAtBetween(tenantId, 1, from, to);
        BigDecimal averageConfidence = jobRepository.averageConfidence(tenantId, from, to);
        long reviewedCount = approvedCount + rejectedCount;
        BigDecimal acceptanceRate = reviewedCount <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(approvedCount).divide(BigDecimal.valueOf(reviewedCount), 4, java.math.RoundingMode.HALF_UP);
        return new AiClinicalAnalyticsResponse(
                tenantId,
                from,
                to,
                usage == null || usage.requestCount() == null ? 0L : usage.requestCount(),
                usage == null || usage.successCount() == null ? 0L : usage.successCount(),
                usage == null || usage.failedCount() == null ? 0L : usage.failedCount(),
                usage == null || usage.fallbackCount() == null ? 0L : usage.fallbackCount(),
                documentCount,
                reviewRequiredCount,
                approvedCount,
                rejectedCount,
                retryCount,
                averageConfidence == null ? BigDecimal.ZERO : averageConfidence,
                acceptanceRate
        );
    }
}
