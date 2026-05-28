package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.IntentClassifier;
import com.geostat.chat.domain.query.QueryIntentCacheStore;
import com.geostat.chat.domain.query.QueryIntentKind;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

/**
 * Caching decorator for IntentClassifier.
 * Caches intent classification results in-memory and optionally in Postgres.
 * Wired as {@code @Primary} when {@code geostat.chat.query.intent-cache-enabled=true}.
 */
public class CachingIntentClassifier implements IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(CachingIntentClassifier.class);

    private final IntentClassifier delegate;
    private final QueryIntentCacheStore cacheStore;
    private final Cache<String, QueryIntentKind> cache;

    public CachingIntentClassifier(
            @Qualifier("routingIntentClassifier") IntentClassifier delegate,
            @Autowired(required = false) QueryIntentCacheStore cacheStore,
            @Value("${geostat.chat.query.intent-cache-ttl-hours:24}") int ttlHours,
            @Value("${geostat.chat.query.intent-cache-max-entries:10000}") int maxEntries) {
        this.delegate = delegate;
        this.cacheStore = cacheStore;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(ttlHours))
                .maximumSize(maxEntries)
                .recordStats()
                .build();
    }

    @Override
    public QueryIntentKind classify(String message, String normalized, String locale) {
        String key = QueryIntentCacheKeys.hash(normalized, locale);
        QueryIntentKind cached = cache.getIfPresent(key);

        if (cached != null) {
            log.debug("Intent cache hit for key: {}", key.substring(0, 8));
            return cached;
        }

        if (cacheStore != null) {
            var persisted = cacheStore.get(normalized, locale);
            if (persisted.isPresent()) {
                cache.put(key, persisted.get());
                log.debug("Intent cache hit (store) for key: {}", key.substring(0, 8));
                return persisted.get();
            }
        }

        QueryIntentKind result = delegate.classify(message, normalized, locale);
        cache.put(key, result);
        if (cacheStore != null) {
            cacheStore.put(normalized, locale, result);
        }
        return result;
    }

    public double getHitRate() {
        return cache.stats().hitRate();
    }
}
