package com.geostat.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.retrieval.hybrid")
public record HybridRetrievalProperties(
        boolean enabled,
        Vectors vectors,
        Bm25 bm25,
        Rrf rrf,
        Rerank rerank,
        Mmr mmr) {

    public HybridRetrievalProperties {
        if (vectors == null) vectors = Vectors.defaults();
        if (bm25 == null) bm25 = Bm25.defaults();
        if (rrf == null) rrf = Rrf.defaults();
        if (rerank == null) rerank = Rerank.defaults();
        if (mmr == null) mmr = Mmr.defaults();
    }

    public record Vectors(
            boolean bodyEnabled,
            int bodyTopK,
            boolean titleEnabled,
            int titleTopK,
            boolean summaryEnabled,
            int summaryTopK) {

        public static Vectors defaults() {
            return new Vectors(true, 40, true, 20, true, 20);
        }
    }

    public record Bm25(boolean enabled, int topK) {
        public static Bm25 defaults() {
            return new Bm25(true, 20);
        }
    }

    public record Rrf(int k, int topN) {
        public static Rrf defaults() {
            return new Rrf(60, 50);
        }
    }

    public record Rerank(boolean crossEncoderEnabled, int topN) {
        public static Rerank defaults() {
            return new Rerank(true, 10);
        }
    }

    public record Mmr(boolean enabled, float lambda) {
        public static Mmr defaults() {
            return new Mmr(true, 0.7f);
        }
    }
}
