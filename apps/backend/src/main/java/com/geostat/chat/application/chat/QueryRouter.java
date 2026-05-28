package com.geostat.chat.application.chat;

import com.geostat.chat.application.util.KeywordMatcher;
import com.geostat.chat.domain.chat.QueryIntent;
import java.util.List;
import java.util.Map;

/**
 * Legacy keyword-based intent routing. Replaced by {@link QueryUnderstandingPipeline} when
 * {@code geostat.chat.query-understanding.enabled=true}.
 * All keyword lists are loaded from {@code catalog/route-keywords.yaml}.
 *
 * @deprecated use {@link QueryUnderstandingPipeline} with {@link QueryIntentMapper} instead
 */
@Deprecated
public class QueryRouter {

    private static final List<QueryIntent> PRIORITY_ORDER =
            List.of(QueryIntent.CONCEPT, QueryIntent.DATA_REQUEST, QueryIntent.NAVIGATE);

    private final Map<QueryIntent, List<String>> routeMap;
    private final KeywordMatcher keywordMatcher;

    public QueryRouter(Map<QueryIntent, List<String>> routeMap, KeywordMatcher keywordMatcher) {
        this.routeMap = Map.copyOf(routeMap);
        this.keywordMatcher = keywordMatcher;
    }

    public QueryIntent route(String message, String lowerQuery) {
        if (message == null || message.isBlank()) {
            return QueryIntent.CLARIFY;
        }
        for (QueryIntent intent : PRIORITY_ORDER) {
            List<String> keywords = routeMap.getOrDefault(intent, List.of());
            if (keywordMatcher.containsAny(lowerQuery, keywords)) {
                return intent;
            }
        }
        return QueryIntent.CONCEPT;
    }
}
