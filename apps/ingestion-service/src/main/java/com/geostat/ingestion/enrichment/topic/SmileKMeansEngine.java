package com.geostat.ingestion.enrichment.topic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import smile.clustering.KMeans;

public final class SmileKMeansEngine {

    private static final int MAX_ITER = 100;
    private static final double TOLERANCE = 1E-4;

    private SmileKMeansEngine() {}

    public record ClusteringResult(int[] labels, float[][] centroids, List<UUID> documentIds) {}

    public static ClusteringResult cluster(Map<UUID, float[]> embeddings, int clusterCount) {
        if (embeddings.size() < clusterCount || clusterCount < 1) {
            throw new IllegalArgumentException("insufficient embeddings for k=" + clusterCount);
        }
        List<UUID> documentIds = new ArrayList<>(embeddings.keySet());
        double[][] data = new double[documentIds.size()][];
        for (int i = 0; i < documentIds.size(); i++) {
            data[i] = toDoubleArray(embeddings.get(documentIds.get(i)));
        }

        KMeans model = KMeans.fit(data, clusterCount, MAX_ITER, TOLERANCE);
        int[] labels = model.y;
        float[][] centroids = toFloatCentroids(model.centroids);
        return new ClusteringResult(labels, centroids, documentIds);
    }

    static double[] toDoubleArray(float[] vector) {
        double[] data = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            data[i] = vector[i];
        }
        return data;
    }

    static float[][] toFloatCentroids(double[][] centers) {
        float[][] centroids = new float[centers.length][];
        for (int i = 0; i < centers.length; i++) {
            centroids[i] = new float[centers[i].length];
            for (int j = 0; j < centers[i].length; j++) {
                centroids[i][j] = (float) centers[i][j];
            }
        }
        return centroids;
    }

    public static Map<Integer, List<UUID>> groupMembers(ClusteringResult result) {
        Map<Integer, List<UUID>> members = new LinkedHashMap<>();
        for (int i = 0; i < result.labels().length; i++) {
            int cluster = result.labels()[i];
            members.computeIfAbsent(cluster, ignored -> new ArrayList<>()).add(result.documentIds().get(i));
        }
        return members;
    }
}
