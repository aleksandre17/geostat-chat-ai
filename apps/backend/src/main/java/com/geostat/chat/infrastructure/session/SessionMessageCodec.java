package com.geostat.chat.infrastructure.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

final class SessionMessageCodec {

    private static final TypeReference<List<Map<String, String>>> LIST_TYPE = new TypeReference<>() {};

    private SessionMessageCodec() {}

    static String encode(Deque<Message> history) {
        try {
            List<Map<String, String>> rows = history.stream().map(SessionMessageCodec::toRow).toList();
            return new ObjectMapper().writeValueAsString(rows);
        } catch (Exception e) {
            throw new IllegalStateException("session encode failed", e);
        }
    }

    static Deque<Message> decode(String json) {
        Deque<Message> deque = new ArrayDeque<>();
        if (json == null || json.isBlank()) {
            return deque;
        }
        try {
            List<Map<String, String>> rows = new ObjectMapper().readValue(json, LIST_TYPE);
            for (Map<String, String> row : rows) {
                Message message = fromRow(row);
                if (message != null) {
                    deque.addLast(message);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("session decode failed", e);
        }
        return deque;
    }

    private static Map<String, String> toRow(Message message) {
        if (message instanceof UserMessage um) {
            return Map.of("role", "user", "text", um.getText());
        }
        if (message instanceof AssistantMessage am) {
            return Map.of("role", "assistant", "text", am.getText());
        }
        return Map.of("role", "unknown", "text", message.getText());
    }

    private static Message fromRow(Map<String, String> row) {
        String role = row.getOrDefault("role", "");
        String text = row.getOrDefault("text", "");
        return switch (role) {
            case "user" -> new UserMessage(text);
            case "assistant" -> new AssistantMessage(text);
            default -> null;
        };
    }
}
