package com.geostat.ingestion.parse.profile;

import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.platform.parse.CorpusPolicyV2;
import org.springframework.stereotype.Component;

/** Routes URL filtering to YAML policy when parse profile is enabled, else legacy CorpusPolicy. */
@Component
public class RoutingUrlFilter {

    private final ParseProperties parseProperties;
    private final CorpusConfigurationLoader configurationLoader;
    private final PolicyUrlFilter policyUrlFilter;

    public RoutingUrlFilter(
            ParseProperties parseProperties,
            CorpusConfigurationLoader configurationLoader,
            PolicyUrlFilter policyUrlFilter) {
        this.parseProperties = parseProperties;
        this.configurationLoader = configurationLoader;
        this.policyUrlFilter = policyUrlFilter;
    }

    public boolean shouldEnqueue(String url, CorpusEntity corpus) {
        if (!parseProperties.profile().enabled()) {
            return CorpusPolicy.isUrlAllowed(corpus, url);
        }
        CorpusPolicyV2 policy = configurationLoader.policyFor(corpus.getName());
        return policyUrlFilter.shouldEnqueue(url, policy);
    }
}
