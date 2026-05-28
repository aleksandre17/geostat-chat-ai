package com.geostat.ingestion.crawl.frontier;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.url.UrlHasher;
import org.junit.jupiter.api.Test;

class UrlHasherTest {

    @Test
    void hashIsStableForSameUrl() {
        assertThat(UrlHasher.hash("https://www.geostat.ge/ka"))
                .isEqualTo(UrlHasher.hash("https://www.geostat.ge/ka"));
    }

    @Test
    void hashDiffersForDifferentUrls() {
        assertThat(UrlHasher.hash("https://www.geostat.ge/ka"))
                .isNotEqualTo(UrlHasher.hash("https://www.geostat.ge/en"));
    }
}
