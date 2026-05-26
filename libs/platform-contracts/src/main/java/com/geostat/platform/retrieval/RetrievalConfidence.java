package com.geostat.platform.retrieval;

/**
 * Confidence tier for retrieval results.
 * Used by ResponseRouter to decide answer strategy.
 */
public enum RetrievalConfidence {
    /** Top score > 0.75 AND gap to second > 0.05 — answer with citations */
    HIGH,
    /** Top score > 0.55 — answer with suggestions */
    MEDIUM,
    /** Top score > 0.35 — clarification needed */
    LOW,
    /** No good match — refuse or suggest topics */
    NONE;

    public static RetrievalConfidence assess(float topScore, float secondScore) {
        if (topScore > 0.75f && (topScore - secondScore) > 0.05f) {
            return HIGH;
        }
        if (topScore > 0.55f) {
            return MEDIUM;
        }
        if (topScore > 0.35f) {
            return LOW;
        }
        return NONE;
    }

    public static RetrievalConfidence assess(float topScore) {
        return assess(topScore, 0f);
    }
}
