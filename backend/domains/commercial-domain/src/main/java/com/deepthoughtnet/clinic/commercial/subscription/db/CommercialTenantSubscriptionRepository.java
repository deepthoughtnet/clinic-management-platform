package com.deepthoughtnet.clinic.commercial.subscription.db;

import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommercialTenantSubscriptionRepository extends JpaRepository<CommercialTenantSubscriptionEntity, UUID>, JpaSpecificationExecutor<CommercialTenantSubscriptionEntity> {
    List<CommercialTenantSubscriptionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<CommercialTenantSubscriptionEntity> findByTenantIdAndSubscriptionStatusInOrderByStartDateAscCreatedAtAsc(UUID tenantId, Collection<SubscriptionStatus> statuses);

    List<CommercialTenantSubscriptionEntity> findByTenantIdAndSubscriptionStatusOrderByStartDateAscCreatedAtAsc(UUID tenantId, SubscriptionStatus status);

    long countBySubscriptionStatus(SubscriptionStatus status);

    @Query("select count(distinct s.tenantId) from CommercialTenantSubscriptionEntity s where s.subscriptionStatus = :status")
    long countDistinctTenantsBySubscriptionStatus(@Param("status") SubscriptionStatus status);

    Optional<CommercialTenantSubscriptionEntity> findTopByTenantIdAndSubscriptionStatusOrderByCreatedAtDesc(UUID tenantId, SubscriptionStatus status);
}
