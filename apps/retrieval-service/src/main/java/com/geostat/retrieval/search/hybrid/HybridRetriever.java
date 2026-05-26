package com.geostat.retrieval.search.hybrid;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.platform.retrieval.HybridRetrieverPort;
import com.geostat.platform.retrieval.HybridSearchResult;
import com.geostat.platform.retrieval.NamedVectorSearchPort;
import com.geostat.platform.retrieval.RetrievalConfidence;
import com.geostat.retrieval.config.HybridRetrievalProperties;
import com.geostat.retrieval.search.ChunkKeywordSearch;
import com.geostat.retrieval.search.SemanticCrossEncoderReranker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "geostat.retrieval.hybrid",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class HybridRetriever implements HybridRetrieverPort {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    private final HybridRetrievalProperties props;
    private final EmbeddingPort embedding;
    private final NamedVectorSearchPort namedSearch;
    private final Optional<ChunkKeywordSearch> keywordSearch;

    public HybridRetriever(
            HybridRetrievalProperties props,
            EmbeddingPort embedding,
            NamedVectorSearchPort namedSearch,
            ObjectProvider<ChunkKeywordSearch> keywordSearch) {
        this.props = props;
        this.embedding = embedding;
        this.namedSearch = namedSearch;
        this.keywordSearch = Optional.ofNullable(keywordSearch.getIfAvailable());
    }

    @Override
    public HybridSearchResult search(RetrievalQuery query) {
        if (query.text() == null || query.text().isBlank()) {
            return HybridSearchResult.empty();
        }

        String corpus = query.corpusName() != null ? query.corpusName() : "geostat-portal";
        String locale = normalizeLocale(query.locale());
        float[] queryVector = embedding.embed(query.text());

        List<List<RetrievedChunk>> allResults = new ArrayList<>();
        Map<String, Integer> sources = new HashMap<>();

        // Search body vectors (recall-focused)
        if (props.vectors().bodyEnabled()) {
            List<RetrievedChunk> bodyHits = namedSearch.search(
                    corpus, "body", queryVector, locale, props.vectors().bodyTopK());
            allResults.add(bodyHits);
            sources.put("body", bodyHits.size());
        }

        // Search title vectors (precision-focused)
        if (props.vectors().titleEnabled()) {
            List<RetrievedChunk> titleHits = namedSearch.search(
                    corpus, "title", queryVector, locale, props.vectors().titleTopK());
            allResults.add(titleHits);
            sources.put("title", titleHits.size());
        }

        // Search summary vectors (semantic match)
        if (props.vectors().summaryEnabled()) {
            List<RetrievedChunk> summaryHits = namedSearch.search(
                    corpus, "summary", queryVector, locale, props.vectors().summaryTopK());
            allResults.add(summaryHits);
            sources.put("summary", summaryHits.size());
        }

        // BM25 keyword search
        if (props.bm25().enabled() && keywordSearch.isPresent()) {
            try {
                List<RetrievedChunk> bm25Hits =
                        keywordSearch.get().search(query.text(), locale, props.bm25().topK());
                allResults.add(bm25Hits);
                sources.put("bm25", bm25Hits.size());
            } catch (RuntimeException e) {
                log.warn("BM25 search failed, continuing with vector-only: {}", e.getMessage());
            }
        }

        if (allResults.isEmpty()) {
            return HybridSearchResult.empty();
        }

        // RRF fusion
        List<RetrievedChunk> fused = RrfFusion.fuse(allResults, props.rrf().k(), props.rrf().topN());

        // Cross-encoder reranking
        if (props.rerank().crossEncoderEnabled() && !fused.isEmpty()) {
            fused = SemanticCrossEncoderReranker.rerank(
                    embedding, fused, query.text(), props.rerank().topN());
        }

        // MMR diversification
        if (props.mmr().enabled() && !fused.isEmpty()) {
            fused = MmrDiversifier.diversify(fused, props.mmr().lambda(), query.maxChunks());
        } else if (fused.size() > query.maxChunks()) {
            fused = fused.subList(0, query.maxChunks());
        }

        // Assess confidence
        RetrievalConfidence confidence = assessConfidence(fused);

        log.debug("Hybrid search: {} results, confidence={}, sources={}", fused.size(), confidence, sources);

        return new HybridSearchResult(fused, confidence, sources);
    }

    private RetrievalConfidence assessConfidence(List<RetrievedChunk> results) {
        if (results.isEmpty()) {
            return RetrievalConfidence.NONE;
        }
        float topScore = (float) results.get(0).score();
        float secondScore = results.size() > 1 ? (float) results.get(1).score() : 0f;
        return RetrievalConfidence.assess(topScore, secondScore);
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        return locale.split("-")[0].toLowerCase();
    }
}
