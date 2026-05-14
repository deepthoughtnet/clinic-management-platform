package com.deepthoughtnet.clinic.carepilot.ai_call.analytics;

/** Aggregated AI call operational metrics. */
public record AiCallAnalyticsRecord(
        long totalCalls,
        long completedCalls,
        long failedCalls,
        long escalations,
        double noAnswerRate,
        double averageDurationSeconds,
        double retryRate,
        long queuedCalls,
        long suppressedCalls,
        long skippedCalls
) {}
