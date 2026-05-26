package com.geostat.chat.infrastructure.query;

import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.domain.query.IntentClassifier;
import com.geostat.chat.domain.query.QueryIntentKind;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Routes to Gemini or heuristic intent classifier based on feature flags. */
@Component("routingIntentClassifier")
@Primary
public class RoutingIntentClassifier implements IntentClassifier {

    private final QueryUnderstandingProperties properties;
    private final HeuristicIntentClassifier heuristicIntentClassifier;
    private final GeminiIntentClassifier geminiIntentClassifier;

    public RoutingIntentClassifier(
            QueryUnderstandingProperties properties,
            HeuristicIntentClassifier heuristicIntentClassifier,
            GeminiIntentClassifier geminiIntentClassifier) {
        this.properties = properties;
        this.heuristicIntentClassifier = heuristicIntentClassifier;
        this.geminiIntentClassifier = geminiIntentClassifier;
    }

    @Override
    public QueryIntentKind classify(String message, String normalized, String locale) {
        if (properties.isEnabled() && properties.isGeminiIntentEnabled()) {
            return geminiIntentClassifier.classify(message, normalized, locale);
        }
        return heuristicIntentClassifier.classify(message, normalized, locale);
    }
}
