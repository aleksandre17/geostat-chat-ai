package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.SourceUrlNormalizer;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Allowed URL set for clarification responses (corpus whitelist). */
public final class CorpusUrlWhitelist {

    private CorpusUrlWhitelist() {}

    public static Set<String> fromChunks(List<RetrievedChunk> chunks) {
        Set<String> allowed = new LinkedHashSet<>();
        if (chunks == null) {
            return allowed;
        }
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null || chunk.sourceUrl() == null || chunk.sourceUrl().isBlank()) {
                continue;
            }
            allowed.add(SourceUrlNormalizer.normalize(chunk.sourceUrl().strip()));
        }
        return allowed;
    }

    public static boolean contains(Set<String> allowed, String url) {
        if (url == null || url.isBlank() || allowed == null || allowed.isEmpty()) {
            return false;
        }
        return allowed.contains(SourceUrlNormalizer.normalize(url.strip()));
    }
}
