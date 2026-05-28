package com.geostat.ingestion.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.chunk.DocumentChunkWriter;
import com.geostat.ingestion.crawl.archive.RawHtmlArchivePort;
import com.geostat.ingestion.crawl.fetch.Crawler4jPageFetcher;
import com.geostat.ingestion.crawl.frontier.LinkDiscoverer;
import com.geostat.platform.crawl.PageFetcher;
import com.geostat.platform.crawl.RenderMode;
import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.events.DocumentPostPersistPipeline;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.profile.DefaultParseProfile;
import com.geostat.ingestion.parse.profile.JsoupContentExtractor;
import com.geostat.ingestion.parse.profile.MarkerBoilerplateStripper;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfigLoader;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig;
import com.geostat.ingestion.parse.profile.ThresholdsCorpusQualityGate;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
import com.geostat.ingestion.persistence.repository.DocumentLinkRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.geostat.ingestion.crawl.runner.CrawlRunStore;
import com.geostat.ingestion.crawl.runner.RunConfig;
import com.geostat.platform.parse.QualityThresholds;
import com.geostat.ingestion.parse.validation.DocumentValidationPipeline;
import com.geostat.ingestion.parse.validation.LanguageConsistencyValidator;
import com.geostat.ingestion.parse.validation.MinContentLengthValidator;
import com.geostat.ingestion.parse.validation.ParagraphRepetitionDetector;
import com.geostat.ingestion.parse.strategy.GeostatNewsExtractionStrategy;
import com.geostat.ingestion.parse.validation.TruncationDetector;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrawlRunStoreQualityGateTest {

    @Mock
    private CrawlRunRepository crawlRunRepository;
    @Mock
    private UrlFrontierRepository urlFrontierRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentLinkRepository documentLinkRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private Crawler4jPageFetcher crawler4jPageFetcher;
    @Mock
    private PageFetcher routingPageFetcher;
    @Mock
    private LinkDiscoverer linkDiscoverer;
    @Mock
    private DocumentChunkWriter documentChunkWriter;
    @Mock
    private DocumentPostPersistPipeline postPersistPipeline;
    @Mock
    private DocumentLocalePairLinker localePairLinker;
    @Mock
    private RawHtmlArchivePort rawHtmlArchive;
    @Mock
    private CorpusConfigurationLoader configurationLoader;
    @Mock
    private CorpusQualityGateConfigLoader gateConfigLoader;

    private CrawlRunStore store;

    @BeforeEach
    void setUp() {
        ParseProperties parseProperties = new ParseProperties(new ParseProperties.Profile(true), "ops/config/corpus", "ops/eval/corpus-quality-gate.yaml");
        HtmlContentCleaner cleaner = new HtmlContentCleaner(
                new PageDisplayMetadataExtractor(),
                parseProperties,
                configurationLoader,
                JsoupContentExtractorTestSupport.yamlFallbackExtractor(),
                testValidationPipeline(),
                new GeostatNewsExtractionStrategy());
        when(configurationLoader.parseProfileFor(any())).thenReturn(DefaultParseProfile.GEOSTAT_PORTAL);
        when(gateConfigLoader.thresholdsForCorpus(any())).thenReturn(Optional.empty());

        store = new CrawlRunStore(
                crawlRunRepository,
                urlFrontierRepository,
                documentRepository,
                documentLinkRepository,
                jdbcTemplate,
                crawler4jPageFetcher,
                routingPageFetcher,
                configurationLoader,
                cleaner,
                linkDiscoverer,
                documentChunkWriter,
                postPersistPipeline,
                localePairLinker,
                rawHtmlArchive,
                new IngestionProperties(null, null, null, null, null, null, null, null, null),
                parseProperties,
                new ThresholdsCorpusQualityGate(),
                QualityThresholds.p0Defaults(),
                gateConfigLoader);
    }

    private static DocumentValidationPipeline testValidationPipeline() {
        return new DocumentValidationPipeline(List.of(
                new MinContentLengthValidator(30, 100),
                new TruncationDetector(200, 40),
                new ParagraphRepetitionDetector(25),
                new LanguageConsistencyValidator("ka")));
    }

    @Test
    void skipsChunkingWhenQualityGateRejectsBoilerplateOnlyPage() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID frontierId = UUID.randomUUID();

        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        corpus.setName("geostat-portal");

        CrawlRunEntity run = new CrawlRunEntity();
        run.setId(runId);
        run.setCorpus(corpus);

        UrlFrontierEntity frontier = new UrlFrontierEntity();
        frontier.setId(frontierId);
        frontier.setUrl("https://www.geostat.ge/ka/empty");
        frontier.setUrlHash("hash");
        frontier.setDepth(0);

        AtomicReference<DocumentEntity> savedDocument = new AtomicReference<>();

        var html = Jsoup.parse("""
                <html lang="ka"><head><title>Stats</title></head>
                <body><main>
                <p>2024 წელს საქართველოს მოსახლეობა 3.7 მილიონი იყო, უმუშეორობა 16.5%.</p>
                <p>ვებგვერდის ადაპტირებული ვერსია მხოლოდ.</p>
                </main></body></html>
                """);

        when(urlFrontierRepository.findById(frontierId)).thenReturn(Optional.of(frontier));
        when(crawlRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(routingPageFetcher.fetch(eq(frontier.getUrl()), any()))
                .thenReturn(new com.geostat.platform.crawl.FetchedPage(
                        frontier.getUrl(), html.outerHtml(), 200, "text/html", RenderMode.STATIC));
        when(documentRepository.findByCorpusIdAndUrlHash(eq(corpusId), anyString()))
                .thenAnswer(invocation -> {
            DocumentEntity current = savedDocument.get();
            return current == null ? Optional.empty() : Optional.of(current);
        });
        when(documentRepository.save(org.mockito.ArgumentMatchers.any(DocumentEntity.class)))
                .thenAnswer(invocation -> {
                    DocumentEntity doc = invocation.getArgument(0);
                    savedDocument.set(doc);
                    return doc;
                });
        when(linkDiscoverer.discover(runId, corpus, frontier, html, 2)).thenReturn(List.of());

        store.processFrontier(frontierId, runId, new RunConfig(runId, corpusId, "test-corpus", 50, 2, 500, List.of(), 5));

        verify(documentChunkWriter, never()).replaceChunks(any(), any(), any(), any(), any());
        verify(documentRepository).save(org.mockito.ArgumentMatchers.argThat(doc -> doc.getFetchStatus() == DocumentFetchStatus.skipped));
    }
}
