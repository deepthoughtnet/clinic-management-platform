package com.deepthoughtnet.clinic.api.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for Administration integrations status view.
 */
public final class AdminIntegrationsDtos {
    private AdminIntegrationsDtos() {
    }

    public enum IntegrationStatus {
        READY,
        DISABLED,
        NOT_CONFIGURED,
        ERROR,
        FUTURE
    }

    public record IntegrationStatusRow(
            String key,
            String name,
            String category,
            IntegrationStatus status,
            boolean enabled,
            boolean configured,
            String providerName,
            List<String> missingConfigurationKeys,
            List<String> safeConfigurationHints,
            String message,
            OffsetDateTime lastCheckedAt,
            boolean supportsTestAction
    ) {
    }

    public record IntegrationStatusResponse(
            List<IntegrationStatusRow> rows
    ) {
    }
}
