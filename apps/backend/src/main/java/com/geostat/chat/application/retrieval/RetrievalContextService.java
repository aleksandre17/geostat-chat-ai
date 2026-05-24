package com.geostat.chat.application.retrieval;

import com.geostat.chat.infrastructure.retrieval.RetrievalClientProperties;
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

    public List<RetrievedChunk> retrieve(String userMessage, String locale) {
        return search(userMessage, locale, properties.maxChunks());
    }

    /** Broader corpus context when catalog links are empty (clarification path). */
    public List<RetrievedChunk> retrieveForClarification(String userMessage, String locale) {
        return search(userMessage, locale, properties.clarificationMaxChunks());
    }

    private List<RetrievedChunk> search(String userMessage, String locale, int maxChunks) {
        if (!properties.enabled() || userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        String resolved = localeOrDefault(locale);
        return retrievalPort.search(new RetrievalQuery(
                userMessage, resolved, maxChunks, properties.defaultCorpus()));
    }

    private static String localeOrDefault(String locale) {
        if (locale == null || locale.isBlank()) {
            return "ka";
        }
        return locale.split("-")[0].toLowerCase();
    }
}
