package com.geostat.ingestion.enrichment.topic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiTopicClusterLabelerTest {

    @Test
    void parseLabelResponseReadsBilingualLabelsAndKeywords() {
        String raw =
                """
                {
                  "label_ka": "ინფლაცია",
                  "label_en": "Inflation",
                  "centroid_summary": "CPI and inflation indicators.",
                  "keywords": ["cpi", "inflation", "2024"]
                }
                """;

        TopicClusterLabels labels = GeminiTopicClusterLabeler.parseLabelResponse(raw);

        assertThat(labels.labelKa()).isEqualTo("ინფლაცია");
        assertThat(labels.labelEn()).isEqualTo("Inflation");
        assertThat(labels.centroidSummary()).contains("CPI");
        assertThat(labels.keywords()).containsExactly("cpi", "inflation", "2024");
    }

    @Test
    void parseLabelResponseFallsBackWhenOneLabelMissing() {
        TopicClusterLabels labels =
                GeminiTopicClusterLabeler.parseLabelResponse("{\"label_en\":\"Trade\",\"keywords\":[]}");

        assertThat(labels.labelKa()).isEqualTo("Trade");
        assertThat(labels.labelEn()).isEqualTo("Trade");
    }
}
