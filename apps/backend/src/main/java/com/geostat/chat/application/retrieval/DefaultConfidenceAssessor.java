package com.geostat.chat.application.retrieval;

import com.geostat.chat.domain.retrieval.RetrievalConfidenceAssessor;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.RetrievalConfidence;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultConfidenceAssessor implements RetrievalConfidenceAssessor {

    private final float highThreshold;
    private final float mediumThreshold;
    private final float lowThreshold;
    private final float gapThreshold;

    public DefaultConfidenceAssessor(
            @Value("${geostat.chat.retrieval.confidence.high:0.75}") float high,
            @Value("${geostat.chat.retrieval.confidence.medium:0.55}") float medium,
            @Value("${geostat.chat.retrieval.confidence.low:0.35}") float low,
            @Value("${geostat.chat.retrieval.confidence.gap:0.02}") float gap) {
        this.highThreshold = high;
        this.mediumThreshold = medium;
        this.lowThreshold = low;
        this.gapThreshold = gap;
    }

    @Override
    public RetrievalConfidence assess(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return RetrievalConfidence.NONE;
        }
        double topScore = chunks.get(0).score();
        double secondScore = chunks.size() > 1 ? chunks.get(1).score() : 0.0;
        double gap = topScore - secondScore;

        if (topScore > highThreshold && gap > gapThreshold) {
            return RetrievalConfidence.HIGH;
        }
        if (topScore > mediumThreshold) {
            return RetrievalConfidence.MEDIUM;
        }
        if (topScore > lowThreshold) {
            return RetrievalConfidence.LOW;
        }
        return RetrievalConfidence.NONE;
    }
}
