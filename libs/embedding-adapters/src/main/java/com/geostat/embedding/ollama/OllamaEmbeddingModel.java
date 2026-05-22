package com.geostat.embedding.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.embedding.EmbeddingPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class OllamaEmbeddingModel implements EmbeddingPort {

    public static final String DEFAULT_MODEL = "nomic-embed-text";
    public static final int DIMENSIONS = 768;

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingModel(String baseUrl) {
        this(baseUrl, DEFAULT_MODEL);
    }

    public OllamaEmbeddingModel(String baseUrl, String model) {
        String normalized = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:11434" : baseUrl.trim();
        this.baseUrl = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
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
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", text == null ? "" : text));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new OllamaEmbeddingException(
                        "Ollama embed HTTP " + response.statusCode() + ": " + response.body(), null);
            }
            JsonNode embedding = objectMapper.readTree(response.body()).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new OllamaEmbeddingException("Ollama embed response missing embedding array", null);
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (OllamaEmbeddingException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OllamaEmbeddingException("Ollama embed interrupted", e);
        } catch (Exception e) {
            throw new OllamaEmbeddingException("Ollama embed failed", e);
        }
    }
}
