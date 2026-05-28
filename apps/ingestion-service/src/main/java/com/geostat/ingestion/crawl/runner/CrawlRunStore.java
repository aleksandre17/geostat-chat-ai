package com.geostat.ingestion.crawl.runner;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.chunk.DocumentChunkWriter;
import com.geostat.ingestion.crawl.UrlNormalizer;
import com.geostat.ingestion.crawl.archive.RawHtmlArchivePort;
import com.geostat.ingestion.crawl.fetch.FetchedPage;
import com.geostat.ingestion.crawl.fetch.FetchOptions;
import com.geostat.ingestion.crawl.frontier.LinkDiscoverer;
import com.geostat.platform.crawl.PageFetcher;
import com.geostat.platform.crawl.RenderMode;
import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.events.DocumentPostPersistPipeline;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.parse.DocumentDisplayFields;
import com.geostat.ingestion.parse.HtmlContentCleaner;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfigLoader;
import com.geostat.ingestion.parse.validation.ValidationViolationCodes;
import com.geostat.platform.parse.CorpusQualityGate;
import com.geostat.platform.parse.QualityThresholds;
import com.geostat.platform.parse.ValidationOutcome;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.DocumentLinkEntity;
import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.model.FrontierStatus;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
import com.geostat.ingestion.persistence.repository.DocumentLinkRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.UrlFrontierRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
public class CrawlRunStore {

    private static final Logger log = LoggerFactory.getLogger(CrawlRunStore.class);

    private final CrawlRunRepository crawlRunRepository;
    private final UrlFrontierRepository urlFrontierRepository;
    private final DocumentRepository documentRepository;
    private final DocumentLinkRepository documentLinkRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PageFetcher routingPageFetcher;
    private final CorpusConfigurationLoader corpusConfigurationLoader;
    private final boolean playwrightEnabled;
    private final HtmlContentCleaner contentCleaner;
    private final LinkDiscoverer linkDiscoverer;
    private final DocumentChunkWriter documentChunkWriter;
    private final DocumentPostPersistPipeline postPersistPipeline;
    private final DocumentLocalePairLinker localePairLinker;
    private final RawHtmlArchivePort rawHtmlArchive;
    private final boolean archiveEnabled;
    private final ParseProperties parseProperties;
    private final CorpusQualityGate corpusQualityGate;
    private final QualityThresholds qualityThresholds;
    private final CorpusQualityGateConfigLoader gateConfigLoader;

    private CrawlRunStore self;

    public CrawlRunStore(
            CrawlRunRepository crawlRunRepository,
            UrlFrontierRepository urlFrontierRepository,
            DocumentRepository documentRepository,
            DocumentLinkRepository documentLinkRepository,
            JdbcTemplate jdbcTemplate,
            PageFetcher routingPageFetcher,
            CorpusConfigurationLoader corpusConfigurationLoader,
            HtmlContentCleaner contentCleaner,
            LinkDiscoverer linkDiscoverer,
            DocumentChunkWriter documentChunkWriter,
            DocumentPostPersistPipeline postPersistPipeline,
            DocumentLocalePairLinker localePairLinker,
            RawHtmlArchivePort rawHtmlArchive,
            IngestionProperties properties,
            ParseProperties parseProperties,
            CorpusQualityGate corpusQualityGate,
            QualityThresholds qualityThresholds,
            CorpusQualityGateConfigLoader gateConfigLoader) {
        this.crawlRunRepository = crawlRunRepository;
        this.urlFrontierRepository = urlFrontierRepository;
        this.documentRepository = documentRepository;
        this.documentLinkRepository = documentLinkRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.routingPageFetcher = routingPageFetcher;
        this.corpusConfigurationLoader = corpusConfigurationLoader;
        this.playwrightEnabled = properties.playwright().enabled();
        this.contentCleaner = contentCleaner;
        this.linkDiscoverer = linkDiscoverer;
        this.documentChunkWriter = documentChunkWriter;
        this.postPersistPipeline = postPersistPipeline;
        this.localePairLinker = localePairLinker;
        this.rawHtmlArchive = rawHtmlArchive;
        this.archiveEnabled = properties.archive().enabled();
        this.parseProperties = parseProperties;
        this.corpusQualityGate = corpusQualityGate;
        this.qualityThresholds = qualityThresholds;
        this.gateConfigLoader = gateConfigLoader;
    }

    @Autowired
    @Lazy
    void setSelf(CrawlRunStore self) {
        this.self = self;
    }

    private record FetchPrepare(String url, CorpusEntity corpus, FetchOptions fetchOptions) {}

