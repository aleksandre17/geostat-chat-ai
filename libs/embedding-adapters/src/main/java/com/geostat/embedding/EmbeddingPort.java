package com.geostat.embedding;

import java.util.List;

public interface EmbeddingPort {

    String modelId();

    int dimensions();

    float[] embed(String text);

    /**
     * Batch embed multiple texts in a single API call.
     *
     * Contract:
     *   - results[i] corresponds to texts.get(i)
     *   - texts must not be empty
     *   - max 100 texts per call (Gemini batch limit)
     *
     * Default: falls back to individual embed() calls.
     * Gemini adapter MUST override with real batch request.
     */
    default float[][] embedBatch(List<String> texts) {
        float[][] results = new float[texts.size()][];
        for (int i = 0; i < texts.size(); i++) {
            results[i] = embed(texts.get(i));
        }
        return results;
    }
}
