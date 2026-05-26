package com.geostat.ingestion.enrichment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.enrichment.Entity;
import org.junit.jupiter.api.Test;

class GeminiFewShotEntityDeriverTest {

    @Test
    void parseEntityResponseFromJsonObject() {
        String raw =
                """
                ```json
                {"entities":[
                  {"type":"INDICATOR","value":"CPI","normalizedForm":"CPI","confidence":0.95},
                  {"type":"YEAR","value":"2024","confidence":1.0}
                ]}
                ```
                """;
        var entities = GeminiFewShotEntityDeriver.parseEntityResponse(raw);
        assertThat(entities).hasSize(2);
        assertThat(entities.get(0).type()).isEqualTo("INDICATOR");
        assertThat(entities.get(0).value()).isEqualTo("CPI");
        assertThat(entities.get(1).type()).isEqualTo("YEAR");
        assertThat(entities.get(1).normalizedForm()).isEqualTo("2024");
    }

    @Test
    void parseEntityResponseFromJsonArray() {
        String raw =
                """
                [{"type":"REGION","value":"Imereti","normalizedForm":"Imereti","confidence":0.9}]
                """;
        var entities = GeminiFewShotEntityDeriver.parseEntityResponse(raw);
        assertThat(entities).extracting(Entity::type).containsExactly("REGION");
    }

    @Test
    void parseEntityResponseFiltersInvalidTypesAndYears() {
        String raw =
                """
                {"entities":[
                  {"type":"INDICATOR","value":"GDP"},
                  {"type":"INVALID","value":"foo"},
                  {"type":"YEAR","value":"1800"},
                  {"type":"ORGANIZATION","value":"Geostat"}
                ]}
                """;
        var entities = GeminiFewShotEntityDeriver.parseEntityResponse(raw);
        assertThat(entities).extracting(Entity::type).containsExactly("INDICATOR", "ORGANIZATION");
    }

    @Test
    void deduplicateKeepsFirstOccurrence() {
        var entities = GeminiFewShotEntityDeriver.deduplicate(java.util.List.of(
                new Entity("INDICATOR", "CPI", "CPI", 0.9),
                new Entity("INDICATOR", "consumer price index", "CPI", 0.8)));
        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).confidence()).isEqualTo(0.9);
    }
}
