package com.geostat.chat.application.retrieval;

import com.geostat.chat.domain.retrieval.RetrievalConfidenceAssessor;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.RetrievalConfidence;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultConfidenceAssessor implements RetrievalConfidenceAssessor {

    private static final float HIGH_THRESHOLD = 0.75f;
    private static final float MEDIUM_THRESHOLD = 0.55f;
    private static final float LOW_THRESHOLD = 0.35f;
    private static final float GAP_THRESHOLD = 0.05f;

    @Override
    public RetrievalConfidence assess(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return RetrievalConfidence.NONE;
        }

        double topScore = chunks.get(0).score();
        double secondScore = chunks.size() > 1 ? chunks.get(1).score() : 0.0;
        double gap = topScore - secondScore;

        if (topScore > HIGH_THRESHOLD && gap > GAP_THRESHOLD) {
            return RetrievalConfidence.HIGH;
        }
        if (topScore > MEDIUM_THRESHOLD) {
            return RetrievalConfidence.MEDIUM;
        }
        if (topScore > LOW_THRESHOLD) {
            return RetrievalConfidence.LOW;
        }
        return RetrievalConfidence.NONE;
    }
}
