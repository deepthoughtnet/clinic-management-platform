package com.deepthoughtnet.clinic.carepilot.analytics.service.model;

/**
 * Provider-level failure aggregate for operational diagnostics.
 */
public record ProviderFailureSummaryRecord(
        String providerName,
        long failureCount
) {}
