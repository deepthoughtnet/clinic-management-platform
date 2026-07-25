package com.deepthoughtnet.clinic.commercial.subscription.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialSubscriptionEventRepository extends JpaRepository<CommercialSubscriptionEventEntity, UUID> {
    List<CommercialSubscriptionEventEntity> findBySubscription_IdOrderByPerformedAtDesc(UUID subscriptionId);
}
