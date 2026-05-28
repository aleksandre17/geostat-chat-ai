package com.geostat.retrieval.search;

import com.geostat.platform.contracts.retrieval.RetrievalPort;
import com.geostat.platform.contracts.retrieval.RetrievalQuery;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import com.geostat.retrieval.config.RetrievalProperties;
import com.geostat.embedding.EmbeddingPort;
import com.geostat.qdrant.QdrantOperationException;
import com.geostat.qdrant.VectorCollectionNaming;
import com.geostat.retrieval.index.qdrant.QdrantSearchStore;
import java.util.List;
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
        havingValue = "false",
        matchIfMissing = true)
public class QdrantRetrievalService implements RetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(QdrantRetrievalService.class);

    private final RetrievalProperties properties;
    private final EmbeddingPort embedding;
    private final QdrantSearchStore searchStore;
    private final Optional<ChunkKeywordSearch> keywordSearch;
    private final Optional<RetrievalSearchCache> searchCache;

    public QdrantRetrievalService(
            RetrievalProperties properties,
            EmbeddingPort embedding,
            QdrantSearchStore searchStore,
            ObjectProvider<ChunkKeywordSearch> keywordSearch,
            ObjectProvider<RetrievalSearchCache> searchCache) {
        this.properties = properties;
        this.embedding = embedding;
        this.searchStore = searchStore;
        this.keywordSearch = Optional.ofNullable(keywordSearch.getIfAvailable());
        this.searchCache = Optional.ofNullable(searchCache.getIfAvailable());
    }

    @Override
    public List<RetrievedChunk> search(RetrievalQuery query) {
        if (query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        if (properties.cache().enabled() && searchCache.isPresent()) {
            List<RetrievedChunk> cached = searchCache.get().get(query);
            if (cached != null) {
                return cached;
            }
        }
        List<RetrievedChunk> results = executeSearch(query);
        if (properties.cache().enabled() && searchCache.isPresent()) {
            searchCache.get().put(query, results);
        }
        return results;
    }

    private List<RetrievedChunk> executeSearch(RetrievalQuery query) {
        String collection = resolveCollection(query);
        float[] vector = embedding.embed(query.text());
        int fetch = Math.max(query.maxChunks(), query.maxChunks() * properties.search().vectorOverFetch());
        String locale = normalizeLocale(query.locale());
        try {
            List<RetrievedChunk> hits = properties.search().localeFilter() && locale != null
                    ? searchStore.search(collection, vector, fetch, locale)
                    : searchStore.search(collection, vector, fetch);
            List<RetrievedChunk> reranked = RetrievalReranker.rerank(hits, query.text(), locale, fetch);
            if (properties.rerank().semanticCrossEncoder()) {
                reranked = SemanticCrossEncoderReranker.rerank(embedding, reranked, query.text(), fetch);
            }
            List<RetrievedChunk> limited = reranked.size() > query.maxChunks()
                    ? reranked.subList(0, query.maxChunks())
                    : reranked;
            if (properties.keyword().enabled() && keywordSearch.isPresent()) {
                try {
                    List<RetrievedChunk> keywordHits =
                            keywordSearch.get().search(query.text(), locale, query.maxChunks());
                    return ChunkKeywordSearch.mergeVectorAndKeyword(limited, keywordHits, query.maxChunks());
                } catch (RuntimeException e) {
                    log.warn("keyword search unavailable, vector-only: {}", e.getMessage());
                }
            }
            return limited;
        } catch (QdrantOperationException e) {
            log.warn("retrieval search failed for collection {}: {}", collection, e.getMessage());
            return List.of();
        }
    }

    private String resolveCollection(RetrievalQuery query) {
        if (query.corpusName() != null && !query.corpusName().isBlank()) {
            return VectorCollectionNaming.collectionForName(query.corpusName());
        }
        return properties.defaultCollection();
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        return locale.split("-")[0].toLowerCase();
    }
}
