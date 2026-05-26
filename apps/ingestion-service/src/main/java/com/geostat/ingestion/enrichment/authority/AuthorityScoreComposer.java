package com.geostat.ingestion.enrichment.authority;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AuthorityScoreComposer {

    private static final double PAGERANK_WEIGHT = 0.7;
    private static final double FRESHNESS_WEIGHT = 0.3;

    private AuthorityScoreComposer() {}

    public static double compose(double normalizedPageRank, double freshness) {
        return PAGERANK_WEIGHT * normalizedPageRank + FRESHNESS_WEIGHT * freshness;
    }

    public static Map<UUID, Double> normalizeMinMax(Map<UUID, Double> rawScores) {
        if (rawScores.isEmpty()) {
            return Map.of();
        }
        double min = rawScores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = rawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        Map<UUID, Double> normalized = new LinkedHashMap<>();
        if (max <= min) {
            rawScores.keySet().forEach(id -> normalized.put(id, 0.5));
            return normalized;
        }
        for (Map.Entry<UUID, Double> entry : rawScores.entrySet()) {
            normalized.put(entry.getKey(), (entry.getValue() - min) / (max - min));
        }
        return normalized;
    }
}
