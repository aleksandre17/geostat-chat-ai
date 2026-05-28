package com.geostat.retrieval.search.hybrid;

import com.geostat.platform.contracts.retrieval.RetrievalPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.HybridRetrieverPort;
import com.geostat.platform.retrieval.HybridSearchResult;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Adapts {@link HybridRetrieverPort} to the sync {@link RetrievalPort} used by the HTTP API. */
@Service
@Primary
@ConditionalOnProperty(
        prefix = "geostat.retrieval.hybrid",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class HybridRetrievalService implements RetrievalPort {

    private final HybridRetrieverPort hybridRetriever;

    public HybridRetrievalService(HybridRetrieverPort hybridRetriever) {
        this.hybridRetriever = hybridRetriever;
    }

    @Override
    public List<RetrievedChunk> search(RetrievalQuery query) {
        HybridSearchResult result = hybridRetriever.search(query);
        return result.chunks();
    }
}
