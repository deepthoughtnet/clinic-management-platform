package com.deepthoughtnet.clinic.carepilot.lead.model;

import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Create/update payload for leads. */
public record LeadUpsertCommand(
        String firstName,
        String lastName,
        String phone,
        String email,
        PatientGender gender,
        LocalDate dateOfBirth,
        LeadSource source,
        String sourceDetails,
        UUID campaignId,
        UUID assignedToAppUserId,
        LeadStatus status,
        LeadPriority priority,
        String notes,
        String tags,
        OffsetDateTime lastContactedAt,
        OffsetDateTime nextFollowUpAt
) {}
