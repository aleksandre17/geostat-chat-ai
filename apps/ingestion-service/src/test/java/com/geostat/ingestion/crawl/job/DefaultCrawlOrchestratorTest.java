package com.geostat.ingestion.crawl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.platform.contracts.ingestion.IngestionJobStatus;
import com.geostat.platform.crawl.CrawlJob;
import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.SubdomainMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DefaultCrawlOrchestratorTest {

    @Mock
    CorpusConfigurationLoader configLoader;

    @Mock
    CorpusRepository corpusRepository;

    @Mock
    CrawlJobService crawlJobService;

    DefaultCrawlOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        CrawlProperties props = new CrawlProperties(2, 6, 10L);
        orchestrator = new DefaultCrawlOrchestrator(configLoader, corpusRepository, crawlJobService, props);
    }

    @Test
    void discoverJobs_returnsOneJobPerPolicyFile() {
        CorpusPolicyV2 policy1 = policy("geostat-portal");
        CorpusPolicyV2 policy2 = policy("census2024");
        when(configLoader.loadAllPolicies()).thenReturn(List.of(policy1, policy2));

        CorpusEntity e1 = corpusEntity("geostat-portal");
        CorpusEntity e2 = corpusEntity("census2024");
        when(corpusRepository.findByName("geostat-portal")).thenReturn(Optional.of(e1));
        when(corpusRepository.findByName("census2024")).thenReturn(Optional.of(e2));
        when(configLoader.parseProfileFor(any())).thenReturn(null);

        List<CrawlJob> jobs = orchestrator.discoverJobs();

        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(CrawlJob::corpusName)
                .containsExactlyInAnyOrder("geostat-portal", "census2024");
    }

    @Test
    void discoverJobs_skipsCorpus_whenNotInDb() {
        CorpusPolicyV2 policy = policy("orphan-corpus");
        when(configLoader.loadAllPolicies()).thenReturn(List.of(policy));
        when(corpusRepository.findByName("orphan-corpus")).thenReturn(Optional.empty());

        List<CrawlJob> jobs = orchestrator.discoverJobs();

        assertThat(jobs).isEmpty();
    }

    @Test
    void executeSingle_callsCrawlJobServiceAndWaitsForCompletion() {
        CrawlJob job = new CrawlJob("geostat-portal", UUID.randomUUID(), null, null);
        when(crawlJobService.startJob(any())).thenReturn(
                new IngestionJobStatus("00000000-0000-4000-8000-000000000099", "pending", "queued"));
        when(crawlJobService.getJobStatus(UUID.fromString("00000000-0000-4000-8000-000000000099")))
                .thenReturn(new IngestionJobStatus(
                        "00000000-0000-4000-8000-000000000099", "completed", "done"));

        orchestrator.executeSingle(job);

        verify(crawlJobService).startJob(argThat(req ->
                "geostat-portal".equals(req.corpusName())
                        && req.seedUrl() == null
                        && !req.fullRecrawl()
                        && req.renderMode() == null));
        verify(crawlJobService).getJobStatus(UUID.fromString("00000000-0000-4000-8000-000000000099"));
    }

    @Test
    void executeAll_noOp_whenJobListEmpty() {
        orchestrator.executeAll(List.of());
        verifyNoInteractions(crawlJobService);
    }

    private static CorpusPolicyV2 policy(String name) {
        return new CorpusPolicyV2(
                name, List.of(), List.of(), List.of(), SubdomainMode.all, List.of(), List.of(), List.of());
    }

    private CorpusEntity corpusEntity(String name) {
        CorpusEntity e = new CorpusEntity();
        e.setId(UUID.randomUUID());
        e.setName(name);
        return e;
    }
}
