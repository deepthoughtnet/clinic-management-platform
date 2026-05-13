package com.deepthoughtnet.clinic.carepilot.lead.analytics;

import java.util.Map;

/** Aggregated operational lead metrics for dashboard and ops surfaces. */
public record LeadAnalyticsRecord(
        long totalLeads,
        long newLeads,
        long qualifiedLeads,
        long convertedLeads,
        long lostLeads,
        long followUpsDue,
        long followUpsDueToday,
        long overdueFollowUps,
        double conversionRate,
        Map<String, Long> sourceBreakdown,
        long staleLeads,
        long highPriorityActiveLeads,
        long conversionsWithAppointment,
        Double avgHoursToConversion
) {}
