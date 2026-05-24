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
}
