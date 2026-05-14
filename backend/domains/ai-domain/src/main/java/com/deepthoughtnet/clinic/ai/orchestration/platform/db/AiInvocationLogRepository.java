package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiInvocationLogRepository extends JpaRepository<AiInvocationLogEntity, UUID> {
    java.util.List<AiInvocationLogEntity> findByTenantIdAndCreatedAtBetween(UUID tenantId, java.time.OffsetDateTime from, java.time.OffsetDateTime to);
    List<AiInvocationLogEntity> findTop200ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("""
            select count(l) from AiInvocationLogEntity l
            where l.tenantId = :tenantId and l.createdAt between :from and :to
            """)
    long countCalls(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            select coalesce(sum(case when l.status in ('SUCCESS','COMPLETED') then 1 else 0 end),0) from AiInvocationLogEntity l
            where l.tenantId = :tenantId and l.createdAt between :from and :to
            """)
    long countSuccessful(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            select coalesce(sum(case when l.status not in ('SUCCESS','COMPLETED') then 1 else 0 end),0) from AiInvocationLogEntity l
            where l.tenantId = :tenantId and l.createdAt between :from and :to
            """)
    long countFailed(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            select coalesce(sum(l.inputTokenCount),0), coalesce(sum(l.outputTokenCount),0),
                   coalesce(sum(l.estimatedCost),0), coalesce(avg(l.latencyMs),0)
            from AiInvocationLogEntity l
            where l.tenantId = :tenantId and l.createdAt between :from and :to
            """)
    Object[] summarizeTotals(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("""
            select l.providerName, count(l)
            from AiInvocationLogEntity l
            where l.tenantId = :tenantId and l.createdAt between :from and :to
              and (:provider is null or l.providerName = :provider)
              and (:useCase is null or l.useCase = :useCase)
            group by l.providerName
            """)
    List<Object[]> groupByProvider(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from,
                                   @Param("to") OffsetDateTime to, @Param("provider") String provider,
                                   @Param("useCase") String useCase);

    @Query("""
            select l.useCase, count(l)
            from AiInvocationLogEntity l
            where l.tenantId = :tenantId and l.createdAt between :from and :to
              and (:provider is null or l.providerName = :provider)
              and (:useCase is null or l.useCase = :useCase)
            group by l.useCase
            """)
    List<Object[]> groupByUseCase(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from,
                                  @Param("to") OffsetDateTime to, @Param("provider") String provider,
                                  @Param("useCase") String useCase);

    @Query("""
            select l.status, count(l)
            from AiInvocationLogEntity l
            where l.tenantId = :tenantId and l.createdAt between :from and :to
              and (:provider is null or l.providerName = :provider)
              and (:useCase is null or l.useCase = :useCase)
            group by l.status
            """)
    List<Object[]> groupByStatus(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from,
                                 @Param("to") OffsetDateTime to, @Param("provider") String provider,
                                 @Param("useCase") String useCase);
}
