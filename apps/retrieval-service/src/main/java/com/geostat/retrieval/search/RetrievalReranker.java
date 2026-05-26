package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Locale + keyword + freshness + title overlap re-ranking (RAG-L07). */
public final class RetrievalReranker {

    private RetrievalReranker() {}

    public static List<RetrievedChunk> rerank(List<RetrievedChunk> hits, String query, String locale, int limit) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Set<String> terms = tokenize(query);
        List<Scored> scored = new ArrayList<>(hits.size());
        for (RetrievedChunk hit : hits) {
            double score = hit.score();
            if (locale != null && !locale.isBlank() && locale.equalsIgnoreCase(hit.language())) {
                score += 0.15;
            } else if (locale != null && hit.language() != null && !locale.equalsIgnoreCase(hit.language())) {
                score -= 0.05;
            }
            score += keywordOverlap(terms, hit.text()) * 0.2;
            score += keywordOverlap(terms, hit.pageTitle()) * 0.15;
            score += keywordOverlap(terms, hit.sourceUrl()) * 0.1;
            score += freshnessBoost(hit.fetchedAt());
            scored.add(new Scored(hit, score));
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

    static double freshnessBoost(String fetchedAt) {
        if (fetchedAt == null || fetchedAt.isBlank()) {
            return 0;
        }
        try {
            Instant instant = Instant.parse(fetchedAt);
            long days = java.time.Duration.between(instant, Instant.now()).toDays();
            if (days <= 30) {
                return 0.08;
            }
            if (days <= 180) {
                return 0.04;
            }
            if (days <= 365) {
                return 0.02;
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private static double keywordOverlap(Set<String> terms, String text) {
        if (terms.isEmpty() || text == null || text.isBlank()) {
            return 0;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String term : terms) {
            if (lower.contains(term)) {
                hits++;
            }
        }
        return (double) hits / terms.size();
    }

    private static Set<String> tokenize(String query) {
        Set<String> terms = new HashSet<>();
        if (query == null) {
            return terms;
        }
        for (String part : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (part.length() >= 3) {
                terms.add(part);
            }
        }
        return terms;
    }

    private record Scored(RetrievedChunk chunk, double score) {}
}
