package com.deepthoughtnet.clinic.carepilot.webinar.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Registration projection for attendee operations. */
public record WebinarRegistrationRecord(
        UUID id,
        UUID tenantId,
        UUID webinarId,
        UUID patientId,
        UUID leadId,
        String attendeeName,
        String attendeeEmail,
        String attendeePhone,
        WebinarRegistrationStatus registrationStatus,
        boolean attended,
        OffsetDateTime attendedAt,
        WebinarRegistrationSource source,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
