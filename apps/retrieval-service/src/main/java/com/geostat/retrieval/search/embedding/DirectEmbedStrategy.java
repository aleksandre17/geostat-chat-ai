package com.geostat.retrieval.search.embedding;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.retrieval.QueryEmbeddingStrategy;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Direct embedding strategy — embeds the query as-is.
 * Always enabled, provides baseline retrieval quality.
 */
@Component
public class DirectEmbedStrategy implements QueryEmbeddingStrategy {

    private final EmbeddingPort embedding;

    public DirectEmbedStrategy(EmbeddingPort embedding) {
        this.embedding = embedding;
    }

    @Override
    public List<float[]> embed(String query, String locale) {
        return List.of(embedding.embed(query));
    }

    @Override
    public String name() {
        return "direct";
    }
}
