package com.deepthoughtnet.clinic.carepilot.webinar.model;

import java.time.OffsetDateTime;

/** Create/update command for webinar management. */
public record WebinarUpsertCommand(
        String title,
        String description,
        WebinarType webinarType,
        WebinarStatus status,
        String webinarUrl,
        String organizerName,
        String organizerEmail,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime scheduledEndAt,
        String timezone,
        Integer capacity,
        Boolean registrationEnabled,
        Boolean reminderEnabled,
        Boolean followupEnabled,
        String tags
) {}
