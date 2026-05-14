package com.deepthoughtnet.clinic.carepilot.ai_call.service.model;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallType;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Request for creating a one-off manual AI call execution. */
public record AiCallManualCallCommand(
        UUID patientId,
        UUID leadId,
        String phoneNumber,
        UUID templateId,
        AiCallType callType,
        String script,
        OffsetDateTime scheduledAt
) {}
