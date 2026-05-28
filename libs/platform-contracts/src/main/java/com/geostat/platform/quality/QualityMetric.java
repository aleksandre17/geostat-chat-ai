package com.geostat.platform.quality;

import java.util.UUID;

/**
 * Port: computes a single quality metric for a given corpus.
 * Implementations are named Spring beans referenced by id in corpus-quality-gate.yaml.
 */
public interface QualityMetric {

    /** Unique metric id — must match the gate id in quality gate YAML. */
    String id();

    /**
     * Compute the metric value for the given corpus.
     *
     * @param corpusId corpus UUID
     * @return computed double value (ratio, count, or percentage)
     */
    double compute(UUID corpusId);

    /** Human-readable description of what this metric measures. */
    String description();
}
