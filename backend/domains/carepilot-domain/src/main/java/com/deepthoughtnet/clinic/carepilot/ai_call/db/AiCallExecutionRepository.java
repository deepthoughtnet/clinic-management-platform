package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for tenant-scoped AI call execution records. */
public interface AiCallExecutionRepository extends JpaRepository<AiCallExecutionEntity, UUID>, JpaSpecificationExecutor<AiCallExecutionEntity> {
    Optional<AiCallExecutionEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<AiCallExecutionEntity> findByTenantIdAndExecutionStatusInAndScheduledAtLessThanEqual(
            UUID tenantId,
            List<AiCallExecutionStatus> statuses,
            OffsetDateTime scheduledAt
    );
    List<AiCallExecutionEntity> findByTenantIdAndExecutionStatusInAndLastAttemptAtLessThanEqual(
            UUID tenantId,
            List<AiCallExecutionStatus> statuses,
            OffsetDateTime staleBefore
    );
    Optional<AiCallExecutionEntity> findByTenantIdAndProviderCallId(UUID tenantId, String providerCallId);
    long countByTenantId(UUID tenantId);
    long countByTenantIdAndExecutionStatus(UUID tenantId, AiCallExecutionStatus status);
    long countByTenantIdAndPatientIdAndCreatedAtGreaterThanEqual(UUID tenantId, UUID patientId, OffsetDateTime createdAt);
    @Query("select count(e) from AiCallExecutionEntity e where e.tenantId = :tenantId and e.executionStatus in :statuses")
    long countByTenantIdAndStatuses(@Param("tenantId") UUID tenantId, @Param("statuses") List<AiCallExecutionStatus> statuses);
}
