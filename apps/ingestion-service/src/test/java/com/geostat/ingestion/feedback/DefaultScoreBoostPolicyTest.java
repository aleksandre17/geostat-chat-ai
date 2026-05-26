package com.geostat.ingestion.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultScoreBoostPolicyTest {

    private final DefaultScoreBoostPolicy policy = new DefaultScoreBoostPolicy();

    @Test
    void returns_zero_when_hits_below_threshold() {
        double delta = policy.calculateDelta(5, 0, 5);
        assertThat(delta).isEqualTo(0.0);
    }

    @Test
    void positive_feedback_increases_boost() {
        double delta = policy.calculateDelta(15, 5, 100);
        assertThat(delta).isGreaterThan(0.0);
    }

    @Test
    void negative_feedback_decreases_boost() {
        double delta = policy.calculateDelta(5, 15, 100);
        assertThat(delta).isLessThan(0.0);
    }

    @Test
    void clamps_boost_to_max_2() {
        double newBoost = policy.clampBoost(1.8, 0.5);
        assertThat(newBoost).isEqualTo(2.0);
    }

    @Test
    void clamps_boost_to_min_05() {
        double newBoost = policy.clampBoost(0.6, -0.3);
        assertThat(newBoost).isEqualTo(0.5);
    }
}
