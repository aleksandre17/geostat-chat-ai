package com.geostat.retrieval.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalRerankerTest {

    @Test
    void prefersMatchingLocaleAndKeywordOverlap() {
        List<RetrievedChunk> hits = List.of(
                new RetrievedChunk("d1", "https://x/en/a", "unrelated text", 0.95, "en", null, null),
                new RetrievedChunk(
                        "d2",
                        "https://x/ka/stat",
                        "სტატისტიკის მონაცემები",
                        0.80,
                        "ka",
                        "სტატისტიკა",
                        null));

        List<RetrievedChunk> ranked = RetrievalReranker.rerank(hits, "სტატისტიკა", "ka", 2);

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).documentId()).isEqualTo("d2");
    }
}
