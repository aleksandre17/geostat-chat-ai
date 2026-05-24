package com.geostat.chat.infrastructure.session;

import com.geostat.chat.domain.session.ConversationHistory;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "geostat.session.store", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineConversationHistory implements ConversationHistory {

    private final Cache<String, Deque<Message>> cache;

    public CaffeineConversationHistory(AiChatProperties aiChatProperties) {
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(aiChatProperties.sessionTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    @Override
    public Deque<Message> getOrCreate(String sessionId) {
        Deque<Message> history = cache.getIfPresent(sessionId);
        if (history == null) {
            history = new ArrayDeque<>();
            cache.put(sessionId, history);
        }
        return history;
    }

    @Override
    public void persist(String sessionId, Deque<Message> history) {
        cache.put(sessionId, history);
    }
}
