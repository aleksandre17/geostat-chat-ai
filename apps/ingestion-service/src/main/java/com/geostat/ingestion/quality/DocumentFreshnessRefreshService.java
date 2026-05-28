package com.geostat.ingestion.quality;

import com.geostat.ingestion.chunk.DocumentChunkWriter;
import com.geostat.ingestion.config.IngestionProperties;
import com.geostat.ingestion.crawl.fetch.FetchedPage;
import com.geostat.ingestion.crawl.fetch.FetchOptions;
import com.geostat.ingestion.crawl.fetch.Crawler4jPageFetcher;
import com.geostat.platform.crawl.PageFetcher;
import com.geostat.platform.crawl.RenderMode;
import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.events.DocumentPostPersistPipeline;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.parse.DocumentDisplayFields;
import com.geostat.ingestion.parse.HtmlContentCleaner;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Incremental re-fetch using stored HTTP validators (RAG freshness). */
@Service
@Profile("db")
public class DocumentFreshnessRefreshService {

    private final CorpusRepository corpusRepository;
    private final DocumentRepository documentRepository;
    private final PageFetcher routingPageFetcher;
    private final Crawler4jPageFetcher staticPageFetcher;
    private final CorpusConfigurationLoader corpusConfigurationLoader;
    private final HtmlContentCleaner contentCleaner;
    private final DocumentChunkWriter documentChunkWriter;
    private final DocumentPostPersistPipeline postPersistPipeline;
    private final DocumentLocalePairLinker localePairLinker;
    private final long staleAfterMs;

    public DocumentFreshnessRefreshService(
            CorpusRepository corpusRepository,
            DocumentRepository documentRepository,
            PageFetcher routingPageFetcher,
            Crawler4jPageFetcher staticPageFetcher,
            CorpusConfigurationLoader corpusConfigurationLoader,
            HtmlContentCleaner contentCleaner,
            DocumentChunkWriter documentChunkWriter,
            DocumentPostPersistPipeline postPersistPipeline,
            DocumentLocalePairLinker localePairLinker,
            IngestionProperties properties) {
        this.corpusRepository = corpusRepository;
        this.documentRepository = documentRepository;
        this.routingPageFetcher = routingPageFetcher;
        this.staticPageFetcher = staticPageFetcher;
        this.corpusConfigurationLoader = corpusConfigurationLoader;
        this.contentCleaner = contentCleaner;
        this.documentChunkWriter = documentChunkWriter;
        this.postPersistPipeline = postPersistPipeline;
        this.localePairLinker = localePairLinker;
        this.staleAfterMs = properties.freshness().staleAfterMs();
    }

    @Transactional
    public FreshnessRefreshReport refreshStale(String corpusName, int limit) {
        CorpusEntity corpus = corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corpus not found: " + corpusName));

        Instant staleBefore = Instant.now().minusMillis(staleAfterMs);
        List<DocumentEntity> candidates = documentRepository.findStaleWithValidators(
                corpus.getId(), DocumentFetchStatus.parsed, staleBefore, PageRequest.of(0, Math.max(1, limit)));

        int notModified = 0;
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (DocumentEntity document : candidates) {
            try {
                if (refreshOne(corpus, document)) {
                    updated++;
                } else {
                    notModified++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(document.getCanonicalUrl() + ": " + e.getMessage());
            }
        }

        return new FreshnessRefreshReport(corpusName, candidates.size(), updated, notModified, failed, errors);
    }

    private boolean refreshOne(CorpusEntity corpus, DocumentEntity document) throws Exception {
        ParseProfile profile = corpusConfigurationLoader.parseProfileFor(corpus.getName());
        FetchedPage page;
        if (profile.renderMode() == RenderMode.HEADLESS) {
            // Headless corpora (SPA): full re-fetch via Playwright; no conditional-GET support
            var platformOptions = com.geostat.platform.crawl.FetchOptions.forProfile(profile);
            page = FetchedPage.fromPlatform(document.getCanonicalUrl(),
                    routingPageFetcher.fetch(document.getCanonicalUrl(), platformOptions));
        } else {
            // Static corpora: conditional GET (If-None-Match / If-Modified-Since) to avoid
            // full re-download when content has not changed
            var conditionalOptions = FetchOptions.of(document.getEtagHttp(), document.getLastModifiedHttp());
            page = staticPageFetcher.fetchConditional(document.getCanonicalUrl(), corpus, conditionalOptions);
        }
        if (page.notModified()) {
            document.setFetchedAt(Instant.now());
            documentRepository.save(document);
            return false;
        }
        return applyFetchedPage(corpus, document, page);
    }

    private boolean applyFetchedPage(CorpusEntity corpus, DocumentEntity document, FetchedPage page) {
        HtmlContentCleaner.CleanedContent cleaned = contentCleaner.clean(page.html());
        String newHash = UrlHasher.hash(cleaned.text());
        boolean contentUnchanged = newHash.equals(document.getContentHash());

        document.setHttpStatus(page.statusCode());
        document.setEtagHttp(page.etagHttp());
        document.setLastModifiedHttp(page.lastModifiedHttp());
        document.setLastModified(page.lastModified());
        document.setFetchedAt(Instant.now());

        if (contentUnchanged) {
            documentRepository.save(document);
            return false;
        }

        document.setTitle(cleaned.title());
        document.setLanguage(UrlLocaleInferer.infer(document.getCanonicalUrl(), cleaned.language()));
        document.setSectionPath(cleaned.sectionPath());
        document.setContentText(cleaned.text());
        document.setContentHash(newHash);
        DocumentDisplayFields.apply(document, cleaned);
        document.setFetchStatus(DocumentFetchStatus.parsed);
        documentRepository.save(document);

        documentChunkWriter.replaceChunks(
                document, corpus, cleaned.text(), cleaned.sectionPath(), document.getLanguage());
        localePairLinker.link(corpus.getId(), document.getId(), document.getCanonicalUrl(), document.getLanguage());
        postPersistPipeline.afterDocumentPersisted(document.getId(), corpus.getId());
        return true;
    }

    public record FreshnessRefreshReport(
            String corpusName,
            int candidates,
            int updated,
            int notModified,
            int failed,
            List<String> errors) {}
}
