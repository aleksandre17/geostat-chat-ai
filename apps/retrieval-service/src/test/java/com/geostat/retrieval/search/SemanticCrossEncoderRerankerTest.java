package com.geostat.retrieval.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticCrossEncoderRerankerTest {

    @Test
    void boostsSemanticallyCloserPassage() {
        EmbeddingPort embed = new EmbeddingPort() {
            @Override
            public String modelId() {
                return "test";
            }

            @Override
            public int dimensions() {
                return 2;
            }

            @Override
            public float[] embed(String text) {
                if (text.contains("inflation")) {
                    return new float[] {1f, 0f};
                }
                if (text.contains("population")) {
                    return new float[] {0f, 1f};
                }
                return new float[] {0.5f, 0.5f};
            }
        };
        List<RetrievedChunk> hits = List.of(
                new RetrievedChunk("d1", "https://x/a", "population census data", 0.99, "en", "Pop", null),
                new RetrievedChunk("d2", "https://x/b", "inflation consumer prices", 0.70, "en", "Inflation", null));

        List<RetrievedChunk> ranked = SemanticCrossEncoderReranker.rerank(embed, hits, "inflation", 1);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).documentId()).isEqualTo("d2");
    }

    @Test
    void cosineIsOneForIdenticalVectors() {
        assertThat(SemanticCrossEncoderReranker.cosine(new float[] {1, 0}, new float[] {1, 0})).isEqualTo(1.0);
    }
}
