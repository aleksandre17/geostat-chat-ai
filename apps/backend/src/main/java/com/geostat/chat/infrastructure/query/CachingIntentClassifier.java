package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.IntentClassifier;
import com.geostat.chat.domain.query.QueryIntentKind;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Caching decorator for IntentClassifier.
 * Caches intent classification results for 24 hours.
 * Enabled via geostat.chat.query.intent-cache-enabled=true.
 */
@Component("cachingIntentClassifier")
@ConditionalOnProperty(
        prefix = "geostat.chat.query",
        name = "intent-cache-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class CachingIntentClassifier implements IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(CachingIntentClassifier.class);

    private final IntentClassifier delegate;
    private final Cache<String, QueryIntentKind> cache;

    public CachingIntentClassifier(
            @Qualifier("routingIntentClassifier") IntentClassifier delegate,
            @Value("${geostat.chat.query.intent-cache-ttl-hours:24}") int ttlHours,
            @Value("${geostat.chat.query.intent-cache-max-entries:10000}") int maxEntries) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(ttlHours))
                .maximumSize(maxEntries)
                .recordStats()
                .build();
    }

    @Override
    public QueryIntentKind classify(String message, String normalized, String locale) {
        String key = cacheKey(normalized, locale);
        QueryIntentKind cached = cache.getIfPresent(key);

        if (cached != null) {
            log.debug("Intent cache hit for key: {}", key.substring(0, 8));
            return cached;
        }

        QueryIntentKind result = delegate.classify(message, normalized, locale);
        cache.put(key, result);
        return result;
    }

    private String cacheKey(String normalized, String locale) {
        String raw = (normalized == null ? "" : normalized.strip().toLowerCase())
                + "|"
                + (locale == null ? "" : locale.toLowerCase());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return raw;
        }
    }

    public double getHitRate() {
        return cache.stats().hitRate();
    }
}
