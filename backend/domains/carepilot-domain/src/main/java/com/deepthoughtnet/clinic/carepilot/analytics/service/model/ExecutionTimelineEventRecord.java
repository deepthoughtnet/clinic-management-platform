package com.deepthoughtnet.clinic.carepilot.analytics.service.model;

import java.time.OffsetDateTime;

/**
 * Lightweight timeline event for execution lifecycle and retry visibility.
 */
public record ExecutionTimelineEventRecord(
        String type,
        String status,
        String detail,
        OffsetDateTime at
) {}
