package com.geostat.retrieval.search;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RAG-L07 full: bi-encoder semantic re-rank (query + passage embeddings via {@link EmbeddingPort}).
 * Lightweight alternative to ONNX cross-encoder; uses approved embedding stack.
 */
public final class SemanticCrossEncoderReranker {

    private static final int MAX_CANDIDATES = 24;
    private static final int SNIPPET_CHARS = 512;
    private static final double SEMANTIC_WEIGHT = 0.35;

    private SemanticCrossEncoderReranker() {}

    public static List<RetrievedChunk> rerank(
            EmbeddingPort embedding, List<RetrievedChunk> hits, String query, int limit) {
        if (embedding == null || hits == null || hits.isEmpty() || query == null || query.isBlank()) {
            return hits == null ? List.of() : hits;
        }
        int candidateCount = Math.min(hits.size(), Math.max(limit * 3, MAX_CANDIDATES));
        List<RetrievedChunk> candidates = hits.subList(0, candidateCount);
        float[] queryVector = embedding.embed(query.strip());
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (RetrievedChunk hit : candidates) {
            String passage = passageText(hit);
            if (passage.isBlank()) {
                scored.add(new Scored(hit, hit.score()));
                continue;
            }
            float[] passageVector = embedding.embed(passage);
            double semantic = cosine(queryVector, passageVector);
            double blended = hit.score() + semantic * SEMANTIC_WEIGHT;
            scored.add(new Scored(hit, blended));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        List<RetrievedChunk> out = new ArrayList<>(Math.min(limit, scored.size()));
        Set<String> seenUrls = new HashSet<>();
        for (Scored s : scored) {
            if (out.size() >= limit) {
                break;
            }
            String url = s.chunk().sourceUrl();
            if (url != null && !seenUrls.add(url.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(new RetrievedChunk(
                    s.chunk().documentId(),
                    s.chunk().sourceUrl(),
                    s.chunk().text(),
                    s.score(),
                    s.chunk().language(),
                    s.chunk().pageTitle(),
                    s.chunk().sectionPath(),
                    s.chunk().pageDescription(),
                    s.chunk().fetchedAt()));
        }
        return List.copyOf(out);
    }

    private static String passageText(RetrievedChunk hit) {
        String description = hit.pageDescription() != null ? hit.pageDescription().strip() : "";
        String title = hit.pageTitle() != null ? hit.pageTitle().strip() : "";
        String body = hit.text() != null ? hit.text().strip() : "";
        String combined;
        if (!description.isEmpty()) {
            combined = title.isEmpty() ? description : title + ". " + description;
        } else {
            combined = title.isEmpty() ? body : title + ". " + body;
        }
        if (combined.length() <= SNIPPET_CHARS) {
            return combined;
        }
        return combined.substring(0, SNIPPET_CHARS);
    }

    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0;
        }
        int len = Math.min(a.length, b.length);
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Scored(RetrievedChunk chunk, double score) {}
}
