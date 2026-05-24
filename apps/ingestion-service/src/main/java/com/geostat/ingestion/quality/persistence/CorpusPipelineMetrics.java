package com.geostat.ingestion.quality.persistence;

/** Chunk + vector index coverage for one corpus. */
public record CorpusPipelineMetrics(long totalChunks, long documentsWithChunks, long indexedChunks) {}
