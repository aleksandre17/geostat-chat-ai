package com.geostat.ingestion.enrichment.authority;

import com.geostat.ingestion.persistence.repository.CorpusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "geostat.ingestion.enrichment",
        name = "authority-scheduler-enabled",
        havingValue = "true")
public class AuthorityBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuthorityBatchScheduler.class);

    private final CorpusRepository corpusRepository;
    private final AuthorityRecomputeService authorityRecomputeService;

    public AuthorityBatchScheduler(
            CorpusRepository corpusRepository, AuthorityRecomputeService authorityRecomputeService) {
        this.corpusRepository = corpusRepository;
        this.authorityRecomputeService = authorityRecomputeService;
    }

    @Scheduled(cron = "${geostat.ingestion.enrichment.authority-cron:0 0 3 * * *}")
    public void recomputeAllCorpora() {
        corpusRepository.findAll().forEach(corpus -> {
            try {
                authorityRecomputeService.recomputeForCorpus(corpus.getId());
            } catch (RuntimeException e) {
                log.warn("authority recompute failed for corpus {}: {}", corpus.getName(), e.getMessage());
            }
        });
    }
}
