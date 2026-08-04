package com.deepthoughtnet.clinic.discover.providerownership.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicProfileOwnershipRepository extends JpaRepository<PublicProfileOwnershipEntity, UUID> {
    Optional<PublicProfileOwnershipEntity> findTopByPublicProfileReferenceAndActiveTrueOrderByUpdatedAtDesc(String publicProfileReference);
    List<PublicProfileOwnershipEntity> findByPublicProfileReferenceOrderByUpdatedAtDesc(String publicProfileReference);
    List<PublicProfileOwnershipEntity> findByProviderAccountIdOrderByUpdatedAtDesc(UUID providerAccountId);
}
