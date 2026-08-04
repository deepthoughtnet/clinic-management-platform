package com.deepthoughtnet.clinic.discover.providerownership;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.DisputeRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipSnapshot;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileDisputeEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileDisputeRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileMembershipEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileMembershipRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileOwnershipEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileOwnershipRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileDisputeStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderOwnershipService {
    private static final Duration CLAIM_INTENT_TTL = Duration.ofHours(12);

    private final ProviderOwnershipLifecyclePolicy lifecyclePolicy;
    private final ProviderPublicProfileService publicProfileService;
    private final DiscoverProviderAccountRepository providerAccountRepository;
    private final PublicProfileClaimIntentRepository claimIntentRepository;
    private final PublicProfileOwnershipRepository ownershipRepository;
    private final PublicProfileMembershipRepository membershipRepository;
    private final PublicProfileDisputeRepository disputeRepository;

    public ProviderOwnershipService(
            ProviderOwnershipLifecyclePolicy lifecyclePolicy,
            ProviderPublicProfileService publicProfileService,
            DiscoverProviderAccountRepository providerAccountRepository,
            PublicProfileClaimIntentRepository claimIntentRepository,
            PublicProfileOwnershipRepository ownershipRepository,
            PublicProfileMembershipRepository membershipRepository,
            PublicProfileDisputeRepository disputeRepository
    ) {
        this.lifecyclePolicy = lifecyclePolicy;
        this.publicProfileService = publicProfileService;
        this.providerAccountRepository = providerAccountRepository;
        this.claimIntentRepository = claimIntentRepository;
        this.ownershipRepository = ownershipRepository;
        this.membershipRepository = membershipRepository;
        this.disputeRepository = disputeRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ClaimIntentRecord> findClaimIntent(String connectionReference) {
        if (!StringUtils.hasText(connectionReference)) {
            return Optional.empty();
        }
        return claimIntentRepository.findByConnectionReference(connectionReference.trim()).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public List<ClaimIntentRecord> listClaimIntents(UUID providerAccountId) {
        if (providerAccountId == null) {
            return List.of();
        }
        return claimIntentRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PublicProfileClaimIntentEntity::getPublicProfileReference,
                        java.util.function.Function.identity(),
                        (current, replacement) -> current,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Transactional
    public ClaimIntentRecord createClinicClaimIntent(
            UUID tenantId,
            UUID issuerAppUserId,
            String reason
    ) {
        require(tenantId, "tenantId");
        return createOrReuseClaimIntent(
                UUID.randomUUID().toString(),
                PublicProfileType.CLINIC,
                tenantId.toString(),
                tenantId.toString(),
                issuerAppUserId,
                reason
        );
    }

    @Transactional
    public ClaimIntentRecord createDoctorClaimIntent(
            UUID tenantId,
            UUID doctorUserId,
            UUID issuerAppUserId,
            String reason
    ) {
        require(tenantId, "tenantId");
        require(doctorUserId, "doctorUserId");
        return createOrReuseClaimIntent(
                UUID.randomUUID().toString(),
                PublicProfileType.DOCTOR,
                doctorUserId.toString(),
                tenantId.toString(),
                issuerAppUserId,
                reason
        );
    }

    @Transactional
    public ClaimIntentRecord createClaimIntent(
            String connectionReference,
            PublicProfileType publicProfileType,
            String publicProfileReference,
            String tenantReference,
            UUID issuerAppUserId,
            String reason
    ) {
        return createOrReuseClaimIntent(connectionReference, publicProfileType, publicProfileReference, tenantReference, issuerAppUserId, reason);
    }

    @Transactional
    public ClaimIntentRecord openClaimIntent(String connectionReference) {
        PublicProfileClaimIntentEntity intent = requireIntent(connectionReference);
        ensureNotExpired(intent);
        if (intent.getStatus() == PublicProfileClaimIntentStatus.CREATED) {
            intent.open();
            claimIntentRepository.save(intent);
        }
        return toRecord(intent);
    }

    @Transactional
    public ClaimIntentRecord authenticateClaimIntent(String connectionReference, UUID providerAccountId) {
        PublicProfileClaimIntentEntity intent = requireIntent(connectionReference);
        ensureNotExpired(intent);
        requireProvider(providerAccountId);
        lifecyclePolicy.validateClaimAuthentication(toRecord(intent), providerAccountId);
        if (intent.getProviderAccountId() == null || !intent.getProviderAccountId().equals(providerAccountId) || intent.getStatus() == PublicProfileClaimIntentStatus.CREATED || intent.getStatus() == PublicProfileClaimIntentStatus.OPENED) {
            intent.authenticate(providerAccountId);
            claimIntentRepository.save(intent);
        }
        return toRecord(intent);
    }

    @Transactional
    public OwnershipSnapshot submitClaim(String connectionReference, UUID providerAccountId, UUID actorAppUserId, String evidenceSnapshotJson, String reason) {
        PublicProfileClaimIntentEntity intent = requireIntent(connectionReference);
        ensureNotExpired(intent);
        requireProvider(providerAccountId);
        OwnershipRecord ownershipRecord = findLatestOwnership(intent.getPublicProfileReference()).orElse(null);
        ClaimIntentRecord claimRecord = toRecord(intent);
        lifecyclePolicy.validateClaimSubmission(claimRecord, ownershipRecord, providerAccountId);
        if (intent.getProviderAccountId() == null || !intent.getProviderAccountId().equals(providerAccountId) || intent.getStatus() != PublicProfileClaimIntentStatus.CLAIM_SUBMITTED) {
            intent.submit(providerAccountId, evidenceSnapshotJson);
            claimIntentRepository.save(intent);
        }

        PublicProfileOwnershipEntity ownership = ownershipRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId).stream()
                .filter(item -> intent.getPublicProfileReference().equals(item.getPublicProfileReference()))
                .findFirst()
                .orElseGet(() -> ownershipRepository.save(PublicProfileOwnershipEntity.create(
                        intent.getPublicProfileReference(),
                        intent.getPublicProfileType(),
                        providerAccountId,
                        "HEALTHCARE_INITIATED_CONNECTION",
                        intent.getTenantReference(),
                        intent.getSourceRevision(),
                        reason
                )));
        if (ownership.getStatus() != PublicProfileOwnershipStatus.CLAIM_PENDING) {
            ownership.markClaimPending(reason);
        }
        ownership.recordEvidenceSnapshot(evidenceSnapshotJson);
        ownershipRepository.save(ownership);

        PublicProfileMembershipEntity membership = membershipRepository.findByPublicProfileReferenceAndProviderAccountIdAndRole(
                        intent.getPublicProfileReference(),
                        providerAccountId,
                        PublicProfileMembershipRole.OWNER
                )
                .orElseGet(() -> membershipRepository.save(PublicProfileMembershipEntity.create(
                        intent.getPublicProfileReference(),
                        providerAccountId,
                        PublicProfileMembershipRole.OWNER,
                        reason,
                        intent.getSourceRevision()
                )));
        if (!"ACTIVE".equals(membership.getStatus())) {
            membership.activate(reason);
            membershipRepository.save(membership);
        }

        return snapshot(ownership, reason, actorAppUserId, evidenceSnapshotJson, intent.getConnectionReference());
    }

    @Transactional
    public OwnershipRecord approveOwnership(UUID ownershipId, UUID actorAppUserId, String reason) {
        return approveOwnership(ownershipId, actorAppUserId, reason, null);
    }

    @Transactional
    public OwnershipRecord approveOwnership(UUID ownershipId, UUID actorAppUserId, String reason, Long expectedRowVersion) {
        PublicProfileOwnershipEntity ownership = ownershipRepository.findById(ownershipId)
                .orElseThrow(() -> new IllegalArgumentException("Ownership record not found."));
        if (expectedRowVersion != null && ownership.getRowVersion() != expectedRowVersion) {
            throw new ProviderOwnershipConflictException("stale_ownership_revision", "Ownership record has changed since it was loaded.");
        }
        lifecyclePolicy.validateOwnershipTransition(ownership.getStatus(), "APPROVE");
        if (ownership.getStatus() != PublicProfileOwnershipStatus.VERIFIED) {
            ownership.markVerified(reason);
            ownershipRepository.save(ownership);
        }
        membershipRepository.findByPublicProfileReferenceAndProviderAccountIdAndRole(
                        ownership.getPublicProfileReference(),
                        ownership.getProviderAccountId(),
                        PublicProfileMembershipRole.OWNER
                )
                .ifPresent(membership -> {
                    if (!"ACTIVE".equals(membership.getStatus())) {
                        membership.activate(reason);
                        membershipRepository.save(membership);
                    }
                });
        return toRecord(ownership);
    }

    @Transactional
    public OwnershipRecord rejectOwnership(UUID ownershipId, UUID actorAppUserId, String reason) {
        PublicProfileOwnershipEntity ownership = ownershipRepository.findById(ownershipId)
                .orElseThrow(() -> new IllegalArgumentException("Ownership record not found."));
        lifecyclePolicy.validateOwnershipTransition(ownership.getStatus(), "REJECT");
        if (ownership.getStatus() != PublicProfileOwnershipStatus.REJECTED) {
            ownership.markRejected(reason);
            ownershipRepository.save(ownership);
        }
        return toRecord(ownership);
    }

    @Transactional
    public OwnershipRecord markDisputed(UUID ownershipId, UUID actorAppUserId, String reason) {
        PublicProfileOwnershipEntity ownership = ownershipRepository.findById(ownershipId)
                .orElseThrow(() -> new IllegalArgumentException("Ownership record not found."));
        lifecyclePolicy.validateOwnershipTransition(ownership.getStatus(), "DISPUTE");
        if (ownership.getStatus() != PublicProfileOwnershipStatus.DISPUTED) {
            ownership.markDisputed(reason);
            ownershipRepository.save(ownership);
            PublicProfileDisputeEntity dispute = PublicProfileDisputeEntity.create(
                    ownership.getPublicProfileReference(),
                    ownership.getPublicProfileType(),
                    ownership.getId(),
                    null,
                    actorAppUserId,
                    reason
            );
            dispute.requestEvidence(reason);
            disputeRepository.save(dispute);
        }
        return toRecord(ownership);
    }

    @Transactional
    public OwnershipRecord revokeOwnership(UUID ownershipId, UUID actorAppUserId, String reason) {
        PublicProfileOwnershipEntity ownership = ownershipRepository.findById(ownershipId)
                .orElseThrow(() -> new IllegalArgumentException("Ownership record not found."));
        lifecyclePolicy.validateOwnershipTransition(ownership.getStatus(), "REVOKE");
        if (ownership.getStatus() != PublicProfileOwnershipStatus.REVOKED) {
            ownership.markRevoked(reason);
            ownershipRepository.save(ownership);
        }
        return toRecord(ownership);
    }

    @Transactional(readOnly = true)
    public List<OwnershipRecord> listOwnerships() {
        return ownershipRepository.findAll().stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<OwnershipRecord> listOwnerships(UUID providerAccountId) {
        if (providerAccountId == null) {
            return List.of();
        }
        return ownershipRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public Optional<OwnershipRecord> findLatestVerifiedOwnership(UUID providerAccountId) {
        if (providerAccountId == null) {
            return Optional.empty();
        }
        return ownershipRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId).stream()
                .filter(entity -> entity.getStatus() == PublicProfileOwnershipStatus.VERIFIED)
                .findFirst()
                .map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<OwnershipRecord> findOwnership(UUID providerAccountId, String publicProfileReference) {
        if (providerAccountId == null || !StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return ownershipRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId).stream()
                .filter(entity -> publicProfileReference.trim().equals(entity.getPublicProfileReference()))
                .findFirst()
                .map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public List<MembershipRecord> listMemberships(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return List.of();
        }
        return membershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(publicProfileReference.trim()).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public List<DisputeRecord> listDisputes(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return List.of();
        }
        return disputeRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(publicProfileReference.trim()).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public Optional<OwnershipRecord> findActiveOwnership(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return ownershipRepository.findTopByPublicProfileReferenceAndActiveTrueOrderByUpdatedAtDesc(publicProfileReference.trim()).map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<OwnershipRecord> findLatestOwnership(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return ownershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(publicProfileReference.trim()).stream()
                .findFirst()
                .map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<ClaimIntentRecord> findLatestClaimIntent(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return claimIntentRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(publicProfileReference.trim()).stream()
                .findFirst()
                .map(this::toRecord);
    }

    @Transactional(readOnly = true)
    public Optional<ClaimIntentRecord> findActiveClaimIntent(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return findActiveClaim(publicProfileReference);
    }

    @Transactional(readOnly = true)
    public String claimPageMode(ClaimIntentRecord intent, OwnershipRecord ownership) {
        return lifecyclePolicy.pageMode(intent, ownership);
    }

    @Transactional(readOnly = true)
    public String claimWorkItemStatus(ClaimIntentRecord intent, OwnershipRecord ownership) {
        return lifecyclePolicy.workItemStatus(intent, ownership);
    }

    @Transactional(readOnly = true)
    public String claimReviewStatus(ClaimIntentRecord intent, OwnershipRecord ownership) {
        return lifecyclePolicy.reviewStatus(intent, ownership);
    }

    @Transactional(readOnly = true)
    public List<String> claimReviewAllowedActions(ClaimIntentRecord intent, OwnershipRecord ownership) {
        return lifecyclePolicy.claimReviewAllowedActions(intent, ownership);
    }

    @Transactional(readOnly = true)
    public List<String> workspaceAllowedActions(ClaimIntentRecord intent, OwnershipRecord ownership) {
        return lifecyclePolicy.workspaceAllowedActions(intent, ownership);
    }

    @Transactional(readOnly = true)
    public List<String> presenceAllowedActions(OwnershipRecord ownership, ClaimIntentRecord activeClaimIntent) {
        return lifecyclePolicy.presenceAllowedActions(ownership, activeClaimIntent);
    }

    public List<String> ownershipAllowedActions(OwnershipRecord ownership, List<DisputeRecord> disputes) {
        return lifecyclePolicy.ownershipAllowedActions(ownership, disputes);
    }

    @Transactional(readOnly = true)
    public Optional<String> maskedProviderMobile(UUID providerAccountId) {
        if (providerAccountId == null) {
            return Optional.empty();
        }
        return providerAccountRepository.findById(providerAccountId)
                .map(DiscoverProviderAccountEntity::getNormalizedPhone)
                .filter(StringUtils::hasText)
                .map(this::maskPhone);
    }

    private OwnershipSnapshot snapshot(PublicProfileOwnershipEntity ownership, String reason, UUID actorAppUserId, String evidenceSnapshotJson, String claimReference) {
        List<MembershipRecord> memberships = listMemberships(ownership.getPublicProfileReference());
        List<DisputeRecord> disputes = listDisputes(ownership.getPublicProfileReference());
        return new OwnershipSnapshot(
                toRecord(ownership),
                memberships,
                disputes
        );
    }

    private ClaimIntentRecord toRecord(PublicProfileClaimIntentEntity entity) {
        return new ClaimIntentRecord(
                entity.getId(),
                entity.getConnectionReference(),
                entity.getPublicProfileType(),
                entity.getPublicProfileReference(),
                entity.getTenantReference(),
                entity.getProviderAccountId(),
                entity.getIssuerAppUserId(),
                entity.getSourceRevision(),
                entity.getStatus(),
                entity.getExpiresAt(),
                entity.getOpenedAt(),
                entity.getProviderAuthenticatedAt(),
                entity.getClaimSubmittedAt(),
                entity.getConsumedAt(),
                entity.getRevokedAt(),
                entity.getRejectedAt(),
                entity.getReason(),
                entity.getEvidenceSnapshotJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OwnershipRecord toRecord(PublicProfileOwnershipEntity entity) {
        return new OwnershipRecord(
                entity.getId(),
                entity.getPublicProfileReference(),
                entity.getPublicProfileType(),
                entity.getProviderAccountId(),
                entity.getStatus(),
                entity.getOwnershipMethod(),
                entity.getTenantReference(),
                entity.getSourceRevision(),
                entity.getVerifiedAt(),
                entity.getRevokedAt(),
                entity.getRejectionReason(),
                entity.getTransferTargetProviderAccountId(),
                entity.isActive(),
                entity.getReason(),
                entity.getEvidenceSnapshotJson(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private MembershipRecord toRecord(PublicProfileMembershipEntity entity) {
        return new MembershipRecord(
                entity.getId(),
                entity.getPublicProfileReference(),
                entity.getProviderAccountId(),
                entity.getRole(),
                entity.getStatus(),
                entity.getSourceRevision(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DisputeRecord toRecord(PublicProfileDisputeEntity entity) {
        return new DisputeRecord(
                entity.getId(),
                entity.getPublicProfileReference(),
                entity.getPublicProfileType(),
                entity.getOwnershipId(),
                entity.getClaimIntentReference(),
                entity.getDisputeStatus(),
                entity.getReason(),
                entity.getResolutionReason(),
                entity.getOpenedByAppUserId(),
                entity.getResolvedByAppUserId(),
                entity.getOpenedAt(),
                entity.getResolvedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private PublicProfileClaimIntentEntity requireIntent(String connectionReference) {
        if (!StringUtils.hasText(connectionReference)) {
            throw new IllegalArgumentException("connectionReference is required");
        }
        return claimIntentRepository.findByConnectionReference(connectionReference.trim())
                .orElseThrow(() -> new IllegalArgumentException("Claim intent not found."));
    }

    private void ensureNotExpired(PublicProfileClaimIntentEntity intent) {
        if (intent.getExpiresAt().isBefore(OffsetDateTime.now())) {
            intent.revoke("Claim intent expired");
            claimIntentRepository.save(intent);
            throw new ProviderOwnershipConflictException("claim_expired", "Claim intent has expired.");
        }
    }

    private void requireProvider(UUID providerAccountId) {
        if (providerAccountId == null) {
            throw new IllegalArgumentException("providerAccountId is required");
        }
        DiscoverProviderAccountEntity account = providerAccountRepository().findById(providerAccountId)
                .orElseThrow(() -> new IllegalArgumentException("provider account not found"));
        if (account.getStatus() == null || !"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new IllegalArgumentException("provider account is not active");
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String maskPhone(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return digits;
        }
        return "*".repeat(Math.max(0, digits.length() - 4)) + digits.substring(digits.length() - 4);
    }

    private DiscoverProviderAccountRepository providerAccountRepository() {
        return providerAccountRepository;
    }

    private ClaimIntentRecord createOrReuseClaimIntent(
            String connectionReference,
            PublicProfileType publicProfileType,
            String publicProfileReference,
            String tenantReference,
            UUID issuerAppUserId,
            String reason
    ) {
        requireText(connectionReference, "connectionReference");
        require(publicProfileType, "publicProfileType");
        requireText(publicProfileReference, "publicProfileReference");
        requireText(tenantReference, "tenantReference");
        String normalizedProfileReference = publicProfileReference.trim();
        String normalizedTenantReference = tenantReference.trim();
        Optional<ClaimIntentRecord> existingByConnectionReference = findClaimIntent(connectionReference);
        if (existingByConnectionReference.isPresent()) {
            return existingByConnectionReference.get();
        }
        OwnershipRecord currentOwnership = findLatestOwnership(normalizedProfileReference).orElse(null);
        ClaimIntentRecord activeClaim = findActiveClaim(normalizedProfileReference).orElse(null);
        if (currentOwnership != null) {
            switch (currentOwnership.status()) {
                case VERIFIED -> throw new ProviderOwnershipConflictException("ownership_already_verified", "Ownership is already verified for this profile.");
                case DISPUTED -> throw new ProviderOwnershipConflictException("ownership_dispute_active", "An ownership dispute is active for this profile.");
                case CLAIM_PENDING -> {
                    if (activeClaim == null) {
                        throw new ProviderOwnershipConflictException("active_claim_exists", "An active claim already exists for this profile.");
                    }
                }
                default -> {
                }
            }
        }
        if (activeClaim != null) {
            return activeClaim;
        }
        PublicProfileClaimIntentEntity intent = PublicProfileClaimIntentEntity.create(
                connectionReference.trim(),
                publicProfileType,
                normalizedProfileReference,
                normalizedTenantReference,
                issuerAppUserId,
                0L,
                OffsetDateTime.now().plus(CLAIM_INTENT_TTL),
                reason
        );
        return toRecord(claimIntentRepository.save(intent));
    }

    private Optional<ClaimIntentRecord> findActiveClaim(String publicProfileReference) {
        if (!StringUtils.hasText(publicProfileReference)) {
            return Optional.empty();
        }
        return claimIntentRepository.findActiveByPublicProfileReferenceOrderByUpdatedAtDesc(publicProfileReference.trim()).stream()
                .findFirst()
                .map(this::toRecord);
    }
}
