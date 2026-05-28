package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.springframework.stereotype.Component;

/** Strips RAG explanations that do not cite indexed passage text (P6). */
@Component
public class ResponseGroundingEnforcer {

    private final ExplanationGroundingVerifier verifier;

    public ResponseGroundingEnforcer(ExplanationGroundingVerifier verifier) {
        this.verifier = verifier;
    }

    public List<LinkedExplanation> enforce(List<LinkedExplanation> items, List<RetrievedChunk> ragChunks) {
        if (items == null || items.isEmpty() || ragChunks == null || ragChunks.isEmpty()) {
            return items != null ? items : List.of();
        }
        return items.stream().map(item -> enforceItem(item, ragChunks)).toList();
    }

    private LinkedExplanation enforceItem(LinkedExplanation item, List<RetrievedChunk> ragChunks) {
        if (item == null || item.explanation() == null || item.explanation().isBlank()) {
            return item;
        }
        if (item.link() == null || !"rag".equals(item.link().sourceType())) {
            return item;
        }
        if (verifier.explanationCitesChunk(item.explanation(), item.link().url(), ragChunks)) {
            return item;
        }
        return new LinkedExplanation(null, item.link());
    }
}
