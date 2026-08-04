package com.deepthoughtnet.clinic.discover.providerownership.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicProfileClaimIntentRepository extends JpaRepository<PublicProfileClaimIntentEntity, UUID> {
    Optional<PublicProfileClaimIntentEntity> findByConnectionReference(String connectionReference);
    Optional<PublicProfileClaimIntentEntity> findTopByConnectionReferenceAndProviderAccountIdOrderByUpdatedAtDesc(String connectionReference, UUID providerAccountId);
    List<PublicProfileClaimIntentEntity> findByPublicProfileReferenceOrderByUpdatedAtDesc(String publicProfileReference);
    List<PublicProfileClaimIntentEntity> findByProviderAccountIdOrderByUpdatedAtDesc(UUID providerAccountId);

    @Query("""
            select claim
            from PublicProfileClaimIntentEntity claim
            where claim.publicProfileReference = :publicProfileReference
              and claim.status in (
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CREATED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.OPENED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.PROVIDER_AUTHENTICATED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CLAIM_SUBMITTED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CONSUMED
              )
              and claim.expiresAt > current_timestamp
            order by claim.updatedAt desc
            """)
    List<PublicProfileClaimIntentEntity> findActiveByPublicProfileReferenceOrderByUpdatedAtDesc(@Param("publicProfileReference") String publicProfileReference);

    @Query("""
            select case when count(claim) > 0 then true else false end
            from PublicProfileClaimIntentEntity claim
            where claim.publicProfileReference = :publicProfileReference
              and claim.status in (
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CREATED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.OPENED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.PROVIDER_AUTHENTICATED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CLAIM_SUBMITTED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CONSUMED
              )
              and claim.expiresAt > current_timestamp
            """)
    boolean existsActiveByPublicProfileReference(@Param("publicProfileReference") String publicProfileReference);

    @Query("""
            select claim
            from PublicProfileClaimIntentEntity claim
            where claim.publicProfileReference = :publicProfileReference
              and claim.providerAccountId = :providerAccountId
              and claim.status in (
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CREATED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.OPENED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.PROVIDER_AUTHENTICATED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CLAIM_SUBMITTED,
                  com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CONSUMED
              )
              and claim.expiresAt > current_timestamp
            order by claim.updatedAt desc
            """)
    List<PublicProfileClaimIntentEntity> findActiveByProviderAccountIdAndPublicProfileReferenceOrderByUpdatedAtDesc(@Param("providerAccountId") UUID providerAccountId, @Param("publicProfileReference") String publicProfileReference);
}
