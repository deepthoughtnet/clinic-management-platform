package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiUsageAggregationService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiUsageSummaryRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiClinicalAnalyticsServiceTest {
    @Test
    void summarizeCombinesAuditAndClinicalDocumentSignals() {
        AiUsageAggregationService usageAggregationService = mock(AiUsageAggregationService.class);
        ClinicalAiJobRepository jobRepository = mock(ClinicalAiJobRepository.class);
        ClinicalDocumentRepository documentRepository = mock(ClinicalDocumentRepository.class);
        AiClinicalAnalyticsService service = new AiClinicalAnalyticsService(usageAggregationService, jobRepository, documentRepository);
        UUID tenantId = UUID.randomUUID();
        when(usageAggregationService.summarize(eq(tenantId), eq(com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode.CLINIC), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(new AiUsageSummaryRecord("CLINIC", tenantId, 10L, 7L, 2L, 1L, 100L, 50L, 150L, BigDecimal.valueOf(0.12)));
        when(documentRepository.countByTenantIdAndCreatedAtBetween(eq(tenantId), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(5L);
        when(documentRepository.countByTenantIdAndAiExtractionStatusAndCreatedAtBetween(eq(tenantId), eq("REVIEW_REQUIRED"), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(2L);
        when(documentRepository.countByTenantIdAndAiExtractionStatusAndCreatedAtBetween(eq(tenantId), eq("APPROVED"), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(2L);
        when(documentRepository.countByTenantIdAndAiExtractionStatusAndCreatedAtBetween(eq(tenantId), eq("REJECTED"), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(1L);
        when(jobRepository.countByTenantIdAndAttemptCountGreaterThanAndCreatedAtBetween(eq(tenantId), eq(1), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(3L);
        when(jobRepository.averageConfidence(eq(tenantId), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(BigDecimal.valueOf(0.89));

        var response = service.summarize(tenantId);

        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.requestCount()).isEqualTo(10L);
        assertThat(response.documentCount()).isEqualTo(5L);
        assertThat(response.reviewRequiredCount()).isEqualTo(2L);
        assertThat(response.approvedCount()).isEqualTo(2L);
        assertThat(response.acceptanceRate()).isEqualByComparingTo("0.6667");
        assertThat(response.averageConfidence()).isEqualByComparingTo("0.89");
    }
}
