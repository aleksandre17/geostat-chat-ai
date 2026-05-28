package com.geostat.ingestion.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EncodingMismatchDetectorTest {

    @Test
    void looksCorrupted_true_forKaUrl_withNoGeorgianChars() {
        assertThat(EncodingMismatchDetector.looksCorrupted(
                        "https://www.geostat.ge/ka/news/123",
                        "Some text with no georgian letters at all"))
                .isTrue();
    }

    @Test
    void looksCorrupted_false_forKaUrl_withGeorgianChars() {
        assertThat(EncodingMismatchDetector.looksCorrupted(
                        "https://www.geostat.ge/ka/news/123",
                        "ბუნებრივი მოძრაობა 2024 წელს"))
                .isFalse();
    }

    @Test
    void looksCorrupted_false_forEnUrl() {
        assertThat(EncodingMismatchDetector.looksCorrupted(
                        "https://www.geostat.ge/en/news/123",
                        "Some English text"))
                .isFalse();
    }

    @Test
    void looksCorrupted_false_forNullText() {
        assertThat(EncodingMismatchDetector.looksCorrupted(
                        "https://www.geostat.ge/ka/news/123",
                        null))
                .isFalse();
    }
}