    @Transactional(readOnly = true)
    public RunConfig loadRunConfig(UUID runId) {
        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        CorpusEntity corpus = run.getCorpus();
        return new RunConfig(
                runId,
                corpus.getId(),
                corpus.getName(),
                CorpusPolicy.maxPagesPerRun(corpus),
                CorpusPolicy.maxDepth(corpus),
                CorpusPolicy.rateLimitMs(corpus),
                CorpusPolicy.allowedHosts(corpus),
                CorpusPolicy.workerThreads(corpus));
    }

    @Transactional
    public void markRunning(UUID runId) {
        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        validateHeadlessPrerequisites(run.getCorpus());
        run.setStatus(CrawlRunStatus.running);
        run.setStartedAt(Instant.now());
        crawlRunRepository.save(run);
    }

    private void validateHeadlessPrerequisites(CorpusEntity corpus) {
        var profile = corpusConfigurationLoader.parseProfileFor(corpus.getName());
        if (profile.renderMode() == RenderMode.HEADLESS && !playwrightEnabled) {
            throw new IllegalStateException(
                    "Corpus '" + corpus.getName() + "' requires headless rendering (renderMode=headless) "
                            + "but geostat.ingestion.playwright.enabled is false");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> nextQueuedFrontierIds(UUID runId) {
        return urlFrontierRepository
                .findTop50ByCrawlRun_IdAndStatusOrderByDiscoveredAtAsc(runId, FrontierStatus.queued)
                .stream()
                .map(UrlFrontierEntity::getId)
                .toList();
    }

    @Transactional
    FetchPrepare prepareFetch(UUID frontierId, UUID runId) {
        UrlFrontierEntity frontier = urlFrontierRepository.findById(frontierId).orElseThrow();
        frontier.setStatus(FrontierStatus.fetching);
        frontier.setAttemptCount(frontier.getAttemptCount() + 1);
        urlFrontierRepository.save(frontier);

        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        CorpusEntity corpus = run.getCorpus();
        // Force-initialize while still inside the @Transactional boundary so the proxy
        // remains usable in non-transactional callers (fetchHtml, resolvePlatformFetchOptions).
        Hibernate.initialize(corpus);

        String normalizedFrontierUrl = UrlNormalizer.normalize(frontier.getUrl());
        Optional<DocumentEntity> existingDocument = documentRepository.findByCorpusIdAndUrlHash(
                corpus.getId(), UrlHasher.hash(normalizedFrontierUrl));
        FetchOptions fetchOptions = existingDocument
                .map(doc -> FetchOptions.of(doc.getEtagHttp(), doc.getLastModifiedHttp()))
                .orElse(FetchOptions.none());

        return new FetchPrepare(frontier.getUrl(), corpus, fetchOptions);
    }

    public Optional<FetchedPage> fetchHtml(UUID frontierId, UUID runId, RunConfig config)
            throws IOException, InterruptedException {
        FetchPrepare prep = invokePrepareFetch(frontierId, runId);

        var platformOptions = resolvePlatformFetchOptions(prep.corpus(), runId);
        var platformPage = routingPageFetcher.fetch(prep.url(), platformOptions);
        FetchedPage page = FetchedPage.fromPlatform(prep.url(), platformPage);
        if (page.notModified()) {
            invokeMarkFrontierNotModified(frontierId, prep.corpus());
            return Optional.empty();
        }
        return Optional.of(page);
    }

    private com.geostat.platform.crawl.FetchOptions resolvePlatformFetchOptions(CorpusEntity corpus, UUID runId) {
        var profile = corpusConfigurationLoader.parseProfileFor(corpus.getName());
        var base = com.geostat.platform.crawl.FetchOptions.forProfile(profile);
        return renderModeFromSnapshot(runId)
                .filter(mode -> mode != profile.renderMode())
                .map(mode -> new com.geostat.platform.crawl.FetchOptions(
                        mode, base.timeoutMs(), base.userAgent(), base.network()))
                .orElse(base);
    }

    private Optional<RenderMode> renderModeFromSnapshot(UUID runId) {
        return crawlRunRepository
                .findById(runId)
                .map(CrawlRunEntity::getConfigSnapshot)
                .flatMap(this::parseRenderMode);
    }

    private Optional<RenderMode> parseRenderMode(Map<String, Object> snapshot) {
        Object raw = snapshot.get("renderMode");
        if (!(raw instanceof String value) || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(RenderMode.valueOf(value.toUpperCase()));
    }

    private FetchPrepare invokePrepareFetch(UUID frontierId, UUID runId) {
        return self != null ? self.prepareFetch(frontierId, runId) : prepareFetch(frontierId, runId);
    }

    private void invokeMarkFrontierNotModified(UUID frontierId, CorpusEntity corpus) {
        if (self != null) {
            self.markFrontierNotModified(frontierId, corpus);
        } else {
            markFrontierNotModified(frontierId, corpus);
        }
    }

    @Transactional
    public PersistedPage persistFetched(UUID frontierId, UUID runId, FetchedPage page, RunConfig config)
            throws IOException {
        UrlFrontierEntity frontier = urlFrontierRepository.findById(frontierId).orElseThrow();
        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        CorpusEntity corpus = run.getCorpus();

        int links = persistFetchedContent(run, corpus, frontier, page, config.maxDepth());
        frontier.setStatus(FrontierStatus.done);
        urlFrontierRepository.save(frontier);

        String normalizedUrl = UrlNormalizer.normalize(page.canonicalUrl());
        Optional<DocumentEntity> docOpt = documentRepository
                .findByCorpusIdAndUrlHash(corpus.getId(), UrlHasher.hash(normalizedUrl));
        if (docOpt.isEmpty()) {
            // Document was not persisted (binary URL, validation reject, quality gate skip, etc.)
            return new PersistedPage(null, corpus.getId(), links);
        }
        return new PersistedPage(docOpt.get().getId(), corpus.getId(), links);
    }

    public Optional<PersistedPage> processFrontier(UUID frontierId, UUID runId, RunConfig config)
            throws IOException, InterruptedException {
        Optional<FetchedPage> htmlPage = fetchHtml(frontierId, runId, config);
        if (htmlPage.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(persistFetched(frontierId, runId, htmlPage.get(), config));
    }

    public void indexPersistedPage(PersistedPage page) {
        if (page.documentId() == null) {
            return;
        }
        postPersistPipeline.afterDocumentPersisted(page.documentId(), page.corpusId());
    }

    public record PersistedPage(UUID documentId, UUID corpusId, int linksDiscovered) {}

    @Transactional
    void markFrontierNotModified(UUID frontierId, CorpusEntity corpus) {
        urlFrontierRepository.findById(frontierId).ifPresent(frontier -> {
            String normalizedFrontierUrl = UrlNormalizer.normalize(frontier.getUrl());
            documentRepository
                    .findByCorpusIdAndUrlHash(corpus.getId(), UrlHasher.hash(normalizedFrontierUrl))
                    .ifPresent(doc -> {
                        doc.setFetchedAt(Instant.now());
                        documentRepository.save(doc);
                    });
            frontier.setStatus(FrontierStatus.done);
            urlFrontierRepository.save(frontier);
        });
    }

    @Transactional
    void markFrontierSkipped(UUID frontierId, String message) {
        urlFrontierRepository.findById(frontierId).ifPresent(frontier -> {
            frontier.setStatus(FrontierStatus.skipped);
            frontier.setLastError(truncate(message, 500));
            urlFrontierRepository.save(frontier);
        });
    }

    @Transactional
    public void markFrontierFailed(UUID frontierId, String message) {
        urlFrontierRepository.findById(frontierId).ifPresent(frontier -> {
            frontier.setStatus(FrontierStatus.failed);
            frontier.setLastError(truncate(message, 500));
            urlFrontierRepository.save(frontier);
        });
    }

    @Transactional(readOnly = true)
    public long countQueuedFrontier(UUID runId) {
        return urlFrontierRepository.countByCrawlRun_IdAndStatus(runId, FrontierStatus.queued);
    }

    @Transactional
    public void markCompleted(
            UUID runId, int pagesFetched, int linksDiscovered, int failures, long queuedRemaining) {
        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        Map<String, Object> stats = new HashMap<>();
        stats.put("pagesFetched", pagesFetched);
        stats.put("linksDiscovered", linksDiscovered);
        stats.put("failures", failures);
        stats.put("queuedRemaining", queuedRemaining);
        run.setStats(stats);
        run.setStatus(CrawlRunStatus.completed);
        run.setFinishedAt(Instant.now());
        crawlRunRepository.save(run);
    }

    @Transactional
    public void markCompleted(UUID runId, int pagesFetched, int linksDiscovered, int failures) {
        markCompleted(runId, pagesFetched, linksDiscovered, failures, countQueuedFrontier(runId));
    }

    public void recordOutboundLinks(
            UUID sourceDocId, UUID corpusId, UUID crawlRunId, List<String> discoveredUrls) {
        List<DocumentLinkEntity> links = discoveredUrls.stream()
                .filter(url -> !documentLinkRepository.existsBySourceDocIdAndTargetUrl(sourceDocId, url))
                .map(url -> {
                    DocumentLinkEntity link = new DocumentLinkEntity();
                    link.setSourceDocId(sourceDocId);
                    link.setCorpusId(corpusId);
                    link.setCrawlRunId(crawlRunId);
                    link.setTargetUrl(url);
                    return link;
                })
                .toList();
        if (!links.isEmpty()) {
            documentLinkRepository.saveAll(links);
        }
    }

    public void resolveDocumentLinkTargets(UUID corpusId) {
        jdbcTemplate.update(
                """
                UPDATE ingestion.document_link dl
                SET target_doc_id = d.id
                FROM ingestion.document d
                WHERE d.corpus_id = dl.corpus_id
                  AND d.canonical_url = dl.target_url
                  AND dl.corpus_id = ?
                  AND dl.target_doc_id IS NULL
                """,
                corpusId);
        log.info("Resolved document_link targets for corpus {}", corpusId);
    }

    @Transactional
    public void markFailed(UUID runId, String message) {
        crawlRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(CrawlRunStatus.failed);
            run.setFinishedAt(Instant.now());
            Map<String, Object> stats = new HashMap<>(run.getStats());
            stats.put("error", truncate(message, 500));
            run.setStats(stats);
            crawlRunRepository.save(run);
        });
    }

    private static final java.util.regex.Pattern BINARY_URL_PATTERN =
            java.util.regex.Pattern.compile(
                    "(?i)\\.(pdf|xlsx?|docx?|pptx?|zip|rar|csv|gz|tar|7z|mp3|mp4|avi|mov|jpg|jpeg|png|gif|svg|ico|bmp|webp)$");

    /** Returns true when the URL extension indicates non-HTML binary content (PDF, image, Office, …). */
    private static boolean isBinaryUrl(String url) {
        if (url == null) return false;
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        return BINARY_URL_PATTERN.matcher(path).find();
    }

    private int persistFetchedContent(
            CrawlRunEntity run, CorpusEntity corpus, UrlFrontierEntity frontier, FetchedPage page, int maxDepth)
            throws IOException {
        if (isBinaryUrl(frontier.getUrl())) {
            log.debug("[ingestion] SKIP binary url={}", frontier.getUrl());
            return 0;
        }
        String canonicalUrl = UrlNormalizer.normalize(page.canonicalUrl());
        String documentUrlHash = UrlHasher.hash(canonicalUrl);
        HtmlContentCleaner.ProfileCleanResult cleanResult =
                contentCleaner.clean(page.html(), canonicalUrl, corpus.getName());
        HtmlContentCleaner.CleanedContent cleaned = cleanResult.content();
        Optional<ValidationOutcome> validationOutcome = cleanResult.validationOutcome();

        if (validationOutcome.isPresent() && validationOutcome.get().isRejected()) {
            log.debug("[ingestion] REJECT url={}", frontier.getUrl());
            List<UrlFrontierEntity> discovered =
                    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
            documentRepository
                    .findByCorpusIdAndUrlHash(corpus.getId(), documentUrlHash)
                    .ifPresent(doc -> recordOutboundLinks(
                            doc.getId(),
                            corpus.getId(),
                            run.getId(),
                            discovered.stream().map(UrlFrontierEntity::getUrl).toList()));
            discovered.forEach(next -> next.setCrawlRun(run));
            urlFrontierRepository.saveAll(discovered);
            return discovered.size();
        }

        // Re-crawl: acquire a pessimistic write lock on the existing document row to serialize
        // concurrent workers processing the same URL (same document can appear in multiple frontier
        // entries discovered from different parent pages).
        DocumentEntity document = documentRepository
                .findByCorpusIdAndUrlHash(corpus.getId(), documentUrlHash)
                .map(existing -> documentRepository.lockById(existing.getId()).orElse(existing))
                .orElseGet(DocumentEntity::new);
        String newHash = UrlHasher.hash(cleaned.text());
        boolean contentUnchanged = document.getId() != null && newHash.equals(document.getContentHash());

        document.setCorpus(corpus);
        document.setCanonicalUrl(canonicalUrl);
        if (page.finalUrl() != null && !page.finalUrl().equals(page.url())) {
            document.setOriginalUrl(page.url());
        } else {
            document.setOriginalUrl(null);
        }
        document.setUrlHash(documentUrlHash);
        document.setHttpStatus(page.statusCode());
        applyHttpCacheHeaders(document, page);
        document.setEncodingIssue(page.encodingIssue());
        document.setFetchedAt(Instant.now());
        archiveRawHtml(corpus, document, page);

        if (contentUnchanged) {
            documentRepository.save(document);
            List<UrlFrontierEntity> discovered =
                    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
            recordOutboundLinks(
                    document.getId(),
                    corpus.getId(),
                    run.getId(),
                    discovered.stream().map(UrlFrontierEntity::getUrl).toList());
            discovered.forEach(next -> next.setCrawlRun(run));
            urlFrontierRepository.saveAll(discovered);
            return discovered.size();
        }

        document.setTitle(cleaned.title());
        document.setLanguage(UrlLocaleInferer.infer(canonicalUrl, cleaned.language()));
        document.setSectionPath(cleaned.sectionPath());
        document.setContentText(cleaned.text());
        document.setContentHash(newHash);
        document.setPublishedAt(cleaned.publishedAt());
        DocumentDisplayFields.apply(document, cleaned);

        validationOutcome.ifPresent(outcome -> {
            document.setQualityScore(outcome.quality().name().toLowerCase());
            document.setValidationViolations(ValidationViolationCodes.toArray(outcome.violations()));
        });

        if (validationOutcome.isPresent() && validationOutcome.get().isSkip()) {
            document.setFetchStatus(DocumentFetchStatus.parsed);
            documentRepository.save(document);
            List<UrlFrontierEntity> discovered =
                    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
            recordOutboundLinks(
                    document.getId(),
                    corpus.getId(),
                    run.getId(),
                    discovered.stream().map(UrlFrontierEntity::getUrl).toList());
            discovered.forEach(next -> next.setCrawlRun(run));
            urlFrontierRepository.saveAll(discovered);
            return discovered.size();
        }

        CorpusQualityGate.Decision gateDecision = CorpusQualityGate.Decision.ACCEPT;
        if (parseProperties.profile().enabled() && cleanResult.profileDocument().isPresent()) {
            QualityThresholds thresholds = gateConfigLoader
                    .thresholdsForCorpus(corpus.getName())
                    .map(t -> mergeWithGlobal(t, qualityThresholds))
                    .orElse(qualityThresholds);
            gateDecision = corpusQualityGate.evaluate(cleanResult.profileDocument().get(), thresholds);
        }
        if (gateDecision != CorpusQualityGate.Decision.ACCEPT) {
            document.setFetchStatus(DocumentFetchStatus.skipped);
            documentRepository.save(document);
            List<UrlFrontierEntity> discovered =
                    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
            recordOutboundLinks(
                    document.getId(),
                    corpus.getId(),
                    run.getId(),
                    discovered.stream().map(UrlFrontierEntity::getUrl).toList());
            discovered.forEach(next -> next.setCrawlRun(run));
            urlFrontierRepository.saveAll(discovered);
            return discovered.size();
        }

        document.setFetchStatus(DocumentFetchStatus.parsing);
        documentRepository.save(document);

        documentChunkWriter.replaceChunks(
                document, corpus, cleaned.text(), cleaned.sectionPath(), document.getLanguage());

        document.setFetchStatus(DocumentFetchStatus.parsed);
        documentRepository.save(document);

        localePairLinker.link(corpus.getId(), document.getId(), document.getCanonicalUrl(), document.getLanguage());

        List<UrlFrontierEntity> discovered =
                linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
        recordOutboundLinks(
                document.getId(),
                corpus.getId(),
                run.getId(),
                discovered.stream().map(UrlFrontierEntity::getUrl).toList());
        discovered.forEach(next -> next.setCrawlRun(run));
        urlFrontierRepository.saveAll(discovered);
        return discovered.size();
    }

    private void archiveRawHtml(CorpusEntity corpus, DocumentEntity document, FetchedPage page) {
        if (!archiveEnabled || page.html() == null) {
            return;
        }
        rawHtmlArchive
                .store(corpus.getName(), document.getCanonicalUrl(), page.html().html().getBytes(StandardCharsets.UTF_8))
                .ifPresent(document::setRawStorageKey);
    }

    private QualityThresholds mergeWithGlobal(
            CorpusQualityGateConfig.ThresholdsConfig override, QualityThresholds global) {
        int minBodyChars = override.minContentLength() != null
                ? override.minContentLength()
                : global.minBodyChars();
        double maxBoilerplateParagraphRatio = override.maxBoilerplateRatio() != null
                ? override.maxBoilerplateRatio()
                : global.maxBoilerplateParagraphRatio();
        return new QualityThresholds(minBodyChars, maxBoilerplateParagraphRatio, global.maxBoilerplateBodyRatio());
    }

    private static void applyHttpCacheHeaders(DocumentEntity document, FetchedPage page) {
        document.setEtagHttp(page.etagHttp());
        document.setLastModifiedHttp(page.lastModifiedHttp());
        document.setLastModified(page.lastModified());
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
