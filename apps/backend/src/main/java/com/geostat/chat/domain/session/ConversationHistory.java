package com.geostat.chat.domain.session;

import org.springframework.ai.chat.messages.Message;
import java.util.Deque;

/**
 * Session conversation history.
 * Default: Caffeine (single instance). Set geostat.session.store=redis for multi-instance.
 */
public interface ConversationHistory {

    Deque<Message> getOrCreate(String sessionId);

    /** Called after each turn so Redis-backed stores can flush. */
    void persist(String sessionId, Deque<Message> history);
}
