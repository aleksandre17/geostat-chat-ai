package com.geostat.ingestion.enrichment.authority;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorityScoreComposerTest {

    @Test
    void composeBlendsPageRankAndFreshness() {
        double score = AuthorityScoreComposer.compose(1.0, 0.5);
        assertThat(score).isEqualTo(0.7 * 1.0 + 0.3 * 0.5);
    }

    @Test
    void normalizeMinMaxMapsRangeToZeroOne() {
        UUID low = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        UUID high = UUID.randomUUID();

        Map<UUID, Double> normalized =
                AuthorityScoreComposer.normalizeMinMax(Map.of(low, 1.0, mid, 2.0, high, 3.0));

        assertThat(normalized.get(low)).isEqualTo(0.0);
        assertThat(normalized.get(mid)).isEqualTo(0.5);
        assertThat(normalized.get(high)).isEqualTo(1.0);
    }

    @Test
    void normalizeMinMaxUsesMidpointWhenAllScoresEqual() {
        UUID id = UUID.randomUUID();
        Map<UUID, Double> normalized = AuthorityScoreComposer.normalizeMinMax(Map.of(id, 0.42));
        assertThat(normalized.get(id)).isEqualTo(0.5);
    }
}
