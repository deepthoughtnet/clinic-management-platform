package com.deepthoughtnet.clinic.carepilot.engagement.scoring;

import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementLevel;

/**
 * Deterministic score calculator for Patient Engagement Engine V1.
 */
public class RuleBasedEngagementScorer {

    /** Returns bounded 0-100 score from positive and negative engagement signals. */
    public int score(ScoringSignals signals, EngagementScoringConfig config) {
        int score = 100;
        if (signals.inactiveDays() >= config.inactiveDays()) {
            score -= 30;
        } else if (signals.inactiveDays() >= Math.max(30, config.inactiveDays() / 2)) {
            score -= 10;
        }
        if (signals.noShowCount() >= config.highRiskNoShowCount()) {
            score -= 20;
        } else if (signals.noShowCount() > 0) {
            score -= 10;
        }
        if (signals.overdueBillsCount() > 0) {
            score -= 20;
        }
        if (signals.overdueVaccinationsCount() > 0) {
            score -= 10;
        }
        if (signals.pendingRefillCount() > 0) {
            score -= 15;
        }
        if (signals.followUpMissedCount() > 0) {
            score -= 15;
        }
        if (signals.completedAppointmentsCount() >= 3) {
            score += 5;
        }
        if (signals.recentVisitDays() >= 0 && signals.recentVisitDays() <= 30) {
            score += 10;
        }
        if (signals.overdueBillsCount() == 0 && signals.pendingRefillCount() == 0 && signals.overdueVaccinationsCount() == 0) {
            score += 5;
        }
        if (score < 0) {
            return 0;
        }
        return Math.min(100, score);
    }

    /** Maps numeric score into engagement level bands. */
    public EngagementLevel level(int score) {
        if (score >= 80) {
            return EngagementLevel.HIGH;
        }
        if (score >= 50) {
            return EngagementLevel.MEDIUM;
        }
        if (score >= 25) {
            return EngagementLevel.LOW;
        }
        return EngagementLevel.CRITICAL;
    }

    /** Lightweight signal bundle for score computation. */
    public record ScoringSignals(
            int inactiveDays,
            int recentVisitDays,
            int noShowCount,
            int completedAppointmentsCount,
            int overdueBillsCount,
            int overdueVaccinationsCount,
            int pendingRefillCount,
            int followUpMissedCount
    ) {}
}
