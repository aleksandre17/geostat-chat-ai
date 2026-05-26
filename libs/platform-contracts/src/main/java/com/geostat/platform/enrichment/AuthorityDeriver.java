package com.geostat.platform.enrichment;

import java.util.UUID;

/** Port — corpus-level authority recomputation (RAG-U01e). */
public interface AuthorityDeriver {

    void recomputeForCorpus(UUID corpusId);
}
