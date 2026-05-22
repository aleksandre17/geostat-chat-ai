package com.geostat.ingestion.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import org.junit.jupiter.api.Test;

class VectorCollectionNamingTest {

    @Test
    void usesSanitizedCorpusName() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setName("geostat-portal");

        assertThat(VectorCollectionNaming.collectionFor(corpus)).isEqualTo("geostat-portal");
    }

    @Test
    void replacesInvalidCharacters() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setName("Site A/B (2026)");

        assertThat(VectorCollectionNaming.collectionFor(corpus)).isEqualTo("site-a-b-2026");
    }
}
