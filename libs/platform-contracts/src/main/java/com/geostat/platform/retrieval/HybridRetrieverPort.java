package com.geostat.platform.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievalQuery;

/**
 * Port for hybrid retrieval combining multiple search strategies:
 * - Multi-vector (body, title, summary)
 * - Multi-query (Direct, HyDE, MultiQuery)
 * - BM25 keyword search
 * - RRF fusion + MMR diversification
 */
public interface HybridRetrieverPort {

    /**
     * Execute hybrid search with RRF fusion and MMR diversification.
     *
     * @param query retrieval query with text, locale, maxChunks
     * @return fused and diversified results with confidence
     */
    HybridSearchResult search(RetrievalQuery query);
}
