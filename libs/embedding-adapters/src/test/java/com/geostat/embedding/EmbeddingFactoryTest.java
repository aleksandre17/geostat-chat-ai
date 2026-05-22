package com.geostat.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.geostat.embedding.hash.HashEmbeddingModel;
import org.junit.jupiter.api.Test;

class EmbeddingFactoryTest {

    @Test
    void createsHashProviderByDefault() {
        EmbeddingPort model = EmbeddingFactory.create(EmbeddingSettings.hashDefaults());

        assertEquals(HashEmbeddingModel.MODEL_ID, model.modelId());
        assertEquals(HashEmbeddingModel.DIMENSIONS, model.dimensions());
    }

    @Test
    void hashEmbeddingsAreDeterministic() {
        EmbeddingPort model = EmbeddingFactory.create(new EmbeddingSettings("hash-v1", "", "", "", ""));

        float[] first = model.embed("sample");
        float[] second = model.embed("sample");

        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i]);
        }
    }

    @Test
    void differentTextProducesDifferentVectors() {
        EmbeddingPort model = EmbeddingFactory.create(new EmbeddingSettings("hash-v1", "", "", "", ""));

        assertNotEquals(model.embed("alpha")[0], model.embed("beta")[0]);
    }
}
