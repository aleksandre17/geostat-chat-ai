package com.geostat.ingestion.feedback;

import com.geostat.platform.feedback.ScoreBoostPolicy;
import org.springframework.stereotype.Component;

/**
 * Default score boost policy based on positive/negative feedback ratio.
 * Boost formula: delta = (positive - negative) / max(hits, 10) * scaleFactor
 */
@Component
public class DefaultScoreBoostPolicy implements ScoreBoostPolicy {

    private static final double SCALE_FACTOR = 0.1;
    private static final int MIN_HITS_THRESHOLD = 10;

    @Override
    public double calculateDelta(int positiveCount, int negativeCount, int totalHits) {
        if (totalHits < MIN_HITS_THRESHOLD) {
            return 0.0;
        }
        int netPositive = positiveCount - negativeCount;
        return (netPositive / (double) totalHits) * SCALE_FACTOR;
    }
}
