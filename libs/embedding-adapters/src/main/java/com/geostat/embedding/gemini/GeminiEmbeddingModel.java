package com.geostat.embedding.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.embedding.EmbeddingPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeminiEmbeddingModel implements EmbeddingPort {

    public static final String DEFAULT_MODEL = "gemini-embedding-001";
    public static final int DIMENSIONS = 768;

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiEmbeddingModel(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public GeminiEmbeddingModel(String apiKey, String model) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static final int BATCH_MAX = 100;

    @Override
    public float[][] embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return new float[0][];
        }

        float[][] results = new float[texts.size()][];
        int offset = 0;
        while (offset < texts.size()) {
            int end = Math.min(offset + BATCH_MAX, texts.size());
            List<String> batch = texts.subList(offset, end);
            float[][] batchResult = callGeminiBatchEmbed(batch);
            System.arraycopy(batchResult, 0, results, offset, batchResult.length);
            offset = end;
        }
        return results;
    }

    @Override
    public float[] embed(String text) {
        if (apiKey.isBlank()) {
            throw new GeminiEmbeddingException("GEMINI_API_KEY is required for gemini embedding provider", null);
        }
        try {
            Map<String, Object> body = Map.of(
                    "content", Map.of("parts", List.of(Map.of("text", text == null ? "" : text))),
                    "outputDimensionality", dimensions());
            String json = objectMapper.writeValueAsString(body);
            URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":embedContent?key=" + apiKey);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new GeminiEmbeddingException(
                        "Gemini embed HTTP " + response.statusCode() + ": " + response.body(), null);
            }
            JsonNode values = objectMapper.readTree(response.body()).path("embedding").path("values");
            if (!values.isArray() || values.isEmpty()) {
                throw new GeminiEmbeddingException("Gemini embed response missing embedding.values", null);
            }
            List<Float> floats = new ArrayList<>(values.size());
            for (JsonNode value : values) {
                floats.add((float) value.asDouble());
            }
            float[] vector = new float[floats.size()];
            for (int i = 0; i < floats.size(); i++) {
                vector[i] = floats.get(i);
            }
            return vector;
        } catch (GeminiEmbeddingException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiEmbeddingException("Gemini embed interrupted", e);
        } catch (Exception e) {
            throw new GeminiEmbeddingException("Gemini embed failed", e);
        }
    }

    private float[][] callGeminiBatchEmbed(List<String> texts) {
        if (apiKey.isBlank()) {
            throw new GeminiEmbeddingException("GEMINI_API_KEY is required for gemini embedding provider", null);
        }
        try {
            List<Map<String, Object>> requests = new ArrayList<>(texts.size());
            for (String text : texts) {
                requests.add(Map.of(
                        "model", "models/" + model,
                        "content", Map.of("parts", List.of(Map.of("text", text == null ? "" : text))),
                        "outputDimensionality", dimensions()));
            }
            Map<String, Object> body = Map.of("requests", requests);
            String json = objectMapper.writeValueAsString(body);
            URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":batchEmbedContents?key=" + apiKey);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new GeminiEmbeddingException(
                        "Gemini batch embed HTTP " + response.statusCode() + ": " + response.body(), null);
            }
            JsonNode embeddings = objectMapper.readTree(response.body()).path("embeddings");
            if (!embeddings.isArray() || embeddings.size() != texts.size()) {
                throw new GeminiEmbeddingException("Gemini batch embed response missing or mismatched embeddings", null);
            }
            float[][] results = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                JsonNode values = embeddings.get(i).path("values");
                if (!values.isArray() || values.isEmpty()) {
                    throw new GeminiEmbeddingException("Gemini batch embed response missing embedding.values", null);
                }
                float[] vector = new float[values.size()];
                for (int j = 0; j < values.size(); j++) {
                    vector[j] = (float) values.get(j).asDouble();
                }
                results[i] = vector;
            }
            return results;
        } catch (GeminiEmbeddingException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiEmbeddingException("Gemini batch embed interrupted", e);
        } catch (Exception e) {
            throw new GeminiEmbeddingException("Gemini batch embed failed", e);
        }
    }
}
