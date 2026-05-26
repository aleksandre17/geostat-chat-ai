package com.geostat.chat.domain.query;

import com.geostat.platform.enrichment.Entity;
import java.util.List;

/** Output of the query understanding pipeline (RAG-U07). */
public record AnalyzedQuery(
        String original,
        String spellFixed,
        String normalized,
        String retrievalText,
        QueryIntentKind intent,
        String locale,
        List<Entity> entities,
        List<String> expansions) {

    public AnalyzedQuery {
        entities = entities == null ? List.of() : List.copyOf(entities);
        expansions = expansions == null ? List.of() : List.copyOf(expansions);
    }
}
