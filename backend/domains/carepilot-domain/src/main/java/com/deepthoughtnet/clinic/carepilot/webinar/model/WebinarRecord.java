package com.deepthoughtnet.clinic.carepilot.webinar.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Webinar aggregate projection used across APIs and services. */
public record WebinarRecord(
        UUID id,
        UUID tenantId,
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
        boolean registrationEnabled,
        boolean reminderEnabled,
        boolean followupEnabled,
        String tags,
        UUID createdBy,
        UUID updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
