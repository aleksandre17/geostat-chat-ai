package com.geostat.retrieval.api;

import com.geostat.platform.contracts.retrieval.RetrievalPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retrieval")
public class RetrievalController {

    private final RetrievalPort retrievalPort;

    public RetrievalController(RetrievalPort retrievalPort) {
        this.retrievalPort = retrievalPort;
    }

    @PostMapping("/search")
    public List<RetrievedChunk> search(@RequestBody RetrievalQuery query) {
        return retrievalPort.search(query);
    }
}
