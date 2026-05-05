package com.deepthoughtnet.clinic.identity.service.provisioning;

import java.util.UUID;

public record TenantProvisioningResult(
        UUID tenantId,
        String tenantCode,
        String planId,
        String adminEmail,
        String keycloakUserId,
        UUID appUserId
) {}
