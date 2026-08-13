package com.deepthoughtnet.clinic.discover.publicprofile;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRepairRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationSubmissionRecord;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderPublicProfileProjectionRepairService {
    private final ProviderApplicationRepository applications;
    private final DiscoverProviderAccountRepository providerAccounts;
    private final ProviderPublicProfileService publicProfileService;
    private final ProviderOwnershipService ownershipService;
    private final ProviderPublicProfileModerationService moderationService;
    private final ProviderPublicProfileDraftService draftService;

    public ProviderPublicProfileProjectionRepairService(
            ProviderApplicationRepository applications,
            DiscoverProviderAccountRepository providerAccounts,
            ProviderPublicProfileService publicProfileService,
            ProviderOwnershipService ownershipService,
            ProviderPublicProfileModerationService moderationService,
            ProviderPublicProfileDraftService draftService
    ) {
        this.applications = applications;
        this.providerAccounts = providerAccounts;
        this.publicProfileService = publicProfileService;
        this.ownershipService = ownershipService;
        this.moderationService = moderationService;
        this.draftService = draftService;
    }

    @Transactional
    public HistoricalProjectionRepairSummary reconcileHistoricalProviderOwnedProfiles() {
        List<HistoricalProjectionRepairOutcome> outcomes = new ArrayList<>();
        for (ProviderApplicationEntity application : applications.findByProviderAccountIdIsNotNullOrderByUpdatedAtDesc()) {
            if (application.getStatus() != ProviderLifecycleStatus.PUBLISHED) {
                outcomes.add(HistoricalProjectionRepairOutcome.skipped(
                        application.getId(),
                        application.getProviderAccountId(),
                        application.getReferenceNumber(),
                        "provider application is not published"
                ));
                continue;
            }
            outcomes.add(repairProviderApplication(application.getId()));
        }
        return HistoricalProjectionRepairSummary.from(outcomes);
    }

    @Transactional
    public HistoricalProjectionRepairOutcome repairProviderApplication(UUID applicationId) {
        if (applicationId == null) {
            return HistoricalProjectionRepairOutcome.skipped(null, null, null, "application id is required");
        }
        ProviderApplicationEntity application = applications.findById(applicationId).orElse(null);
        if (application == null) {
            return HistoricalProjectionRepairOutcome.skipped(applicationId, null, null, "provider application not found");
        }
        if (application.getProviderAccountId() == null) {
            return HistoricalProjectionRepairOutcome.skipped(application.getId(), null, application.getReferenceNumber(), "provider application is not owned");
        }
        if (providerAccounts.findById(application.getProviderAccountId()).isEmpty()) {
            return HistoricalProjectionRepairOutcome.skipped(application.getId(), application.getProviderAccountId(), application.getReferenceNumber(), "provider account not found");
        }

        PublicProfileLifecycleRecord lifecycle = publicProfileService.findLifecycleByProviderId(application.getId()).orElse(null);
        if (lifecycle == null) {
            return HistoricalProjectionRepairOutcome.skipped(application.getId(), application.getProviderAccountId(), application.getReferenceNumber(), "public profile lifecycle not found");
        }
        if (lifecycle.providerType() != application.getProviderType()) {
            return HistoricalProjectionRepairOutcome.conflict(
                    application.getId(),
                    application.getProviderAccountId(),
                    application.getReferenceNumber(),
                    "public profile type does not match the provider application"
            );
        }
        if (!"PUBLISHED".equalsIgnoreCase(lifecycle.publicationStatus())) {
            return HistoricalProjectionRepairOutcome.skipped(application.getId(), application.getProviderAccountId(), application.getReferenceNumber(), "public profile is not published");
        }

        OwnershipRepairRecord ownershipRepair = ownershipService.ensureHistoricalVerifiedOwnership(application, lifecycle, "Historical provider profile projection repair");
        if (ownershipRepair.conflict()) {
            return HistoricalProjectionRepairOutcome.conflict(
                    application.getId(),
                    application.getProviderAccountId(),
                    application.getReferenceNumber(),
                    ownershipRepair.conflictReason()
            );
        }

        String publicProfileReference = lifecycle.providerId().toString();
        PublicProfileModerationSubmissionRecord currentSubmission = moderationService.currentSubmission(publicProfileReference).orElse(null);
        boolean activeReview = isActiveReview(currentSubmission);
        PublicProfileDraftWorkspaceRecord existingDraft = draftService.findDraft(publicProfileReference).orElse(null);
        if (existingDraft != null) {
            if (!application.getProviderAccountId().equals(existingDraft.providerAccountId())) {
                return HistoricalProjectionRepairOutcome.conflict(
                        application.getId(),
                        application.getProviderAccountId(),
                        application.getReferenceNumber(),
                        "public profile draft belongs to another provider account"
                );
            }
            if (existingDraft.publicProfileType() != application.getProviderType()) {
                return HistoricalProjectionRepairOutcome.conflict(
                        application.getId(),
                        application.getProviderAccountId(),
                        application.getReferenceNumber(),
                        "public profile draft type does not match the provider application"
                );
            }
        }

        boolean draftCreated = false;
        boolean activeReviewSkipped = false;
        if (existingDraft == null) {
            if (activeReview) {
                activeReviewSkipped = true;
            } else {
                draftService.createOrLoadDraft(application.getProviderAccountId(), publicProfileReference);
                draftCreated = true;
            }
        } else if (activeReview) {
            activeReviewSkipped = true;
        }
        boolean ownershipChanged = ownershipRepair.ownershipCreated() || ownershipRepair.ownershipUpdated();
        boolean membershipChanged = ownershipRepair.membershipCreated() || ownershipRepair.membershipUpdated();
        boolean repaired = draftCreated || ownershipChanged || membershipChanged;
        return new HistoricalProjectionRepairOutcome(
                application.getId(),
                application.getProviderAccountId(),
                application.getReferenceNumber(),
                draftCreated,
                ownershipRepair.ownershipCreated(),
                ownershipRepair.ownershipUpdated(),
                ownershipRepair.membershipCreated(),
                ownershipRepair.membershipUpdated(),
                activeReviewSkipped,
                false,
                repaired
                        ? (activeReviewSkipped
                            ? "Historical provider profile projections repaired; draft bootstrap skipped because a submission is under review"
                            : "Historical provider profile projections repaired")
                        : (activeReviewSkipped
                            ? "Historical provider profile projections already consistent; active submission left untouched"
                            : "Historical provider profile projections already consistent")
        );
    }

    public record HistoricalProjectionRepairOutcome(
            UUID providerApplicationId,
            UUID providerAccountId,
            String providerReference,
            boolean draftCreated,
            boolean ownershipCreated,
            boolean ownershipUpdated,
            boolean membershipCreated,
            boolean membershipUpdated,
            boolean activeReviewSkipped,
            boolean conflict,
            String message
    ) {
        static HistoricalProjectionRepairOutcome skipped(UUID providerApplicationId, UUID providerAccountId, String providerReference, String message) {
            return new HistoricalProjectionRepairOutcome(providerApplicationId, providerAccountId, providerReference, false, false, false, false, false, false, false, message);
        }

        static HistoricalProjectionRepairOutcome skippedActiveReview(UUID providerApplicationId, UUID providerAccountId, String providerReference, String message) {
            return new HistoricalProjectionRepairOutcome(providerApplicationId, providerAccountId, providerReference, false, false, false, false, false, true, false, message);
        }

        static HistoricalProjectionRepairOutcome conflict(UUID providerApplicationId, UUID providerAccountId, String providerReference, String message) {
            return new HistoricalProjectionRepairOutcome(providerApplicationId, providerAccountId, providerReference, false, false, false, false, false, false, true, message);
        }

        public boolean repaired() {
            return draftCreated || ownershipCreated || ownershipUpdated || membershipCreated || membershipUpdated;
        }
    }

    public record HistoricalProjectionRepairSummary(
            int inspected,
            int repaired,
            int skipped,
            int activeReviewSkipped,
            int conflicted,
            List<HistoricalProjectionRepairOutcome> outcomes
    ) {
        static HistoricalProjectionRepairSummary from(List<HistoricalProjectionRepairOutcome> outcomes) {
            int inspected = outcomes == null ? 0 : outcomes.size();
            int repaired = outcomes == null ? 0 : (int) outcomes.stream().filter(HistoricalProjectionRepairOutcome::repaired).count();
            int activeReviewSkipped = outcomes == null ? 0 : (int) outcomes.stream().filter(HistoricalProjectionRepairOutcome::activeReviewSkipped).count();
            int skipped = outcomes == null ? 0 : (int) outcomes.stream().filter(outcome -> !outcome.conflict() && !outcome.repaired() && !outcome.activeReviewSkipped()).count();
            int conflicted = outcomes == null ? 0 : (int) outcomes.stream().filter(HistoricalProjectionRepairOutcome::conflict).count();
            return new HistoricalProjectionRepairSummary(inspected, repaired, skipped, activeReviewSkipped, conflicted, outcomes == null ? List.of() : List.copyOf(outcomes));
        }
    }

    private boolean isActiveReview(PublicProfileModerationSubmissionRecord submission) {
        if (submission == null || submission.moderationStatus() == null) {
            return false;
        }
        return switch (submission.moderationStatus()) {
            case "SUBMITTED", "UNDER_REVIEW" -> true;
            default -> false;
        };
    }
}
