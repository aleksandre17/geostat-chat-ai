package com.geostat.ingestion.crawl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.crawl.runner.CrawlRunner;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.model.CorpusStatus;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;
import com.geostat.platform.contracts.ingestion.IngestionJobRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlJobServiceTest {

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private CrawlRunRepository crawlRunRepository;

    @Mock
    private UrlFrontierRepository urlFrontierRepository;

    @Mock
    private CrawlRunner crawlRunner;

    private CrawlJobService service;

    @BeforeEach
    void setUp() {
        service = new CrawlJobService(corpusRepository, crawlRunRepository, urlFrontierRepository, crawlRunner);
    }

    @Test
    void startJobCreatesRunAndSeedsFrontier() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());
        corpus.setName("geostat-portal");
        corpus.setSeedUrls(List.of("https://www.geostat.ge/ka"));
        corpus.setStatus(CorpusStatus.active);

        when(corpusRepository.findByName("geostat-portal")).thenReturn(Optional.of(corpus));
        when(crawlRunRepository.save(any(CrawlRunEntity.class))).thenAnswer(invocation -> {
            CrawlRunEntity run = invocation.getArgument(0);
            run.setId(UUID.fromString("00000000-0000-4000-8000-000000000099"));
            return run;
        });
        when(urlFrontierRepository.existsByCrawlRun_IdAndUrlHash(any(), any())).thenReturn(false);

        var status = service.startJob(new IngestionJobRequest(null, null, false));

        assertThat(status.jobId()).isEqualTo("00000000-0000-4000-8000-000000000099");
        assertThat(status.state()).isEqualTo(CrawlRunStatus.pending.name());

        ArgumentCaptor<CrawlRunEntity> runCaptor = ArgumentCaptor.forClass(CrawlRunEntity.class);
        verify(crawlRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getCorpus()).isSameAs(corpus);

        verify(urlFrontierRepository).save(any());
        verify(crawlRunner).executeAsync(UUID.fromString("00000000-0000-4000-8000-000000000099"));
    }

    @Test
    void startJobUsesExplicitSeedUrl() {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(UUID.randomUUID());
        corpus.setName("geostat-portal");
        corpus.setSeedUrls(List.of("https://www.geostat.ge/ka"));
        corpus.setStatus(CorpusStatus.active);

        when(corpusRepository.findByName("geostat-portal")).thenReturn(Optional.of(corpus));
        when(crawlRunRepository.save(any())).thenAnswer(invocation -> {
            CrawlRunEntity run = invocation.getArgument(0);
            run.setId(UUID.randomUUID());
            return run;
        });
        when(urlFrontierRepository.existsByCrawlRun_IdAndUrlHash(any(), any())).thenReturn(false);

        service.startJob(new IngestionJobRequest("geostat-portal", "https://www.geostat.ge/en", true));

        verify(urlFrontierRepository).save(any());
    }
}
