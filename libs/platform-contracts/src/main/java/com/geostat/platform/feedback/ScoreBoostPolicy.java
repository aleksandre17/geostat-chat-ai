package com.geostat.platform.feedback;

/**
 * Policy for mapping feedback signals to score boost delta.
 * Used by FeedbackScoreAggregator to adjust document.score_boost.
 */
public interface ScoreBoostPolicy {

    /**
     * Calculate boost delta based on feedback metrics.
     *
     * @param positiveCount number of positive ratings
     * @param negativeCount number of negative ratings
     * @param totalHits total retrieval hits
     * @return boost delta in range [-0.5, +1.0]
     */
    double calculateDelta(int positiveCount, int negativeCount, int totalHits);

    /**
     * Clamp boost to valid range [0.5, 2.0].
     */
    default double clampBoost(double currentBoost, double delta) {
        double newBoost = currentBoost + delta;
        return Math.max(0.5, Math.min(2.0, newBoost));
    }
}
