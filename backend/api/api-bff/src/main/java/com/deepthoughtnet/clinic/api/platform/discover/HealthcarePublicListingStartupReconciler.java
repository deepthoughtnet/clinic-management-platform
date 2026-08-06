package com.deepthoughtnet.clinic.api.platform.discover;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class HealthcarePublicListingStartupReconciler {
    private static final Logger log = LoggerFactory.getLogger(HealthcarePublicListingStartupReconciler.class);

    private final HealthcarePublicListingSyncService syncService;
    private final TenantRepository tenantRepository;
    private final ProviderPublicProfileModerationService moderationService;

    public HealthcarePublicListingStartupReconciler(
            HealthcarePublicListingSyncService syncService,
            TenantRepository tenantRepository,
            ProviderPublicProfileModerationService moderationService
    ) {
        this.syncService = syncService;
        this.tenantRepository = tenantRepository;
        this.moderationService = moderationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        int reconciledPublications = moderationService.reconcileCurrentPublishedLifecycles();
        log.info("Startup publication lifecycle reconcile finished. reconciled={}", reconciledPublications);
        tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(tenant -> "ACTIVE".equalsIgnoreCase(tenant.getStatus()))
                .forEach(tenant -> {
                    try {
                        var summary = syncService.syncTenant(tenant.getId(), null, "startup.reconcile");
                        log.info(
                                "Startup healthcare public listing reconcile finished. tenantId={} inserted={} updated={} skipped={} failed={}",
                                tenant.getId(),
                                summary.inserted(),
                                summary.updated(),
                                summary.skipped(),
                                summary.failed()
                        );
                    } catch (ProviderOwnershipConflictException conflict) {
                        log.warn(
                                "Startup healthcare public listing reconcile skipped for tenantId={} due to ownership conflict: {}",
                                tenant.getId(),
                                conflict.getMessage()
                        );
                    }
                });
    }
}
