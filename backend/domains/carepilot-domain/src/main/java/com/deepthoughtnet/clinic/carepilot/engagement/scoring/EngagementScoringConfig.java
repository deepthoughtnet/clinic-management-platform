package com.deepthoughtnet.clinic.carepilot.engagement.scoring;

/**
 * Thresholds and penalties used by the rule-based engagement scorer.
 */
public record EngagementScoringConfig(
        int inactiveDays,
        int highRiskNoShowCount,
        int overdueBillDays
) {
    /** Default thresholds for V1 engagement scoring. */
    public static EngagementScoringConfig defaults() {
        return new EngagementScoringConfig(90, 2, 3);
    }
}
