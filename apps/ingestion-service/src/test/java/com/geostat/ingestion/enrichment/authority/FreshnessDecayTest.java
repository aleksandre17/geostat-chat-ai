package com.geostat.ingestion.enrichment.authority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class FreshnessDecayTest {

    @Test
    void freshDocumentScoresNearOne() {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        Instant fetched = now.minus(1, ChronoUnit.DAYS);
        assertThat(FreshnessDecay.score(fetched, now)).isGreaterThan(0.99);
    }

    @Test
    void oneYearOldDocumentScoresNearExpMinusOne() {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        Instant fetched = now.minus(365, ChronoUnit.DAYS);
        assertThat(FreshnessDecay.score(fetched, now)).isCloseTo(Math.exp(-1.0), org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void nullOrFutureFetchedAtReturnsZero() {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        assertThat(FreshnessDecay.score(null, now)).isEqualTo(0.0);
        assertThat(FreshnessDecay.score(now.plus(1, ChronoUnit.DAYS), now)).isEqualTo(0.0);
    }
}
