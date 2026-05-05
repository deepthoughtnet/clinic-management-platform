package com.deepthoughtnet.clinic.ai.orchestration.db;

import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiUsageSummaryRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRequestAuditRepository extends JpaRepository<AiRequestAuditEntity, UUID> {
    @Query("""
            select new com.deepthoughtnet.clinic.ai.orchestration.service.model.AiUsageSummaryRecord(
                :productCode,
                :tenantId,
                count(a),
                coalesce(sum(case when a.status = 'SUCCESS' then 1 else 0 end), 0),
                coalesce(sum(case when a.status = 'FAILED' then 1 else 0 end), 0),
                coalesce(sum(case when a.status = 'FALLBACK' then 1 else 0 end), 0),
                coalesce(sum(a.inputTokens), 0),
                coalesce(sum(a.outputTokens), 0),
                coalesce(sum(a.totalTokens), 0),
                coalesce(sum(a.estimatedCost), 0)
            )
            from AiRequestAuditEntity a
            where a.tenantId = :tenantId
              and a.productCode = :productCode
              and a.createdAt between :from and :to
            """)
    AiUsageSummaryRecord summarizeUsage(@Param("tenantId") UUID tenantId,
                                        @Param("productCode") String productCode,
                                        @Param("from") OffsetDateTime from,
                                        @Param("to") OffsetDateTime to);

    List<AiRequestAuditEntity> findTop20ByTenantIdAndProductCodeOrderByCreatedAtDesc(UUID tenantId, String productCode);
}
