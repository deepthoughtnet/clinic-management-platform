package com.deepthoughtnet.clinic.carepilot.ai_call.service.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Request for creating one AI call execution trigger. */
public record AiCallTriggerCommand(
        UUID patientId,
        UUID leadId,
        String phoneNumber,
        String script,
        OffsetDateTime scheduledAt
) {}
