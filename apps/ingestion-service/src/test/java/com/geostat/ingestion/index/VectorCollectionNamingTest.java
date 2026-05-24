package com.geostat.ingestion.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.qdrant.VectorCollectionNaming;
import org.junit.jupiter.api.Test;

class VectorCollectionNamingTest {

    @Test
    void usesSanitizedCorpusName() {
        assertThat(VectorCollectionNaming.collectionForName("geostat-portal")).isEqualTo("geostat-portal");
    }

    @Test
    void replacesInvalidCharacters() {
        assertThat(VectorCollectionNaming.collectionForName("Site A/B (2026)")).isEqualTo("site-a-b-2026");
    }
}
