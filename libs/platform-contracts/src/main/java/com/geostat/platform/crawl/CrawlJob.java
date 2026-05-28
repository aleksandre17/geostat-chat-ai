package com.geostat.platform.crawl;

import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.ParseProfile;
import java.util.UUID;

/**
 * Immutable descriptor for a single corpus crawl run.
 * Created by {@link CrawlOrchestrator} from a {@code *-policy.yaml} file.
 *
 * <p>Carries all configuration needed to start and execute the crawl without
 * additional DB lookups or YAML reads at execution time.
 */
public record CrawlJob(
        String corpusName,
        UUID corpusId,
        CorpusPolicyV2 policy,
        ParseProfile profile) {}
