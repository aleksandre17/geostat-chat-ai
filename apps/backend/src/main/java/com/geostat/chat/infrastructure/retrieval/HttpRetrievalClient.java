package com.geostat.chat.infrastructure.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievalPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public class HttpRetrievalClient implements RetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(HttpRetrievalClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    public HttpRetrievalClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public List<RetrievedChunk> search(RetrievalQuery query) {
        try {
            return webClient
                    .post()
                    .uri("/api/v1/retrieval/search")
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<RetrievedChunk>>() {})
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            log.warn("retrieval-service HTTP {}: {}", e.getStatusCode(), e.getMessage());
            return List.of();
        } catch (RuntimeException e) {
            log.warn("retrieval-service call failed: {}", e.getMessage());
            return List.of();
        }
    }
}
