package com.geostat.chat.application.telemetry;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Short-lived turnId → sessionId map for feedback correlation. */
@Component
public class TurnRegistry {

    private final Cache<String, String> turns = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    public void register(String turnId, String sessionId) {
        if (turnId != null && !turnId.isBlank() && sessionId != null) {
            turns.put(turnId, sessionId);
        }
    }

    public boolean knowsTurn(String turnId) {
        return turnId != null && turns.getIfPresent(turnId) != null;
    }
}
