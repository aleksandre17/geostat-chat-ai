package com.geostat.platform.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;

/**
 * Port for searching Qdrant named vectors (body, title, summary).
 * Each vector type may have different retrieval characteristics.
 */
public interface NamedVectorSearchPort {

    /**
     * Search using a specific named vector.
     *
     * @param corpus corpus name (collection prefix)
     * @param vectorName one of: "body", "title", "summary"
     * @param queryVector embedded query
     * @param locale optional language filter (ka/en)
     * @param topK max results
     * @return scored chunks
     */
    List<RetrievedChunk> search(
            String corpus,
            String vectorName,
            float[] queryVector,
            String locale,
            int topK);

    /** Available named vectors in the collection. */
    default List<String> availableVectors() {
        return List.of("body", "title", "summary");
    }
}
