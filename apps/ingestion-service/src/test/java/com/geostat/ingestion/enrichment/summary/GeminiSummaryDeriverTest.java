package com.geostat.ingestion.enrichment.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiSummaryDeriverTest {

    @Test
    void parseSummaryResponseFromJsonBlock() {
        String raw =
                """
                ```json
                {"summary_ka":"სამომხმარებლო ფასების ინდექსი 2024.","summary_en":"Consumer price index 2024."}
                ```
                """;
        var result = GeminiSummaryDeriver.parseSummaryResponse(raw, "gemini-2.0-flash-lite@2026-05-25");
        assertThat(result.summaryKa()).contains("ინდექსი");
        assertThat(result.summaryEn()).contains("Consumer price index");
        assertThat(result.modelVersion()).isEqualTo("gemini-2.0-flash-lite@2026-05-25");
    }

    @Test
    void parseSummaryResponseTrimsLongText() {
        String longKa = "ა".repeat(400);
        String raw = "{\"summary_ka\":\"" + longKa + "\",\"summary_en\":\"Short en.\"}";
        var result = GeminiSummaryDeriver.parseSummaryResponse(raw, "v1");
        assertThat(result.summaryKa()).hasSize(320);
        assertThat(result.summaryEn()).isEqualTo("Short en.");
    }
}
