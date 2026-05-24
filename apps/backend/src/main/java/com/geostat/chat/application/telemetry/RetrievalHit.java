package com.geostat.chat.application.telemetry;

/** RAG chunk metadata attached to a chat turn (B-19 / eval). */
public record RetrievalHit(String documentId, String sourceUrl, double score) {}
