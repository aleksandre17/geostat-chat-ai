package com.geostat.chat.domain.catalog;

import java.util.UUID;

/** Approved topic cluster label from ingestion.topic_cluster (+ optional curation rename). */
public record DerivedTopicCluster(UUID id, String labelKa, String labelEn) {

    public String label(boolean georgian) {
        if (georgian) {
            return labelKa != null && !labelKa.isBlank() ? labelKa : fallbackEn();
        }
        return fallbackEn();
    }

    private String fallbackEn() {
        return labelEn != null && !labelEn.isBlank() ? labelEn : "General";
    }
}
