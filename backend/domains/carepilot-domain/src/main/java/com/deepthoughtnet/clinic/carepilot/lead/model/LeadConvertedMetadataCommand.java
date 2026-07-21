package com.deepthoughtnet.clinic.carepilot.lead.model;

import java.util.UUID;

/** Mutable marketing metadata for converted leads. */
public record LeadConvertedMetadataCommand(
        String notes,
        String tags,
        String sourceDetails,
        UUID campaignId,
        UUID assignedToAppUserId
) {}
