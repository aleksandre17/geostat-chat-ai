package com.geostat.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.retrieval")
public record RetrievalProperties(
        String defaultCollection,
        Qdrant qdrant,
        Embedding embedding,
        Search search,
        Keyword keyword,
        Cache cache,
        Rerank rerank) {

    public RetrievalProperties {
        if (search == null) {
            search = Search.defaults();
        }
        if (keyword == null) {
            keyword = Keyword.defaults();
        }
        if (cache == null) {
            cache = Cache.defaults();
        }
        if (rerank == null) {
            rerank = Rerank.defaults();
        }
    }

    public record Qdrant(String url, int grpcPort, boolean useTls, String apiKey) {}

    public record Embedding(
            String provider,
            String modelId,
            int vectorSize,
            String geminiApiKey,
            String geminiModel,
            String ollamaBaseUrl,
            String ollamaModel) {}

    public record Search(int vectorOverFetch, boolean localeFilter) {
        public static Search defaults() {
            return new Search(3, true);
        }
    }

    public record Keyword(boolean enabled) {
        public static Keyword defaults() {
            return new Keyword(true);
        }
    }

    public record Cache(boolean enabled, String backend, int ttlMinutes, int maxEntries) {
        public static Cache defaults() {
            return new Cache(true, "local", 10, 512);
        }
    }

    public record Rerank(boolean semanticCrossEncoder) {
        public static Rerank defaults() {
            return new Rerank(true);
        }
    }
}
