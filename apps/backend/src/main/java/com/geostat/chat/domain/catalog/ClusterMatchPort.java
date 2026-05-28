package com.geostat.chat.domain.catalog;

import com.geostat.chat.domain.query.AnalyzedQuery;
import java.util.List;

/**
 * Port: application layer requests ranked, scored cluster matches.
 * Infrastructure implements multi-signal SQL + Java scoring against mv_topic_keywords.
 */
public interface ClusterMatchPort {

    /**
     * Returns clusters ranked by multi-signal relevance score.
     * Only clusters with score > 0 are included; the application layer applies
     * its own minimum threshold gate.
     *
     * @param query    fully analyzed query (normalized, entities, expansions)
     * @param language "ka" or "en"
     * @param limit    maximum results to return
     */
    List<ScoredCluster> match(AnalyzedQuery query, String language, int limit);
}
