package com.geostat.chat.application.query;

import com.geostat.chat.domain.chat.QueryIntent;
import com.geostat.chat.domain.query.QueryIntentKind;
import org.springframework.stereotype.Component;

@Component
public class QueryIntentMapper {

    public QueryIntent toChatIntent(QueryIntentKind kind) {
        if (kind == null) {
            return QueryIntent.CONCEPT;
        }
        return switch (kind) {
            case NAVIGATION -> QueryIntent.NAVIGATE;
            case SMALLTALK -> QueryIntent.CLARIFY;
            case COMPARE, DEFINITION, FACTUAL, LATEST, LOOKUP -> QueryIntent.CONCEPT;
        };
    }
}
