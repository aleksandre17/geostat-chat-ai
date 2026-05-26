package com.geostat.platform.enrichment;

import java.util.Optional;
import java.util.UUID;

/** Port — ka↔en locale pair resolution (RAG-U01d). */
public interface LocalePairDeriver {

    Optional<UUID> findPair(DocumentContext document);
}
