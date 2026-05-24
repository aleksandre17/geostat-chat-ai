package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.retrieval.config.RetrievalProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "geostat.retrieval.cache",
        name = "backend",
        havingValue = "local",
        matchIfMissing = true)
public class CaffeineRetrievalCacheBackend implements RetrievalCacheBackend {

    private final Cache<String, List<RetrievedChunk>> localCache;

    public CaffeineRetrievalCacheBackend(RetrievalProperties properties) {
        int ttlMinutes = properties.cache().ttlMinutes();
        int maxEntries = properties.cache().maxEntries();
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .build();
    }

    @Override
    public List<RetrievedChunk> get(String key) {
        return localCache.getIfPresent(key);
    }

    @Override
    public void put(String key, List<RetrievedChunk> hits) {
        localCache.put(key, List.copyOf(hits));
    }
}
