package com.geostat.ingestion.crawl.runner;

import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.chunk.DocumentChunkWriter;
import com.geostat.ingestion.crawl.archive.RawHtmlArchivePort;
import com.geostat.ingestion.crawl.fetch.FetchedPage;
import com.geostat.ingestion.crawl.fetch.Crawler4jPageFetcher;
import com.geostat.ingestion.crawl.fetch.PolicyBlockedException;
import com.geostat.ingestion.crawl.fetch.RobotsBlockedException;
import com.geostat.ingestion.crawl.frontier.LinkDiscoverer;
import com.geostat.ingestion.crawl.frontier.UrlHasher;
import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.events.DocumentPostPersistPipeline;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.parse.DocumentDisplayFields;
import com.geostat.ingestion.parse.HtmlContentCleaner;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.platform.parse.CorpusQualityGate;
import com.geostat.platform.parse.QualityThresholds;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.model.FrontierStatus;
import com.geostat.ingestion.persistence.repository.CrawlRunRepository;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
public class CrawlRunStore {

    private final CrawlRunRepository crawlRunRepository;
    private final UrlFrontierRepository urlFrontierRepository;
    private final DocumentRepository documentRepository;
    private final Crawler4jPageFetcher pageFetcher;
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

    public CrawlRunStore(
            CrawlRunRepository crawlRunRepository,
            UrlFrontierRepository urlFrontierRepository,
            DocumentRepository documentRepository,
            Crawler4jPageFetcher pageFetcher,
            HtmlContentCleaner contentCleaner,
            LinkDiscoverer linkDiscoverer,
            DocumentChunkWriter documentChunkWriter,
            DocumentPostPersistPipeline postPersistPipeline,
            DocumentLocalePairLinker localePairLinker,
            RawHtmlArchivePort rawHtmlArchive,
            IngestionProperties properties,
            ParseProperties parseProperties,
            CorpusQualityGate corpusQualityGate,
            QualityThresholds qualityThresholds) {
        this.crawlRunRepository = crawlRunRepository;
        this.urlFrontierRepository = urlFrontierRepository;
        this.documentRepository = documentRepository;
        this.pageFetcher = pageFetcher;
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
    }

    @Transactional(readOnly = true)
    public RunConfig loadRunConfig(UUID runId) {
        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        CorpusEntity corpus = run.getCorpus();
        return new RunConfig(
                runId,
                corpus.getId(),
                CorpusPolicy.maxPagesPerRun(corpus),
                CorpusPolicy.maxDepth(corpus),
                CorpusPolicy.rateLimitMs(corpus),
                CorpusPolicy.allowedHosts(corpus));
    }

