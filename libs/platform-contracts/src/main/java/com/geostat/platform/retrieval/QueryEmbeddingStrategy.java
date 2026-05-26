package com.geostat.platform.retrieval;

import java.util.List;

/**
 * Strategy for embedding a query into one or more vectors.
 * Implementations: Direct (as-is), HyDE (hypothetical doc), MultiQuery (paraphrases).
 */
public interface QueryEmbeddingStrategy {

    /**
     * Embed the query into one or more vectors.
     *
     * @param query normalized query text
     * @param locale user locale (ka/en)
     * @return list of query vectors (1 for Direct, 1 for HyDE, 3+ for MultiQuery)
     */
    List<float[]> embed(String query, String locale);

    /** Strategy name for telemetry. */
    String name();

    /** Whether this strategy is enabled. */
    default boolean enabled() {
        return true;
    }
}
