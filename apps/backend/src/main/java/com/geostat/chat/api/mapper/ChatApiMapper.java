package com.geostat.chat.api.mapper;

import com.geostat.chat.api.dto.ChatErrorDetail;
import com.geostat.chat.api.dto.ChatFeedbackRequest;
import com.geostat.chat.api.dto.ChatResponse;
import com.geostat.chat.application.chat.ChatResult;
import com.geostat.chat.application.telemetry.ChatFeedbackCommand;
import org.springframework.stereotype.Component;

/** Maps application models ↔ REST DTOs (api layer only). */
@Component
public class ChatApiMapper {

    public ChatResponse toDto(ChatResult result) {
        ChatErrorDetail error = null;
        if (result.hasError()) {
            error = new ChatErrorDetail(result.errorCode(), result.errorMessage());
        }
        return new ChatResponse(
                result.intro(),
                result.items(),
                result.language(),
                result.primaryTopic(),
                result.topics(),
                result.topicIcon(),
                result.topicColor(),
                result.sessionId(),
                result.turnId(),
                result.responseKind().name(),
                result.grounded(),
                result.sourceCount(),
                error);
    }

    public ChatFeedbackCommand toCommand(ChatFeedbackRequest request) {
        return new ChatFeedbackCommand(
                request.turnId(),
                request.sessionId(),
                request.rating(),
                request.comment());
    }
}
