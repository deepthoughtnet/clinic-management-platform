package com.deepthoughtnet.clinic.carepilot.ai_call.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for one AI call execution. */
public record AiCallExecutionRecord(
        UUID id,
        UUID tenantId,
        UUID campaignId,
        UUID patientId,
        UUID leadId,
        String phoneNumber,
        AiCallExecutionStatus executionStatus,
        String providerName,
        String providerCallId,
        OffsetDateTime scheduledAt,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        int retryCount,
        OffsetDateTime nextRetryAt,
        OffsetDateTime lastAttemptAt,
        String failureReason,
        String suppressionReason,
        boolean escalationRequired,
        String escalationReason,
        boolean failoverAttempted,
        String failoverReason,
        UUID transcriptId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
