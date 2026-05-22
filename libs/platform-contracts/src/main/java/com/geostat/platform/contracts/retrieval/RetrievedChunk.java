package com.geostat.platform.contracts.retrieval;

/**
 * One retrieved passage for LLM context (skeleton).
 */
public record RetrievedChunk(
        String documentId,
        String sourceUrl,
        String text,
        double score
) {}
