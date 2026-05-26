package com.geostat.platform.enrichment;

import java.util.UUID;

/** Port — corpus-level topic discovery via k-means + cluster labels (RAG-U01g). */
public interface TopicMiner {

    TopicMiningReport remineForCorpus(UUID corpusId);
}
