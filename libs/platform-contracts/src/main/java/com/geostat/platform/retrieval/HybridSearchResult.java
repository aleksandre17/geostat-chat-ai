package com.geostat.platform.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Map;

/**
 * Result of hybrid retrieval combining multiple search strategies.
 */
public record HybridSearchResult(
        List<RetrievedChunk> chunks,
        RetrievalConfidence confidence,
        Map<String, Integer> sourceContribution) {

    public static HybridSearchResult empty() {
        return new HybridSearchResult(List.of(), RetrievalConfidence.NONE, Map.of());
    }

    public boolean hasResults() {
        return chunks != null && !chunks.isEmpty();
    }

    public RetrievedChunk top() {
        return hasResults() ? chunks.get(0) : null;
    }
}
