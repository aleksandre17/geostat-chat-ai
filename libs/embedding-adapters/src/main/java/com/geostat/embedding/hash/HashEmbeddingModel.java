package com.geostat.embedding.hash;

import com.geostat.embedding.EmbeddingPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashEmbeddingModel implements EmbeddingPort {

    public static final String MODEL_ID = "hash-v1";
    public static final int DIMENSIONS = 384;

    @Override
    public String modelId() {
        return MODEL_ID;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(String text) {
        byte[] digest = sha256((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            int value = digest[i % digest.length] & 0xFF;
            vector[i] = (value / 127.5f) - 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private static void normalize(float[] vector) {
        double sumSquares = 0;
        for (float value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares == 0) {
            return;
        }
        float scale = (float) (1.0 / Math.sqrt(sumSquares));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
