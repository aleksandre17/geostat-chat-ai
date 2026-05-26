package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.enrichment.Entity;
import org.junit.jupiter.api.Test;

class HeuristicQueryEntityExtractorTest {

    private final HeuristicQueryEntityExtractor extractor = new HeuristicQueryEntityExtractor();

    @Test
    void extractsYearAndGdpIndicator() {
        var entities = extractor.extract("GDP 2024", "gdp 2024", "en");

        assertThat(entities).extracting(Entity::type).contains("YEAR", "INDICATOR");
        assertThat(entities).anyMatch(e -> "2024".equals(e.normalizedForm()));
        assertThat(entities).anyMatch(e -> "GDP".equals(e.normalizedForm()));
    }
}
