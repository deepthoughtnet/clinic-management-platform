package com.deepthoughtnet.clinic.discover.providerownership;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipReconciliationRecord;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileMembershipRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileOwnershipRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderOwnershipReconciliationService {
    private final ProviderOwnershipLifecyclePolicy lifecyclePolicy;
    private final PublicProfileClaimIntentRepository claimIntentRepository;
    private final PublicProfileOwnershipRepository ownershipRepository;
    private final PublicProfileMembershipRepository membershipRepository;

    public ProviderOwnershipReconciliationService(
            ProviderOwnershipLifecyclePolicy lifecyclePolicy,
            PublicProfileClaimIntentRepository claimIntentRepository,
            PublicProfileOwnershipRepository ownershipRepository,
            PublicProfileMembershipRepository membershipRepository
    ) {
        this.lifecyclePolicy = lifecyclePolicy;
        this.claimIntentRepository = claimIntentRepository;
        this.ownershipRepository = ownershipRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public OwnershipReconciliationRecord summarize(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return new OwnershipReconciliationRecord(null, 0L, 0L, null, "UNCLAIMED", null, 0L, "NO_ACTION");
        }
        String normalizedReference = publicProfileReference.trim();
        List<com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentEntity> claims = claimIntentRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(normalizedReference);
        long activeClaimCount = claims.stream().filter(lifecyclePolicy::isActiveClaimIntent).count();
        String currentClaimReference = claims.stream().findFirst().map(com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentEntity::getConnectionReference).orElse(null);
        String ownershipStatus = ownershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(normalizedReference).stream()
                .findFirst()
                .map(entity -> entity.getStatus().name())
                .orElse("UNCLAIMED");
        String currentMembershipStatus = membershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(normalizedReference).stream()
                .filter(entity -> entity.getRole() == com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole.OWNER)
                .findFirst()
                .map(entity -> entity.getRole().name() + ":" + entity.getStatus())
                .orElse(null);
        String recommendedAction = recommendedAction(ownershipStatus, activeClaimCount);
        return new OwnershipReconciliationRecord(
                normalizedReference,
                claims.size(),
                activeClaimCount,
                currentClaimReference,
                ownershipStatus,
                currentMembershipStatus,
                Math.max(0L, activeClaimCount - 1L),
                recommendedAction
        );
    }

    private String recommendedAction(String ownershipStatus, long activeClaimCount) {
        if ("VERIFIED".equals(ownershipStatus)) {
            return "NO_ACTION";
        }
        if (activeClaimCount > 1) {
            return "MARK_OLDER_CLAIMS_SUPERSEDED";
        }
        if ("CLAIM_PENDING".equals(ownershipStatus)) {
            return "KEEP_ACTIVE_CLAIM";
        }
        return "REVIEW_HISTORY";
    }
}
