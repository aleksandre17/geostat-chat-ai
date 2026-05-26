package com.geostat.retrieval.search.hybrid;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF) for combining multiple ranked lists.
 * Formula: RRF(d) = Σ 1 / (k + rank(d))
 * where k is a smoothing constant (typically 60).
 */
public final class RrfFusion {

    private static final int DEFAULT_K = 60;

    private RrfFusion() {}

    /**
     * Fuse multiple ranked lists using RRF.
     *
     * @param rankedLists list of ranked results from different sources
     * @param k smoothing constant (default 60)
     * @param topN max results to return
     * @return fused and re-ranked list
     */
    public static List<RetrievedChunk> fuse(List<List<RetrievedChunk>> rankedLists, int k, int topN) {
        Map<String, FusedScore> scores = new HashMap<>();

        for (List<RetrievedChunk> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RetrievedChunk chunk = list.get(rank);
                String key = chunkKey(chunk);
                FusedScore fs = scores.computeIfAbsent(key, ignored -> new FusedScore(chunk));
                fs.addRank(rank, k);
            }
        }

        return scores.values().stream()
                .sorted(Comparator.comparingDouble(FusedScore::score).reversed())
                .limit(topN)
                .map(fs -> new RetrievedChunk(
                        fs.chunk.documentId(),
                        fs.chunk.sourceUrl(),
                        fs.chunk.text(),
                        fs.score(),
                        fs.chunk.language(),
                        fs.chunk.pageTitle(),
                        fs.chunk.sectionPath(),
                        fs.chunk.pageDescription(),
                        fs.chunk.fetchedAt()))
                .toList();
    }

    public static List<RetrievedChunk> fuse(List<List<RetrievedChunk>> rankedLists, int topN) {
        return fuse(rankedLists, DEFAULT_K, topN);
    }

    private static String chunkKey(RetrievedChunk chunk) {
        return chunk.documentId() + ":" + (chunk.text() != null ? chunk.text().hashCode() : 0);
    }

    private static class FusedScore {
        final RetrievedChunk chunk;
        double rrfScore = 0.0;
        int contributions = 0;

        FusedScore(RetrievedChunk chunk) {
            this.chunk = chunk;
        }

        void addRank(int rank, int k) {
            rrfScore += 1.0 / (k + rank + 1);
            contributions++;
        }

        double score() {
            return rrfScore;
        }
    }
}
