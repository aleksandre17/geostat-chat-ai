package com.geostat.chat.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.chat.api.mapper.ChatApiMapper;
import com.geostat.chat.application.chat.ChatCompleteEncoder;
import com.geostat.chat.application.chat.ChatResult;
import org.springframework.stereotype.Component;

@Component
public class JacksonChatCompleteEncoder implements ChatCompleteEncoder {

    private final ObjectMapper objectMapper;
    private final ChatApiMapper chatApiMapper;

    public JacksonChatCompleteEncoder(ObjectMapper objectMapper, ChatApiMapper chatApiMapper) {
        this.objectMapper = objectMapper;
        this.chatApiMapper = chatApiMapper;
    }

    @Override
    public String encodeComplete(ChatResult result) {
        try {
            return objectMapper.writeValueAsString(chatApiMapper.toDto(result));
        } catch (Exception e) {
            throw new IllegalStateException("SSE complete encode failed", e);
        }
    }
}
