package com.geostat.platform.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EntityJsonParserTest {

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
        var entities = EntityJsonParser.parseEntityResponse(raw);
        assertEquals(2, entities.size());
        assertEquals("INDICATOR", entities.get(0).type());
        assertEquals("YEAR", entities.get(1).type());
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
        var entities = EntityJsonParser.parseEntityResponse(raw);
        assertEquals(2, entities.size());
        assertEquals("INDICATOR", entities.get(0).type());
        assertEquals("ORGANIZATION", entities.get(1).type());
    }
}
