package com.geostat.chat.domain.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.RetrievalConfidence;
import java.util.List;

/**
 * Assesses confidence of retrieval results for response routing.
 */
public interface RetrievalConfidenceAssessor {

    /**
     * Assess confidence based on retrieved chunks.
     *
     * @param chunks ranked retrieval results
     * @return confidence tier (HIGH/MEDIUM/LOW/NONE)
     */
    RetrievalConfidence assess(List<RetrievedChunk> chunks);
}
