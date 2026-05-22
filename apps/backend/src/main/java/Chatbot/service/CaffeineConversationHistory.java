package Chatbot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class CaffeineConversationHistory implements ConversationHistory {

    private final Cache<String, Deque<Message>> cache = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    @Override
    public Deque<Message> getOrCreate(String sessionId) {
        return Objects.requireNonNullElseGet(
                cache.get(sessionId, k -> new ArrayDeque<>()),
                ArrayDeque::new);
    }
}