    @Transactional
    public void markRunning(UUID runId) {
        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        run.setStatus(CrawlRunStatus.running);
        run.setStartedAt(Instant.now());
        crawlRunRepository.save(run);
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
    public Optional<PersistedPage> processFrontier(UUID frontierId, UUID runId, RunConfig config)
            throws IOException, InterruptedException {
        UrlFrontierEntity frontier = urlFrontierRepository.findById(frontierId).orElseThrow();
        frontier.setStatus(FrontierStatus.fetching);
        frontier.setAttemptCount(frontier.getAttemptCount() + 1);
        urlFrontierRepository.save(frontier);

        CrawlRunEntity run = crawlRunRepository.findById(runId).orElseThrow();
        CorpusEntity corpus = run.getCorpus();

        try {
            PersistedPage page = persistPage(run, corpus, frontier, config.maxDepth());
            frontier.setStatus(FrontierStatus.done);
            urlFrontierRepository.save(frontier);
            return Optional.of(page);
        } catch (RobotsBlockedException | PolicyBlockedException e) {
            frontier.setStatus(FrontierStatus.skipped);
            frontier.setLastError(truncate(e.getMessage(), 500));
            urlFrontierRepository.save(frontier);
            return Optional.empty();
        }
    }

    public void indexPersistedPage(PersistedPage page) {
        postPersistPipeline.afterDocumentPersisted(page.documentId(), page.corpusId());
    }

    public record PersistedPage(UUID documentId, UUID corpusId, int linksDiscovered) {}

    @Transactional
    PersistedPage persistPage(
            CrawlRunEntity run, CorpusEntity corpus, UrlFrontierEntity frontier, int maxDepth)
            throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException {
        int links = fetchAndPersist(run, corpus, frontier, maxDepth);
        DocumentEntity document = documentRepository
                .findByCorpusIdAndUrlHash(corpus.getId(), frontier.getUrlHash())
                .orElseThrow();
        return new PersistedPage(document.getId(), corpus.getId(), links);
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

    private int fetchAndPersist(
            CrawlRunEntity run, CorpusEntity corpus, UrlFrontierEntity frontier, int maxDepth)
            throws IOException, InterruptedException, RobotsBlockedException, PolicyBlockedException {
        FetchedPage page = pageFetcher.fetch(frontier.getUrl(), corpus);
        HtmlContentCleaner.ProfileCleanResult cleanResult =
                contentCleaner.clean(page.html(), frontier.getUrl(), corpus.getName());
        HtmlContentCleaner.CleanedContent cleaned = cleanResult.content();

        DocumentEntity document = documentRepository
                .findByCorpusIdAndUrlHash(corpus.getId(), frontier.getUrlHash())
                .orElseGet(DocumentEntity::new);
        String newHash = UrlHasher.hash(cleaned.text());
        boolean contentUnchanged = document.getId() != null && newHash.equals(document.getContentHash());

        document.setCorpus(corpus);
        document.setCanonicalUrl(frontier.getUrl());
        document.setUrlHash(frontier.getUrlHash());
        document.setHttpStatus(page.statusCode());
        document.setHttpEtag(page.httpEtag());
        document.setLastModified(page.lastModified());
        document.setFetchedAt(Instant.now());
        archiveRawHtml(corpus, document, page);

        if (contentUnchanged) {
            documentRepository.save(document);
            List<UrlFrontierEntity> discovered =
                    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
            for (UrlFrontierEntity next : discovered) {
                next.setCrawlRun(run);
                urlFrontierRepository.save(next);
            }
            return discovered.size();
        }

        document.setTitle(cleaned.title());
        document.setLanguage(UrlLocaleInferer.infer(frontier.getUrl(), cleaned.language()));
        document.setSectionPath(cleaned.sectionPath());
        document.setContentText(cleaned.text());
        document.setContentHash(newHash);
        DocumentDisplayFields.apply(document, cleaned);

        CorpusQualityGate.Decision gateDecision = CorpusQualityGate.Decision.ACCEPT;
        if (parseProperties.profile().enabled() && cleanResult.profileDocument().isPresent()) {
            gateDecision = corpusQualityGate.evaluate(cleanResult.profileDocument().get(), qualityThresholds);
        }
        if (gateDecision != CorpusQualityGate.Decision.ACCEPT) {
            document.setFetchStatus(DocumentFetchStatus.skipped);
            documentRepository.save(document);
            List<UrlFrontierEntity> discovered =
                    linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
            for (UrlFrontierEntity next : discovered) {
                next.setCrawlRun(run);
                urlFrontierRepository.save(next);
            }
            return discovered.size();
        }

        document.setFetchStatus(DocumentFetchStatus.parsed);
        documentRepository.save(document);

        documentChunkWriter.replaceChunks(document, corpus, cleaned.text(), cleaned.sectionPath(), document.getLanguage());

        localePairLinker.link(corpus.getId(), document.getId(), document.getCanonicalUrl(), document.getLanguage());

        List<UrlFrontierEntity> discovered =
                linkDiscoverer.discover(run.getId(), corpus, frontier, page.html(), maxDepth);
        for (UrlFrontierEntity next : discovered) {
            next.setCrawlRun(run);
            urlFrontierRepository.save(next);
        }
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

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
