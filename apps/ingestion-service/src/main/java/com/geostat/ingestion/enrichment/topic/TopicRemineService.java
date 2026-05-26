package com.geostat.ingestion.enrichment.topic;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.platform.enrichment.TopicMiner;
import com.geostat.platform.enrichment.TopicMiningReport;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class TopicRemineService {

    private final TopicMiner topicMiner;
    private final com.geostat.ingestion.persistence.repository.CorpusRepository corpusRepository;
    private final CatalogRefreshAfterBatch catalogRefreshAfterBatch;

    public TopicRemineService(
            TopicMiner topicMiner,
            com.geostat.ingestion.persistence.repository.CorpusRepository corpusRepository,
            CatalogRefreshAfterBatch catalogRefreshAfterBatch) {
        this.topicMiner = topicMiner;
        this.corpusRepository = corpusRepository;
        this.catalogRefreshAfterBatch = catalogRefreshAfterBatch;
    }

    public TopicMiningReport remineForCorpus(UUID corpusId) {
        TopicMiningReport report = topicMiner.remineForCorpus(corpusId);
        catalogRefreshAfterBatch.refreshIfConfigured("topic-remine");
        return report;
    }

    public TopicMiningReport remineForCorpusName(String corpusName) {
        UUID corpusId = corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new IllegalArgumentException("unknown corpus: " + corpusName))
                .getId();
        return remineForCorpus(corpusId);
    }
}
