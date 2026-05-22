package com.geostat.platform.contracts.ingestion;

import java.util.UUID;

/**
 * Async signal that a crawled document is ready for vector indexing (P5 RabbitMQ).
 */
public record DocumentIndexEvent(UUID documentId, UUID corpusId) {}
