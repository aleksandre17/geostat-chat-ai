package com.geostat.platform.enrichment;

import java.util.Optional;
import java.util.UUID;

/** Port — assign document to nearest existing topic cluster (RAG-U01g). */
public interface TopicAssigner {

    Optional<UUID> assignNearest(UUID documentId);
}
