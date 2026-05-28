package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.enrichment.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeuristicQueryEntityExtractorTest {

    private HeuristicQueryEntityExtractor extractor;

    @BeforeEach
    void setUp() throws Exception {
        extractor = new HeuristicQueryEntityExtractor();
        extractor.load();
    }

    @Test
    void extractsYearAndGdpIndicator() {
        var entities = extractor.extract("GDP 2024", "gdp 2024", "en");

        assertThat(entities).extracting(Entity::type).contains("YEAR", "INDICATOR");
        assertThat(entities).anyMatch(e -> "2024".equals(e.normalizedForm()));
        assertThat(entities).anyMatch(e -> "GDP".equals(e.normalizedForm()));
    }

    @Test
    void extractsGeorgianRegion() {
        var entities = extractor.extract("თბილისის მოსახლეობა 2023", "თბილისის მოსახლეობა 2023", "ka");

        assertThat(entities).extracting(Entity::type).contains("REGION", "YEAR", "INDICATOR");
        assertThat(entities).anyMatch(e -> "Tbilisi".equals(e.normalizedForm()));
    }

    @Test
    void extractsGeorgianOrganization() {
        var entities = extractor.extract("გეოსტატის მონაცემები", "გეოსტატის მონაცემები", "ka");

        assertThat(entities).extracting(Entity::type).contains("ORGANIZATION");
        assertThat(entities).anyMatch(e -> "Geostat".equals(e.normalizedForm()));
    }
}
