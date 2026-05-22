package Chatbot.retrieval;

import com.geostat.platform.contracts.retrieval.RetrievalPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RetrievalContextService {

    private final RetrievalPort retrievalPort;
    private final RetrievalClientProperties properties;

    public RetrievalContextService(RetrievalPort retrievalPort, RetrievalClientProperties properties) {
        this.retrievalPort = retrievalPort;
        this.properties = properties;
    }

    public List<RetrievedChunk> retrieve(String userMessage, boolean isGeorgian) {
        if (!properties.enabled() || userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        String locale = isGeorgian ? "ka" : "en";
        return retrievalPort.search(new RetrievalQuery(
                userMessage, locale, properties.maxChunks(), properties.defaultCorpus()));
    }
}
