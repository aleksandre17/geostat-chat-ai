package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;

/** Retrieval hot-cache backend (Caffeine local or Redis). */
public interface RetrievalCacheBackend {

    List<RetrievedChunk> get(String key);

    void put(String key, List<RetrievedChunk> hits);
}
