package com.deepthoughtnet.clinic.carepilot.ai_call.service.model;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallType;
import java.time.LocalDate;
import java.util.UUID;

/** Filter criteria for listing AI call executions. */
public record AiCallExecutionSearchCriteria(
        AiCallExecutionStatus status,
        AiCallType callType,
        UUID patientId,
        UUID leadId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean escalationRequired,
        String provider,
        UUID campaignId
) {}
