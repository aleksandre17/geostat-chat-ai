package com.geostat.ingestion.config;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.platform.parse.CorpusPolicyV2;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs YAML corpus policy to database at startup.
 * 
 * Architecture: YAML is source of truth for configuration,
 * DB is operational state for runtime. This service bridges the two.
 * 
 * Flow: YAML (ops/config/corpus/*.yaml) → DB (ingestion.corpus) → Runtime
 */
@Service
@Profile("db")
public class CorpusPolicySyncService {

    private static final Logger log = LoggerFactory.getLogger(CorpusPolicySyncService.class);

    private final CorpusRepository corpusRepository;
    private final CorpusConfigurationLoader configurationLoader;
    private final ParseProperties parseProperties;

    public CorpusPolicySyncService(
            CorpusRepository corpusRepository,
            CorpusConfigurationLoader configurationLoader,
            ParseProperties parseProperties) {
        this.corpusRepository = corpusRepository;
        this.configurationLoader = configurationLoader;
        this.parseProperties = parseProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!parseProperties.profile().enabled()) {
            log.debug("Parse profile disabled, skipping YAML→DB sync");
            return;
        }
        syncAllCorpora();
    }

    /**
     * Sync all corpora from YAML to DB.
     */
    @Transactional
    public void syncAllCorpora() {
        List<CorpusEntity> corpora = corpusRepository.findAll();
        for (CorpusEntity corpus : corpora) {
            syncCorpus(corpus);
        }
    }

    /**
     * Sync single corpus from YAML policy to DB.
     * YAML seeds + curatedUrls are the source of truth for seed_urls (replace, not merge).
     */
    @Transactional
    public void syncCorpus(CorpusEntity corpus) {
        try {
            CorpusPolicyV2 policy = configurationLoader.policyFor(corpus.getName());

            Set<String> yamlSeeds = new HashSet<>();
            yamlSeeds.addAll(policy.seeds());
            yamlSeeds.addAll(policy.curatedUrls());
            if (yamlSeeds.isEmpty()) {
                log.debug("Corpus '{}' YAML policy has no seeds/curatedUrls — skipping sync", corpus.getName());
                return;
            }

            List<String> nextSeeds = new ArrayList<>(yamlSeeds);
            List<String> previous = corpus.getSeedUrls();
            if (nextSeeds.equals(previous)) {
                log.debug("Corpus '{}' seed_urls already match YAML ({} URLs)", corpus.getName(), nextSeeds.size());
                return;
            }

            corpus.setSeedUrls(nextSeeds);
            corpusRepository.save(corpus);
            log.info(
                    "Synced corpus '{}': seed_urls {} -> {} (YAML seeds + curatedUrls)",
                    corpus.getName(),
                    previous == null ? 0 : previous.size(),
                    nextSeeds.size());
        } catch (Exception e) {
            log.warn("Failed to sync YAML policy for corpus '{}': {}", 
                    corpus.getName(), e.getMessage());
        }
    }

    /**
     * Sync specific corpus by name (for admin API).
     */
    @Transactional
    public void syncCorpusByName(String corpusName) {
        corpusRepository.findByName(corpusName).ifPresent(this::syncCorpus);
    }
}
