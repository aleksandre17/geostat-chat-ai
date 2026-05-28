package com.geostat.chat.domain.chat;

import com.geostat.chat.domain.query.AnalyzedQuery;
import org.springframework.ai.chat.messages.Message;
import java.util.Deque;

/**
 * Immutable request context passed through the chat pipeline.
 */
public record ChatContext(
        String message,
        String lowerQuery,
        boolean isGeorgian,
        String locale,
        QueryIntent intent,
        String sessionId,
        Deque<Message> history,
        String retrievalQuery,
        AnalyzedQuery analyzedQuery
) {
    public ChatContext {
        if (locale == null || locale.isBlank()) {
            locale = isGeorgian ? "ka" : "en";
        }
        if (intent == null) {
            intent = QueryIntent.NAVIGATE;
        }
        if (retrievalQuery == null || retrievalQuery.isBlank()) {
            retrievalQuery = message;
        }
        // analyzedQuery may be null when query understanding pipeline is disabled
    }
}
