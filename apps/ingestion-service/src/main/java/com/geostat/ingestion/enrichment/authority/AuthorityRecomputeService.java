package com.geostat.ingestion.enrichment.authority;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.platform.enrichment.AuthorityDeriver;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class AuthorityRecomputeService {

    private final AuthorityDeriver authorityDeriver;
    private final com.geostat.ingestion.persistence.repository.CorpusRepository corpusRepository;
    private final CatalogRefreshAfterBatch catalogRefreshAfterBatch;

    public AuthorityRecomputeService(
            AuthorityDeriver authorityDeriver,
            com.geostat.ingestion.persistence.repository.CorpusRepository corpusRepository,
            CatalogRefreshAfterBatch catalogRefreshAfterBatch) {
        this.authorityDeriver = authorityDeriver;
        this.corpusRepository = corpusRepository;
        this.catalogRefreshAfterBatch = catalogRefreshAfterBatch;
    }

    public void recomputeForCorpus(UUID corpusId) {
        authorityDeriver.recomputeForCorpus(corpusId);
        catalogRefreshAfterBatch.refreshIfConfigured("authority-recompute");
    }

    public void recomputeForCorpusName(String corpusName) {
        UUID corpusId = corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new IllegalArgumentException("unknown corpus: " + corpusName))
                .getId();
        recomputeForCorpus(corpusId);
    }
}
