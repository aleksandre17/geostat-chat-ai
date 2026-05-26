package com.geostat.chat.infrastructure.query;

import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.domain.query.QueryEntityExtractor;
import com.geostat.platform.enrichment.Entity;
import com.geostat.platform.enrichment.EntityJsonParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Heuristic baseline + optional Gemini merge (RAG-U07d). */
@Component
@Primary
public class RoutingQueryEntityExtractor implements QueryEntityExtractor {

    private final QueryUnderstandingProperties properties;
    private final HeuristicQueryEntityExtractor heuristicQueryEntityExtractor;
    private final GeminiQueryEntityExtractor geminiQueryEntityExtractor;

    public RoutingQueryEntityExtractor(
            QueryUnderstandingProperties properties,
            HeuristicQueryEntityExtractor heuristicQueryEntityExtractor,
            GeminiQueryEntityExtractor geminiQueryEntityExtractor) {
        this.properties = properties;
        this.heuristicQueryEntityExtractor = heuristicQueryEntityExtractor;
        this.geminiQueryEntityExtractor = geminiQueryEntityExtractor;
    }

    @Override
    public List<Entity> extract(String query, String normalized, String locale) {
        List<Entity> heuristic = heuristicQueryEntityExtractor.extract(query, normalized, locale);
        if (!properties.isEnabled() || !properties.isGeminiEntityEnabled()) {
            return heuristic;
        }
        List<Entity> gemini = geminiQueryEntityExtractor.extract(query, normalized, locale);
        if (heuristic.isEmpty()) {
            return gemini;
        }
        if (gemini.isEmpty()) {
            return heuristic;
        }
        List<Entity> merged = new ArrayList<>(heuristic);
        merged.addAll(gemini);
        return EntityJsonParser.deduplicate(merged);
    }
}
