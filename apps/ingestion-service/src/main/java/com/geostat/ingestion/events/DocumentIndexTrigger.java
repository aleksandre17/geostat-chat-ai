package com.geostat.ingestion.events;

import java.util.UUID;

/** Port: request vector indexing for a persisted document. */
public interface DocumentIndexTrigger {

    void requestIndex(UUID documentId, UUID corpusId);
}
