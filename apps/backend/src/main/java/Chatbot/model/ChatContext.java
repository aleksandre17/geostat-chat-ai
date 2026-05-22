package Chatbot.model;

import org.springframework.ai.chat.messages.Message;
import java.util.Deque;

/**
 * Immutable request context passed through the chat pipeline.
 * Built once at the entry point; eliminates parameter repetition across helpers.
 */
public record ChatContext(
        String message,
        String lowerQuery,
        boolean isGeorgian,
        String sessionId,
        Deque<Message> history
) {}