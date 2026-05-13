package com.deepthoughtnet.clinic.carepilot.engagement.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementLevel;
import org.junit.jupiter.api.Test;

class RuleBasedEngagementScorerTest {

    private final RuleBasedEngagementScorer scorer = new RuleBasedEngagementScorer();
    private final EngagementScoringConfig config = EngagementScoringConfig.defaults();

    @Test
    void scorePenalizesInactiveNoShowAndOverdueSignals() {
        int score = scorer.score(new RuleBasedEngagementScorer.ScoringSignals(
                120,
                120,
                3,
                0,
                1,
                1,
                2,
                1
        ), config);

        assertThat(score).isLessThanOrEqualTo(24);
        assertThat(scorer.level(score)).isEqualTo(EngagementLevel.CRITICAL);
    }

    @Test
    void scoreRewardsRecentAndCompliantPatients() {
        int score = scorer.score(new RuleBasedEngagementScorer.ScoringSignals(
                5,
                5,
                0,
                4,
                0,
                0,
                0,
                0
        ), config);

        assertThat(score).isGreaterThanOrEqualTo(80);
        assertThat(scorer.level(score)).isEqualTo(EngagementLevel.HIGH);
    }
}
