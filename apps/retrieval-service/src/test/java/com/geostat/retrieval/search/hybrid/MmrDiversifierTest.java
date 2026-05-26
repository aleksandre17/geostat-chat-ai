package com.geostat.retrieval.search.hybrid;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MmrDiversifierTest {

    @Test
    void diversifies_similar_documents() {
        List<RetrievedChunk> candidates = new ArrayList<>(List.of(
                chunk("doc1", 0.9f, "inflation price index statistics"),
                chunk("doc2", 0.88f, "inflation price index data"),
                chunk("doc3", 0.85f, "gdp economic growth national accounts")));

        List<RetrievedChunk> diversified = MmrDiversifier.diversify(candidates, 0.7f, 2);

        assertThat(diversified).hasSize(2);
        assertThat(diversified.get(0).documentId()).isEqualTo("doc1");
        assertThat(diversified.get(1).documentId()).isEqualTo("doc3");
    }

    @Test
    void skips_duplicate_documents() {
        List<RetrievedChunk> candidates = new ArrayList<>(List.of(
                chunk("doc1", 0.9f, "text a"),
                chunk("doc1", 0.85f, "text b"),
                chunk("doc2", 0.8f, "text c")));

        List<RetrievedChunk> diversified = MmrDiversifier.diversify(candidates, 0.7f, 3);

        assertThat(diversified).hasSize(2);
        assertThat(diversified).extracting("documentId").containsExactly("doc1", "doc2");
    }

    @Test
    void returns_empty_for_empty_input() {
        List<RetrievedChunk> diversified = MmrDiversifier.diversify(List.of(), 3);
        assertThat(diversified).isEmpty();
    }

    @Test
    void respects_topN_limit() {
        List<RetrievedChunk> candidates = new ArrayList<>(List.of(
                chunk("doc1", 0.9f, "a"),
                chunk("doc2", 0.8f, "b"),
                chunk("doc3", 0.7f, "c")));

        List<RetrievedChunk> diversified = MmrDiversifier.diversify(candidates, 2);

        assertThat(diversified).hasSize(2);
    }

    private static RetrievedChunk chunk(String docId, double score, String text) {
        return new RetrievedChunk(docId, "http://test/" + docId, text, score, "ka", "Title", null, null, null);
    }
}
