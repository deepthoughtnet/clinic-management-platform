package com.deepthoughtnet.clinic.api.platform.discover;

import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileProjectionRepairService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ProviderPublicProfileProjectionStartupReconciler {
    private static final Logger log = LoggerFactory.getLogger(ProviderPublicProfileProjectionStartupReconciler.class);

    private final ProviderPublicProfileProjectionRepairService repairService;

    public ProviderPublicProfileProjectionStartupReconciler(ProviderPublicProfileProjectionRepairService repairService) {
        this.repairService = repairService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        var summary = repairService.reconcileHistoricalProviderOwnedProfiles();
        log.info(
                "Startup provider public profile projection repair finished. inspected={} repaired={} skipped={} activeReviewSkipped={} conflicted={}",
                summary.inspected(),
                summary.repaired(),
                summary.skipped(),
                summary.activeReviewSkipped(),
                summary.conflicted()
        );
        if (!summary.outcomes().isEmpty()) {
            summary.outcomes().stream()
                    .filter(outcome -> outcome.conflict() || outcome.repaired() || outcome.activeReviewSkipped())
                    .forEach(outcome -> log.info(
                            "PROVIDER_PROFILE_REPAIR_RESULT applicationId={} providerAccountId={} providerReference={} draftCreated={} ownershipCreated={} ownershipUpdated={} membershipCreated={} membershipUpdated={} activeReviewSkipped={} conflict={} message={}",
                            outcome.providerApplicationId(),
                            outcome.providerAccountId(),
                            outcome.providerReference(),
                            outcome.draftCreated(),
                            outcome.ownershipCreated(),
                            outcome.ownershipUpdated(),
                            outcome.membershipCreated(),
                            outcome.membershipUpdated(),
                            outcome.activeReviewSkipped(),
                            outcome.conflict(),
                            outcome.message()
                    ));
        }
    }
}
