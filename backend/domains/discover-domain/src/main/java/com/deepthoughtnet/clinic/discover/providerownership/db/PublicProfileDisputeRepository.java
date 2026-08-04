package com.deepthoughtnet.clinic.discover.providerownership.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicProfileDisputeRepository extends JpaRepository<PublicProfileDisputeEntity, UUID> {
    List<PublicProfileDisputeEntity> findByPublicProfileReferenceOrderByUpdatedAtDesc(String publicProfileReference);
    Optional<PublicProfileDisputeEntity> findTopByPublicProfileReferenceAndDisputeStatusNotInOrderByUpdatedAtDesc(String publicProfileReference, java.util.Collection<com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileDisputeStatus> statuses);
}
