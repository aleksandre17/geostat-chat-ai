package com.geostat.ingestion.chunk;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class ChunkHasher {

    /**
     * Stable SHA-256 for deduplication.
     * Normalization MUST match V18 migration SQL:
     *   regexp_replace(lower(trim(text)), '\s+', ' ', 'g')
     *
     * Do NOT change normalization without updating migration and recomputing all hashes.
     */
    public String hash(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return null;
        }
        String normalized = chunkText.strip().toLowerCase().replaceAll("\\s+", " ");
        return Hashing.sha256().hashString(normalized, StandardCharsets.UTF_8).toString();
    }
}
