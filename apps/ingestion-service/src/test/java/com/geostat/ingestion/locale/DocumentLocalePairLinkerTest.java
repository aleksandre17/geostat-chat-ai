package com.geostat.ingestion.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentLocalePairLinkerTest {

    @Test
    void buildsPathPairFromGeorgianUrl() {
        DocumentLocalePairLinker.PathPair pair =
                DocumentLocalePairLinker.toPathPair("https://www.geostat.ge/ka/modules/categories/41", "ka");
        assertThat(pair.pathKey()).isEqualTo("/modules/categories/41");
        assertThat(pair.kaUrl()).contains("/ka/modules");
        assertThat(pair.enUrl()).contains("/en/modules");
    }
}
