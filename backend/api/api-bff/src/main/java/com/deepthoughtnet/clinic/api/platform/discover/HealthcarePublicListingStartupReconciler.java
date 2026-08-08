package com.deepthoughtnet.clinic.api.platform.discover;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import java.util.UUID;
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
        int reconciledPublications;
        try {
            reconciledPublications = moderationService.reconcileCurrentPublishedLifecycles();
        } catch (RuntimeException ex) {
            Throwable root = rootCause(ex);
            log.error(
                    "STARTUP_PUBLICATION_RECONCILE_FAILED rootExceptionClass={} rootMessage={}",
                    root.getClass().getName(),
                    root.getMessage(),
                    ex
            );
            throw ex;
        }
        log.info("Startup publication lifecycle reconcile finished. reconciled={}", reconciledPublications);
        tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(tenant -> "ACTIVE".equalsIgnoreCase(tenant.getStatus()))
                .forEach(tenant -> {
                    UUID tenantId = tenant.getId();
                    log.info("START tenantId={}", tenantId);
                    try {
                        var summary = syncService.syncTenant(tenantId, null, "startup.reconcile");
                        log.info(
                                "Startup healthcare public listing reconcile finished. tenantId={} inserted={} updated={} skipped={} failed={}",
                                tenantId,
                                summary.inserted(),
                                summary.updated(),
                                summary.skipped(),
                                summary.failed()
                        );
                        log.info("DONE tenantId={}", tenantId);
                    } catch (ProviderOwnershipConflictException conflict) {
                        log.warn(
                                "Startup healthcare public listing reconcile skipped for tenantId={} due to ownership conflict: {}",
                                tenantId,
                                conflict.getMessage()
                        );
                    } catch (RuntimeException ex) {
                        Throwable root = rootCause(ex);
                        log.error(
                                "SYNC_FAILED tenantId={} rootExceptionClass={} rootMessage={}",
                                tenantId,
                                root.getClass().getName(),
                                root.getMessage(),
                                ex
                        );
                        throw ex;
                    }
                });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
