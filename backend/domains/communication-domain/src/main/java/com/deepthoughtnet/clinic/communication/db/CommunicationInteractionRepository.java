package com.deepthoughtnet.clinic.communication.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationInteractionRepository extends JpaRepository<CommunicationInteractionEntity, UUID> {
    Optional<CommunicationInteractionEntity> findByProviderCallId(String providerCallId);
}
