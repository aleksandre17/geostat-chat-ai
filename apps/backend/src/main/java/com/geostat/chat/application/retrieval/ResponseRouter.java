package com.geostat.chat.application.retrieval;

import com.geostat.chat.domain.retrieval.ResponseRoute;
import com.geostat.platform.retrieval.RetrievalConfidence;
import org.springframework.stereotype.Component;

/**
 * Routes responses based on retrieval confidence tier.
 * Maps confidence to response strategy.
 */
@Component
public class ResponseRouter {

    /**
     * Determine response route based on confidence.
     *
     * @param confidence retrieval confidence tier
     * @return appropriate response route
     */
    public ResponseRoute route(RetrievalConfidence confidence) {
        return switch (confidence) {
            case HIGH -> ResponseRoute.ANSWER_WITH_CITATIONS;
            case MEDIUM -> ResponseRoute.ANSWER_WITH_SUGGESTIONS;
            case LOW -> ResponseRoute.CLARIFY;
            case NONE -> ResponseRoute.REFUSE_SUGGEST_TOPICS;
        };
    }

    /**
     * Check if we should proceed with answer generation.
     */
    public boolean shouldGenerateAnswer(RetrievalConfidence confidence) {
        return confidence == RetrievalConfidence.HIGH || confidence == RetrievalConfidence.MEDIUM;
    }

    /**
     * Check if we should show clarification UI.
     */
    public boolean shouldClarify(RetrievalConfidence confidence) {
        return confidence == RetrievalConfidence.LOW;
    }

    /**
     * Check if we should refuse and suggest topics.
     */
    public boolean shouldRefuse(RetrievalConfidence confidence) {
        return confidence == RetrievalConfidence.NONE;
    }
}
