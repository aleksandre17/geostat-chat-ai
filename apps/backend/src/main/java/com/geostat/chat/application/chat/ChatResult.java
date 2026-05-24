package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.LinkedExplanation;
import java.util.List;

/** Application-layer chat turn (mapped to {@code ChatResponse} in api.mapper). */
public record ChatResult(
        String intro,
        List<LinkedExplanation> items,
        String language,
        String primaryTopic,
        List<String> topics,
        String topicIcon,
        String topicColor,
        String sessionId,
        String turnId,
        ChatResponseKind responseKind,
        boolean grounded,
        int sourceCount,
        String errorCode,
        String errorMessage) {

    public boolean hasError() {
        return errorCode != null;
    }
}
