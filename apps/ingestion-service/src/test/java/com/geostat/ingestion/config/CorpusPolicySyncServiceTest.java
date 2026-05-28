package com.geostat.ingestion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.profile.CorpusCrawlLimits;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.SubdomainMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorpusPolicySyncServiceTest {

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private CorpusConfigurationLoader configurationLoader;

    private CorpusPolicySyncService service;

    @BeforeEach
    void setUp() {
        service = new CorpusPolicySyncService(
                corpusRepository,
                configurationLoader,
                new ParseProperties(
                        new ParseProperties.Profile(true),
                        "ops/config/corpus",
                        "ops/eval/corpus-quality-gate.yaml"));
    }

    @Test
    void syncCorpus_overridesRateLimitMsFromYaml() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setName("geostat-portal");
        corpus.setSeedUrls(List.of("https://www.geostat.ge/ka"));
        Map<String, Object> policy = new HashMap<>();
        policy.put("rateLimitMs", 500);
        corpus.setPolicy(policy);

        when(configurationLoader.policyFor("geostat-portal")).thenReturn(yamlPolicy());
        when(configurationLoader.crawlLimitsFor("geostat-portal"))
                .thenReturn(new CorpusCrawlLimits(8, 1000, 200));

        service.syncCorpus(corpus);

        assertThat(corpus.getPolicy().get("rateLimitMs")).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> crawl = (Map<String, Object>) corpus.getPolicy().get("crawl");
        assertThat(crawl.get("workerThreads")).isEqualTo(8);
        assertThat(crawl.get("crawlDelay")).isEqualTo(1000);
        verify(corpusRepository).save(corpus);
    }

    @Test
    void syncCorpus_skipsSaveWhenSeedsAndLimitsUnchanged() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setName("geostat-portal");
        corpus.setSeedUrls(List.of("https://www.geostat.ge/ka"));
        Map<String, Object> crawl = new HashMap<>();
        crawl.put("workerThreads", 8);
        crawl.put("crawlDelay", 1000);
        Map<String, Object> policy = new HashMap<>();
        policy.put("rateLimitMs", 200);
        policy.put("crawl", crawl);
        corpus.setPolicy(policy);

        when(configurationLoader.policyFor("geostat-portal")).thenReturn(yamlPolicy());
        when(configurationLoader.crawlLimitsFor("geostat-portal"))
                .thenReturn(new CorpusCrawlLimits(8, 1000, 200));

        service.syncCorpus(corpus);

        verifyNoInteractions(corpusRepository);
    }

    private static CorpusPolicyV2 yamlPolicy() {
        return new CorpusPolicyV2(
                "geostat-portal",
                List.of("https://www.geostat.ge/ka"),
                List.of(),
                List.of("www.geostat.ge"),
                SubdomainMode.all,
                List.of(),
                List.of(),
                List.of());
    }
}
