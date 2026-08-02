package com.deepthoughtnet.clinic.api.platform.discover;

import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/discover/public-listings")
@PreAuthorize("@permissionChecker.hasPermission('discover.provider.application.publish')")
public class HealthcarePublicListingSyncController {
    private final HealthcarePublicListingSyncService syncService;

    public HealthcarePublicListingSyncController(HealthcarePublicListingSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/reconcile")
    public HealthcarePublicListingSyncResponse reconcile(@RequestParam UUID tenantId) {
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        HealthcarePublicListingSyncService.HealthcarePublicListingSyncSummary summary = syncService.syncTenant(tenantId, actorAppUserId, "platform.reconcile");
        return new HealthcarePublicListingSyncResponse(
                summary.inserted(),
                summary.updated(),
                summary.skipped(),
                summary.failed(),
                summary.outcomes().stream()
                        .map(outcome -> new HealthcarePublicListingSyncItemResponse(
                                outcome.sourceSystem(),
                                outcome.sourceReference() == null ? null : outcome.sourceReference().toString(),
                                outcome.slug(),
                                outcome.bookingMode(),
                                outcome.inserted(),
                                outcome.updated(),
                                outcome.skipped(),
                                outcome.failed(),
                                outcome.message()
                        ))
                        .toList()
        );
    }

    public record HealthcarePublicListingSyncResponse(
            int inserted,
            int updated,
            int skipped,
            int failed,
            List<HealthcarePublicListingSyncItemResponse> items
    ) {
    }

    public record HealthcarePublicListingSyncItemResponse(
            String sourceSystem,
            String sourceReference,
            String slug,
            String bookingMode,
            int inserted,
            int updated,
            int skipped,
            int failed,
            String message
    ) {
    }
}
