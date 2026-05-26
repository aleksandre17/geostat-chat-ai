package com.geostat.platform.contracts.ingestion;

import java.util.UUID;

/** Async signal that a document body is parsed and ready for Layer 2 enrichment (RAG-U01). */
public record DocumentParsedEvent(UUID documentId, UUID corpusId) {}
