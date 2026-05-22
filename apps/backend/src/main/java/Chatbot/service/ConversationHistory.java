package Chatbot.service;

import org.springframework.ai.chat.messages.Message;
import java.util.Deque;

/**
 * Session conversation history.
 * Default implementation uses Caffeine (TTL 30 min, max 1000 sessions).
 * Swap for RedisConversationHistory when distributed sessions are needed.
 */
public interface ConversationHistory {

    /** Returns the mutable history deque for the session, creating an empty one if absent. */
    Deque<Message> getOrCreate(String sessionId);
}