package com.geostat.ingestion.quality.persistence;

/** Aggregated document counts for one corpus (Postgres ingestion schema). */
public record CorpusDocumentMetrics(
        long totalDocuments,
        long parsedDocuments,
        long failedDocuments,
        long emptyBodyDocuments) {}
