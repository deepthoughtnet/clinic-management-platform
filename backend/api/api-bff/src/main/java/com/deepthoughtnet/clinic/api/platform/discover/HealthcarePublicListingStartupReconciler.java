package com.deepthoughtnet.clinic.api.platform.discover;

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

    public HealthcarePublicListingStartupReconciler(
            HealthcarePublicListingSyncService syncService,
            TenantRepository tenantRepository
    ) {
        this.syncService = syncService;
        this.tenantRepository = tenantRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        tenantRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(tenant -> "ACTIVE".equalsIgnoreCase(tenant.getStatus()))
                .forEach(tenant -> {
                    var summary = syncService.syncTenant(tenant.getId(), null, "startup.reconcile");
                    log.info(
                            "Startup healthcare public listing reconcile finished. tenantId={} inserted={} updated={} skipped={} failed={}",
                            tenant.getId(),
                            summary.inserted(),
                            summary.updated(),
                            summary.skipped(),
                            summary.failed()
                    );
                });
    }
}
