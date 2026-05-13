package com.deepthoughtnet.clinic.carepilot.lead.activity.model;

import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Timeline row projection for one lead lifecycle event. */
public record LeadActivityRecord(
        UUID id,
        UUID tenantId,
        UUID leadId,
        LeadActivityType activityType,
        String title,
        String description,
        LeadStatus oldStatus,
        LeadStatus newStatus,
        String relatedEntityType,
        UUID relatedEntityId,
        UUID createdByAppUserId,
        OffsetDateTime createdAt
) {}
