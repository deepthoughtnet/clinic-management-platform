package com.deepthoughtnet.clinic.carepilot.webinar.analytics;

import java.util.Map;

/** Summary metrics for webinar operational analytics. */
public record WebinarAnalyticsRecord(
        long totalWebinars,
        long upcomingWebinars,
        long completedWebinars,
        long totalRegistrations,
        long attendedCount,
        long noShowCount,
        double attendanceRate,
        double noShowRate,
        long webinarConversions,
        Map<String, Long> registrationsBySource,
        long attendeeEngagementCount
) {}
