package com.geostat.platform.enrichment;

/** Result of corpus-level topic remine (RAG-U01g). */
public record TopicMiningReport(int clustersCreated, int documentsAssigned, int documentsSkipped) {}
