package com.geostat.platform.contracts.feedback;

import java.util.UUID;

/**
 * Published by chat-service when user feedback affects a document's relevance score.
 * Consumed by ingestion-service to update curation_override.
 */
public record FeedbackScoreEvent(
        UUID documentId, UUID corpusId, double scoreDelta, String feedbackType) {}
