package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiQueryExpanderTest {

    @Test
    void parsesParaphraseArray() {
        var phrases = GeminiQueryExpander.parseParaphrases(
                """
                {"paraphrases":["consumer price inflation","cpi statistics"]}
                """);
        assertThat(phrases).containsExactly("consumer price inflation", "cpi statistics");
    }
}
