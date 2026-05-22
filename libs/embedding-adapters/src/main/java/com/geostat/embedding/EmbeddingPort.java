package com.geostat.embedding;

public interface EmbeddingPort {

    String modelId();

    int dimensions();

    float[] embed(String text);
}
