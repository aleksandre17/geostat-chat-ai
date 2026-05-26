package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.query.QueryIntentKind;
import org.junit.jupiter.api.Test;

class GeminiIntentClassifierTest {

    @Test
    void parsesIntentJson() {
        assertThat(GeminiIntentClassifier.parseIntentResponse("{\"intent\":\"lookup\"}"))
                .isEqualTo(QueryIntentKind.LOOKUP);
    }

    @Test
    void parsesIntentFromCodeBlock() {
        assertThat(GeminiIntentClassifier.parseIntentResponse(
                        "```json\n{\"intent\":\"navigation\"}\n```"))
                .isEqualTo(QueryIntentKind.NAVIGATION);
    }
}
