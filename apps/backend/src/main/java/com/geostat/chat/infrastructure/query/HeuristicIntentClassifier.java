package com.geostat.chat.infrastructure.query;

import com.geostat.chat.application.util.KeywordMatcher;
import com.geostat.chat.domain.query.IntentClassifier;
import com.geostat.chat.domain.query.QueryIntentKind;
import java.util.List;
import java.util.Map;

/** RAG-U07c heuristic classifier — used directly and as Gemini fallback. */
public class HeuristicIntentClassifier implements IntentClassifier {

    private final Map<QueryIntentKind, List<String>> keywordMap;
    private final KeywordMatcher keywordMatcher;

    private static final List<QueryIntentKind> PRIORITY_ORDER = List.of(
            QueryIntentKind.SMALLTALK,
            QueryIntentKind.COMPARE,
            QueryIntentKind.DEFINITION,
            QueryIntentKind.FACTUAL,
            QueryIntentKind.LATEST,
            QueryIntentKind.STATISTICAL,
            QueryIntentKind.NAVIGATION);

    public HeuristicIntentClassifier(Map<QueryIntentKind, List<String>> keywordMap, KeywordMatcher keywordMatcher) {
        this.keywordMap = Map.copyOf(keywordMap);
        this.keywordMatcher = keywordMatcher;
    }

    @Override
    public QueryIntentKind classify(String message, String normalized, String locale) {
        if (message == null || message.isBlank()) {
            return QueryIntentKind.LOOKUP;
        }
        String lower = normalized != null ? normalized.toLowerCase() : message.toLowerCase();

        for (QueryIntentKind intent : PRIORITY_ORDER) {
            List<String> keywords = keywordMap.getOrDefault(intent, List.of());
            if (keywordMatcher.containsAny(lower, keywords)) {
                // STATISTICAL must not match portal queries
                if (intent == QueryIntentKind.STATISTICAL) {
                    List<String> navKeywords = keywordMap.getOrDefault(QueryIntentKind.NAVIGATION, List.of());
                    if (keywordMatcher.containsAny(lower, navKeywords)) {
                        continue;
                    }
                }
                return intent;
            }
        }
        return QueryIntentKind.LOOKUP;
    }
}
