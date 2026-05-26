package com.geostat.ingestion.enrichment.authority;

import java.time.Duration;
import java.time.Instant;

public final class FreshnessDecay {

    private static final double HALF_LIFE_DAYS = 365.0;

    private FreshnessDecay() {}

    public static double score(Instant fetchedAt, Instant now) {
        if (fetchedAt == null || now == null || fetchedAt.isAfter(now)) {
            return 0.0;
        }
        double ageDays = Duration.between(fetchedAt, now).toSeconds() / 86_400.0;
        return Math.exp(-ageDays / HALF_LIFE_DAYS);
    }
}
