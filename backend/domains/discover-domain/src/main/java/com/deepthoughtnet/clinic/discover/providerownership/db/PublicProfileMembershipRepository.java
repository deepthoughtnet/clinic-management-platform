package com.deepthoughtnet.clinic.discover.providerownership.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicProfileMembershipRepository extends JpaRepository<PublicProfileMembershipEntity, UUID> {
    List<PublicProfileMembershipEntity> findByPublicProfileReferenceOrderByUpdatedAtDesc(String publicProfileReference);
    Optional<PublicProfileMembershipEntity> findByPublicProfileReferenceAndProviderAccountIdAndRole(String publicProfileReference, UUID providerAccountId, com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole role);
}
