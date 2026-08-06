package com.deepthoughtnet.clinic.discover.providerownership;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.ClaimIntentRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.DisputeRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentEntity;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProviderOwnershipLifecyclePolicy {
    public boolean isActiveClaimIntent(ClaimIntentRecord claimIntent) {
        if (claimIntent == null) {
            return false;
        }
        if (claimIntent.expiresAt() != null && claimIntent.expiresAt().isBefore(java.time.OffsetDateTime.now())) {
            return false;
        }
        return isActiveClaimStatus(claimIntent.status());
    }

    public boolean isActiveClaimIntent(PublicProfileClaimIntentEntity claimIntent) {
        if (claimIntent == null) {
            return false;
        }
        if (claimIntent.getExpiresAt() != null && claimIntent.getExpiresAt().isBefore(java.time.OffsetDateTime.now())) {
            return false;
        }
        return isActiveClaimStatus(claimIntent.getStatus());
    }

    public boolean isActiveClaimStatus(PublicProfileClaimIntentStatus status) {
        if (status == null) {
            return false;
        }
        return switch (status) {
            case CREATED, OPENED, PROVIDER_AUTHENTICATED, CLAIM_SUBMITTED, CONSUMED -> true;
            case EXPIRED, REVOKED, REJECTED -> false;
        };
    }

    public boolean isTerminalClaimStatus(PublicProfileClaimIntentStatus status) {
        return status != null && !isActiveClaimStatus(status);
    }

    public String pageMode(ClaimIntentRecord intent, OwnershipRecord ownership) {
        if (ownership != null) {
            return switch (ownership.status()) {
                case VERIFIED -> "OWNERSHIP_VERIFIED";
                case CLAIM_PENDING -> "CLAIM_PENDING";
                case DISPUTED -> "CLAIM_DISPUTED";
                case REJECTED -> "CLAIM_REJECTED";
                case REVOKED -> "CLAIM_REVOKED";
                case TRANSFER_PENDING -> "CLAIM_PENDING";
                case UNCLAIMED -> intent == null ? "READ_ONLY_FALLBACK" : pageModeForClaim(intent);
            };
        }
        return pageModeForClaim(intent);
    }

    public String workItemStatus(ClaimIntentRecord intent, OwnershipRecord ownership) {
        if (ownership != null) {
            return switch (ownership.status()) {
                case VERIFIED -> "OWNERSHIP_VERIFIED";
                case CLAIM_PENDING, TRANSFER_PENDING -> "PLATFORM_REVIEW";
                case DISPUTED -> "DISPUTED";
                case REJECTED -> "REJECTED";
                case REVOKED -> "REVOKED";
                case UNCLAIMED -> intent == null ? "READ_ONLY_FALLBACK" : workItemStatusForClaim(intent);
            };
        }
        return workItemStatusForClaim(intent);
    }

    public String reviewStatus(ClaimIntentRecord intent, OwnershipRecord ownership) {
        String pageMode = pageMode(intent, ownership);
        return switch (pageMode) {
            case "OWNERSHIP_VERIFIED" -> "APPROVED";
            case "CLAIM_PENDING", "CLAIM_SUBMITTED" -> "PENDING_REVIEW";
            case "CLAIM_DISPUTED" -> "DISPUTED";
            case "CLAIM_REJECTED" -> "REJECTED";
            case "CLAIM_REVOKED" -> "REVOKED";
            case "CLAIM_EXPIRED" -> "EXPIRED";
            default -> intent == null ? "READ_ONLY_FALLBACK" : intent.status().name();
        };
    }

    public List<String> claimReviewAllowedActions(ClaimIntentRecord intent, OwnershipRecord ownership) {
        String pageMode = pageMode(intent, ownership);
        return switch (pageMode) {
            case "OWNERSHIP_VERIFIED" -> List.of("BACK_TO_DASHBOARD", "VIEW_OWNERSHIP");
            case "CLAIM_PENDING", "CLAIM_SUBMITTED", "PROVIDER_AUTHENTICATED", "CLAIM_INTENT_CREATED" -> List.of("BACK_TO_DASHBOARD", "SUBMIT_CLAIM");
            case "CLAIM_DISPUTED", "CLAIM_REJECTED", "CLAIM_REVOKED", "CLAIM_EXPIRED" -> List.of("BACK_TO_DASHBOARD");
            default -> List.of("BACK_TO_DASHBOARD");
        };
    }

    public List<String> workspaceAllowedActions(ClaimIntentRecord intent, OwnershipRecord ownership) {
        String pageMode = pageMode(intent, ownership);
        return switch (pageMode) {
            case "OWNERSHIP_VERIFIED" -> List.of("CREATE_PUBLIC_PROFILE_DRAFT", "VIEW_PREVIEW", "VIEW_READINESS", "OPEN_PUBLIC_PROFILE", "VIEW_DETAILS");
            case "CLAIM_PENDING", "CLAIM_SUBMITTED", "PROVIDER_AUTHENTICATED", "CLAIM_INTENT_CREATED" -> List.of("OPEN_CLAIM");
            case "CLAIM_DISPUTED", "CLAIM_REJECTED", "CLAIM_REVOKED", "CLAIM_EXPIRED" -> List.of("VIEW_DETAILS");
            default -> List.of();
        };
    }

    public List<String> presenceAllowedActions(OwnershipRecord ownership, ClaimIntentRecord activeClaimIntent) {
        if (ownership != null) {
            return switch (ownership.status()) {
                case VERIFIED -> List.of("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP");
                case CLAIM_PENDING, DISPUTED, REJECTED, REVOKED, TRANSFER_PENDING -> List.of("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP");
                case UNCLAIMED -> activeClaimIntent != null ? List.of("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP") : List.of("CONNECT_PROVIDER_ACCOUNT");
            };
        }
        if (isActiveClaimIntent(activeClaimIntent)) {
            return List.of("OPEN_PROVIDER_DASHBOARD", "VIEW_OWNERSHIP");
        }
        return List.of("CONNECT_PROVIDER_ACCOUNT");
    }

    public List<String> ownershipAllowedActions(OwnershipRecord ownership, List<DisputeRecord> disputes) {
        if (ownership == null) {
            return List.of();
        }
        return switch (ownership.status()) {
            case CLAIM_PENDING, TRANSFER_PENDING -> List.of("APPROVE_OWNERSHIP", "REJECT_OWNERSHIP", "DISPUTE_OWNERSHIP", "REVOKE_CLAIM", "VIEW_OWNERSHIP");
            case VERIFIED -> List.of("VIEW_OWNERSHIP", "DISPUTE_OWNERSHIP", "REVOKE_OWNERSHIP");
            case DISPUTED -> List.of("VIEW_OWNERSHIP");
            case REJECTED, REVOKED -> List.of("VIEW_OWNERSHIP");
            case UNCLAIMED -> List.of("VIEW_OWNERSHIP");
        };
    }

    public void validateClaimCreation(String publicProfileReference, OwnershipRecord ownership, ClaimIntentRecord activeClaimIntent) {
        if (ownership != null) {
            switch (ownership.status()) {
                case VERIFIED -> throw new ProviderOwnershipConflictException("ownership_already_verified", "Ownership is already verified for this profile.");
                case CLAIM_PENDING -> throw new ProviderOwnershipConflictException("active_claim_exists", "An active claim already exists for this profile.");
                case DISPUTED -> throw new ProviderOwnershipConflictException("ownership_dispute_active", "An ownership dispute is active for this profile.");
                default -> {
                }
            }
        }
        if (activeClaimIntent != null && isActiveClaimIntent(activeClaimIntent)) {
            throw new ProviderOwnershipConflictException("active_claim_exists", "An active claim already exists for this profile.");
        }
    }

    public void validateClaimAuthentication(ClaimIntentRecord claimIntent, java.util.UUID providerAccountId) {
        if (claimIntent == null) {
            throw new IllegalArgumentException("Claim intent not found.");
        }
        if (claimIntent.providerAccountId() != null && !claimIntent.providerAccountId().equals(providerAccountId)) {
            throw new ProviderOwnershipConflictException("claim_not_owned_by_provider", "Claim intent is not assigned to this provider account.");
        }
        if (claimIntent.status() == PublicProfileClaimIntentStatus.EXPIRED) {
            throw new ProviderOwnershipConflictException("claim_expired", "Claim intent has expired.");
        }
        if (claimIntent.status() == PublicProfileClaimIntentStatus.REJECTED || claimIntent.status() == PublicProfileClaimIntentStatus.REVOKED) {
            throw new ProviderOwnershipConflictException("claim_not_eligible_for_resubmission", "Claim intent is no longer available.");
        }
    }

    public void validateClaimSubmission(ClaimIntentRecord claimIntent, OwnershipRecord ownership, java.util.UUID providerAccountId) {
        validateClaimAuthentication(claimIntent, providerAccountId);
        if (ownership != null && ownership.status() == PublicProfileOwnershipStatus.VERIFIED) {
            throw new ProviderOwnershipConflictException("ownership_already_verified", "Ownership is already verified for this profile.");
        }
        if (ownership != null && ownership.status() == PublicProfileOwnershipStatus.DISPUTED) {
            throw new ProviderOwnershipConflictException("ownership_dispute_active", "An ownership dispute is active for this profile.");
        }
    }

    public void validateOwnershipTransition(PublicProfileOwnershipStatus currentStatus, String requestedAction) {
        if (currentStatus == null) {
            throw new ProviderOwnershipConflictException("invalid_ownership_transition", "Ownership state is unavailable.");
        }
        switch (requestedAction) {
            case "APPROVE" -> {
                if (currentStatus == PublicProfileOwnershipStatus.VERIFIED) {
                    return;
                }
                if (currentStatus != PublicProfileOwnershipStatus.CLAIM_PENDING) {
                    throw new ProviderOwnershipConflictException("invalid_ownership_transition", "Only a pending ownership can be approved.");
                }
            }
            case "REJECT" -> {
                if (currentStatus == PublicProfileOwnershipStatus.REJECTED) {
                    return;
                }
                if (currentStatus != PublicProfileOwnershipStatus.CLAIM_PENDING && currentStatus != PublicProfileOwnershipStatus.TRANSFER_PENDING) {
                    throw new ProviderOwnershipConflictException("invalid_ownership_transition", "Only a pending ownership can be rejected.");
                }
            }
            case "DISPUTE" -> {
                if (currentStatus == PublicProfileOwnershipStatus.DISPUTED) {
                    return;
                }
                if (currentStatus != PublicProfileOwnershipStatus.CLAIM_PENDING && currentStatus != PublicProfileOwnershipStatus.VERIFIED) {
                    throw new ProviderOwnershipConflictException("invalid_ownership_transition", "Only a pending or verified ownership can be disputed.");
                }
            }
            case "REVOKE" -> {
                if (currentStatus == PublicProfileOwnershipStatus.REVOKED) {
                    return;
                }
                if (currentStatus != PublicProfileOwnershipStatus.CLAIM_PENDING && currentStatus != PublicProfileOwnershipStatus.VERIFIED && currentStatus != PublicProfileOwnershipStatus.DISPUTED) {
                    throw new ProviderOwnershipConflictException("invalid_ownership_transition", "Only a pending, verified, or disputed ownership can be revoked.");
                }
            }
            default -> throw new ProviderOwnershipConflictException("invalid_ownership_transition", "Unsupported ownership transition.");
        }
    }

    private String pageModeForClaim(ClaimIntentRecord intent) {
        if (intent == null) {
            return "READ_ONLY_FALLBACK";
        }
        return switch (intent.status()) {
            case CREATED, OPENED -> "CLAIM_INTENT_CREATED";
            case PROVIDER_AUTHENTICATED -> "PROVIDER_AUTHENTICATED";
            case CLAIM_SUBMITTED, CONSUMED -> "CLAIM_SUBMITTED";
            case EXPIRED -> "CLAIM_EXPIRED";
            case REVOKED -> "CLAIM_REVOKED";
            case REJECTED -> "CLAIM_REJECTED";
        };
    }

    private String workItemStatusForClaim(ClaimIntentRecord intent) {
        if (intent == null) {
            return "READ_ONLY_FALLBACK";
        }
        return switch (intent.status()) {
            case CREATED, OPENED, PROVIDER_AUTHENTICATED, CLAIM_SUBMITTED, CONSUMED -> intent.status().name();
            case EXPIRED -> "EXPIRED";
            case REVOKED -> "REVOKED";
            case REJECTED -> "REJECTED";
        };
    }
}
