package com.geostat.ingestion.enrichment.topic;

import java.util.List;

public record TopicClusterLabels(
        String labelKa, String labelEn, String centroidSummary, List<String> keywords) {}
