package com.deepthoughtnet.clinic.carepilot.lead.model;

import java.time.LocalDate;
import java.util.UUID;

/** Filter criteria for lead list and operational tabs. */
public record LeadSearchCriteria(
        LeadStatus status,
        LeadSource source,
        UUID assignedToAppUserId,
        LeadPriority priority,
        String search,
        boolean followUpDueOnly,
        LocalDate createdFrom,
        LocalDate createdTo
) {}
