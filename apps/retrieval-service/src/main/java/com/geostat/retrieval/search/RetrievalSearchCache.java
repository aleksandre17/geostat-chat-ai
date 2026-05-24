package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.retrieval.config.RetrievalProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Hot retrieval cache facade — delegates to local Caffeine or Redis backend. */
@Component
@ConditionalOnProperty(prefix = "geostat.retrieval.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetrievalSearchCache {

    private final RetrievalCacheBackend backend;

    public RetrievalSearchCache(RetrievalCacheBackend backend) {
        this.backend = backend;
    }

    public List<RetrievedChunk> get(RetrievalQuery query) {
        return backend.get(cacheKey(query));
    }

    public void put(RetrievalQuery query, List<RetrievedChunk> hits) {
        backend.put(cacheKey(query), List.copyOf(hits));
    }

    static String cacheKey(RetrievalQuery query) {
        String raw = (query.text() == null ? "" : query.text().strip().toLowerCase())
                + "|"
                + (query.locale() == null ? "" : query.locale().toLowerCase())
                + "|"
                + query.maxChunks()
                + "|"
                + (query.corpusName() == null ? "" : query.corpusName());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return raw;
        }
    }
}
