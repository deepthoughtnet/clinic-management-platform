package com.deepthoughtnet.clinic.carepilot.featureflag.service.model;

import java.util.UUID;

/** Response model representing tenant-level CarePilot flag status. */
public record FeatureFlagRecord(UUID tenantId, boolean carePilotEnabled, String source) {}
