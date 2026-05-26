package com.geostat.ingestion.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VectorSimilarityTest {

    @Test
    void cosineIsOneForIdenticalVectors() {
        assertThat(VectorSimilarity.cosine(new float[] {1, 0}, new float[] {1, 0})).isEqualTo(1.0);
    }

    @Test
    void cosineIsZeroForOrthogonalVectors() {
        assertThat(VectorSimilarity.cosine(new float[] {1, 0}, new float[] {0, 1})).isEqualTo(0.0);
    }
}
