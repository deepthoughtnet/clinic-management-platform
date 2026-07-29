package com.deepthoughtnet.clinic.discover.verification.db;

import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import com.deepthoughtnet.clinic.discover.verification.VerificationPurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverVerificationChallengeRepository extends JpaRepository<DiscoverVerificationChallengeEntity, UUID> {
    Optional<DiscoverVerificationChallengeEntity> findTopByPurposeAndChannelAndNormalizedRecipientOrderByCreatedAtDesc(
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient
    );

    Optional<DiscoverVerificationChallengeEntity> findTopByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdIsNullAndProviderAccountIdIsNullOrderByCreatedAtDesc(
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient
    );

    Optional<DiscoverVerificationChallengeEntity> findTopByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdOrderByCreatedAtDesc(
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient,
            UUID providerApplicationId
    );

    Optional<DiscoverVerificationChallengeEntity> findTopByPurposeAndChannelAndNormalizedRecipientAndProviderAccountIdOrderByCreatedAtDesc(
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient,
            UUID providerAccountId
    );

    List<DiscoverVerificationChallengeEntity> findByPurposeAndChannelAndNormalizedRecipientAndProviderApplicationIdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient,
            UUID providerApplicationId
    );

    List<DiscoverVerificationChallengeEntity> findByPurposeAndChannelAndNormalizedRecipientAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient
    );

    List<DiscoverVerificationChallengeEntity> findByPurposeAndChannelAndNormalizedRecipientAndProviderAccountIdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient,
            UUID providerAccountId
    );
}
