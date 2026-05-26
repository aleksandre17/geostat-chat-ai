package com.geostat.ingestion.enrichment.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalePathPatternTest {

    @Test
    void detectsStandardLocalePaths() {
        assertThat(UrlPlusEmbeddingLocalePairer.hasLocalePathSegment("https://www.geostat.ge/ka/modules/41"))
                .isTrue();
        assertThat(UrlPlusEmbeddingLocalePairer.hasLocalePathSegment("https://www.geostat.ge/en/"))
                .isTrue();
    }

    @Test
    void rejectsNonLocalePaths() {
        assertThat(UrlPlusEmbeddingLocalePairer.hasLocalePathSegment("https://legacy.geostat.ge/page?id=1"))
                .isFalse();
    }
}
