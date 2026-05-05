package com.deepthoughtnet.clinic.identity.service.provisioning;

public record TenantProvisioningRequest(
        String tenantCode,
        String tenantName,
        String planId,              // TRIAL/BASIC/PRO/ENTERPRISE
        String adminEmail,
        String adminDisplayName,
        String tempPassword         // optional
) {}
