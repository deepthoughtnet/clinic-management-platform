package com.deepthoughtnet.clinic.carepilot.lead.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Narrow mutation for status pipeline transitions and follow-up updates. */
public record LeadStatusUpdateCommand(
        LeadStatus status,
        LeadPriority priority,
        UUID assignedToAppUserId,
        OffsetDateTime lastContactedAt,
        OffsetDateTime nextFollowUpAt,
        String comment
) {}
