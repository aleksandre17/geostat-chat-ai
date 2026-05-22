package com.geostat.platform.contracts.retrieval;

import java.util.List;

/**
 * Port: chat-api → retrieval (sync). Implementation lives in retrieval-service.
 */
public interface RetrievalPort {

    List<RetrievedChunk> search(RetrievalQuery query);
}
