package com.geostat.retrieval.search.hybrid;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class RrfFusionTest {

    @Test
    void fuses_multiple_lists_by_reciprocal_rank() {
        List<RetrievedChunk> list1 = List.of(
                chunk("doc1", 0.9f),
                chunk("doc2", 0.8f),
                chunk("doc3", 0.7f));

        List<RetrievedChunk> list2 = List.of(
                chunk("doc2", 0.95f),
                chunk("doc3", 0.85f),
                chunk("doc1", 0.75f));

        List<RetrievedChunk> fused = RrfFusion.fuse(List.of(list1, list2), 60, 3);

        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).documentId()).isIn("doc1", "doc2");
    }

    @Test
    void handles_disjoint_lists() {
        List<RetrievedChunk> list1 = List.of(chunk("doc1", 0.9f));
        List<RetrievedChunk> list2 = List.of(chunk("doc2", 0.8f));

        List<RetrievedChunk> fused = RrfFusion.fuse(List.of(list1, list2), 60, 5);

        assertThat(fused).hasSize(2);
        assertThat(fused).extracting("documentId").containsExactlyInAnyOrder("doc1", "doc2");
    }

    @Test
    void respects_topN_limit() {
        List<RetrievedChunk> list = List.of(
                chunk("doc1", 0.9f),
                chunk("doc2", 0.8f),
                chunk("doc3", 0.7f));

        List<RetrievedChunk> fused = RrfFusion.fuse(List.of(list), 60, 2);

        assertThat(fused).hasSize(2);
    }

    @Test
    void returns_empty_for_empty_input() {
        List<RetrievedChunk> fused = RrfFusion.fuse(List.of(), 60, 5);
        assertThat(fused).isEmpty();
    }

    private static RetrievedChunk chunk(String docId, double score) {
        return new RetrievedChunk(docId, "http://test/" + docId, "text", score, "ka", "Title", null, null, null);
    }
}
