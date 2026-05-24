package com.geostat.chat.infrastructure.session;

import com.geostat.chat.domain.session.ConversationHistory;
import com.geostat.chat.infrastructure.config.AiChatProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "geostat.session.store", havingValue = "redis")
public class RedisConversationHistory implements ConversationHistory {

    private static final String KEY_PREFIX = "geostat:chat:session:";

    private final StringRedisTemplate redis;
    private final AiChatProperties aiChatProperties;
    private final Cache<String, Deque<Message>> local;

    public RedisConversationHistory(StringRedisTemplate redis, AiChatProperties aiChatProperties) {
        this.redis = redis;
        this.aiChatProperties = aiChatProperties;
        this.local = Caffeine.newBuilder()
                .expireAfterAccess(aiChatProperties.sessionTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    @Override
    public Deque<Message> getOrCreate(String sessionId) {
        return local.get(sessionId, this::loadFromRedis);
    }

    @Override
    public void persist(String sessionId, Deque<Message> history) {
        local.put(sessionId, history);
        Duration ttl = Duration.ofMinutes(aiChatProperties.sessionTtlMinutes());
        redis.opsForValue().set(KEY_PREFIX + sessionId, SessionMessageCodec.encode(history), ttl);
    }

    private Deque<Message> loadFromRedis(String sessionId) {
        String json = redis.opsForValue().get(KEY_PREFIX + sessionId);
        Deque<Message> loaded = SessionMessageCodec.decode(json);
        return Objects.requireNonNullElseGet(loaded, ArrayDeque::new);
    }
}
