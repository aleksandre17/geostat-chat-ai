package com.geostat.retrieval.search.hybrid;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maximal Marginal Relevance (MMR) for result diversification.
 * Balances relevance with diversity to avoid redundant results.
 * Formula: MMR = λ * sim(query, doc) - (1-λ) * max(sim(doc, selected))
 */
public final class MmrDiversifier {

    private static final float DEFAULT_LAMBDA = 0.7f;

    private MmrDiversifier() {}

    /**
     * Diversify results using MMR.
     *
     * @param candidates ranked candidates (already scored)
     * @param lambda balance factor (0=max diversity, 1=max relevance)
     * @param topN max results to return
     * @return diversified results
     */
    public static List<RetrievedChunk> diversify(List<RetrievedChunk> candidates, float lambda, int topN) {
        if (candidates.isEmpty() || topN <= 0) {
            return List.of();
        }

        List<RetrievedChunk> selected = new ArrayList<>();
        Set<String> selectedDocs = new HashSet<>();

        while (selected.size() < topN && !candidates.isEmpty()) {
            RetrievedChunk best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;
            int bestIdx = -1;

            for (int i = 0; i < candidates.size(); i++) {
                RetrievedChunk candidate = candidates.get(i);
                if (selectedDocs.contains(candidate.documentId())) {
                    continue;
                }

                double relevance = candidate.score();
                double maxSimilarity = maxSimilarityToSelected(candidate, selected);
                double mmr = lambda * relevance - (1 - lambda) * maxSimilarity;

                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    best = candidate;
                    bestIdx = i;
                }
            }

            if (best != null) {
                selected.add(best);
                selectedDocs.add(best.documentId());
                candidates.remove(bestIdx);
            } else {
                break;
            }
        }

        return selected;
    }

    public static List<RetrievedChunk> diversify(List<RetrievedChunk> candidates, int topN) {
        return diversify(new ArrayList<>(candidates), DEFAULT_LAMBDA, topN);
    }

    private static double maxSimilarityToSelected(RetrievedChunk candidate, List<RetrievedChunk> selected) {
        if (selected.isEmpty()) {
            return 0.0;
        }

        double maxSim = 0.0;
        for (RetrievedChunk s : selected) {
            double sim = textSimilarity(candidate.text(), s.text());
            if (sim > maxSim) {
                maxSim = sim;
            }
        }
        return maxSim;
    }

    private static double textSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> setA = tokenize(a);
        Set<String> setB = tokenize(b);
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase().split("\\W+")) {
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
