package com.geostat.retrieval.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.retrieval.config.RetrievalProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.retrieval.cache", name = "backend", havingValue = "redis")
public class RedisRetrievalCacheBackend implements RetrievalCacheBackend {

    private static final Logger log = LoggerFactory.getLogger(RedisRetrievalCacheBackend.class);
    private static final String KEY_PREFIX = "retrieval:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisRetrievalCacheBackend(
            StringRedisTemplate redis, ObjectMapper objectMapper, RetrievalProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(properties.cache().ttlMinutes());
    }

    @Override
    public List<RetrievedChunk> get(String key) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("redis retrieval cache get failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, List<RetrievedChunk> hits) {
        try {
            String json = objectMapper.writeValueAsString(hits);
            redis.opsForValue().set(KEY_PREFIX + key, json, ttl);
        } catch (Exception e) {
            log.warn("redis retrieval cache put failed: {}", e.getMessage());
        }
    }
}
