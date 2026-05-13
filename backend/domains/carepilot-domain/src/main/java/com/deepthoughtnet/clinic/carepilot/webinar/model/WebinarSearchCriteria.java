package com.deepthoughtnet.clinic.carepilot.webinar.model;

import java.time.LocalDate;

/** Filter criteria for webinar listing. */
public record WebinarSearchCriteria(
        WebinarStatus status,
        WebinarType webinarType,
        LocalDate scheduledFrom,
        LocalDate scheduledTo,
        Boolean upcoming,
        Boolean completed
) {}
