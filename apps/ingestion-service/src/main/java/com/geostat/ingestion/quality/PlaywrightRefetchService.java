package com.geostat.ingestion.quality;

import com.geostat.ingestion.chunk.DocumentChunkWriter;
import com.geostat.ingestion.crawl.fetch.FetchedPage;
import com.geostat.ingestion.crawl.fetch.PlaywrightPageFetcher;
import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.events.DocumentPostPersistPipeline;
import com.geostat.ingestion.locale.DocumentLocalePairLinker;
import com.geostat.ingestion.parse.HtmlContentCleaner;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.quality.persistence.CorpusQualityReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** P3-03b — re-fetch empty-body documents via Playwright when corpus audit recommends it. */
@Service
@Profile("db")
public class PlaywrightRefetchService {

    private final CorpusRepository corpusRepository;
    private final DocumentRepository documentRepository;
    private final CorpusQualityReader qualityReader;
    private final HtmlContentCleaner contentCleaner;
    private final DocumentChunkWriter documentChunkWriter;
    private final DocumentPostPersistPipeline postPersistPipeline;
    private final DocumentLocalePairLinker localePairLinker;
    private final Optional<PlaywrightPageFetcher> playwrightFetcher;
    private final CorpusConfigurationLoader corpusConfigurationLoader;
    private final int minContentChars;

    private PlaywrightRefetchService self;

    public PlaywrightRefetchService(
            CorpusRepository corpusRepository,
            DocumentRepository documentRepository,
            CorpusQualityReader qualityReader,
            HtmlContentCleaner contentCleaner,
            DocumentChunkWriter documentChunkWriter,
            DocumentPostPersistPipeline postPersistPipeline,
            DocumentLocalePairLinker localePairLinker,
            ObjectProvider<PlaywrightPageFetcher> playwrightFetcher,
            CorpusConfigurationLoader corpusConfigurationLoader,
            com.geostat.ingestion.config.IngestionProperties properties) {
        this.corpusRepository = corpusRepository;
        this.documentRepository = documentRepository;
        this.qualityReader = qualityReader;
        this.contentCleaner = contentCleaner;
        this.documentChunkWriter = documentChunkWriter;
        this.postPersistPipeline = postPersistPipeline;
        this.localePairLinker = localePairLinker;
        this.playwrightFetcher = Optional.ofNullable(playwrightFetcher.getIfAvailable());
        this.corpusConfigurationLoader = corpusConfigurationLoader;
        this.minContentChars = properties.qualityAudit().minContentChars();
    }

    @Autowired
    @Lazy
    void setSelf(PlaywrightRefetchService self) {
        this.self = self;
    }

    public PlaywrightRefetchReport refetchEmptyBodies(String corpusName, int limit) {
        if (playwrightFetcher.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Playwright disabled — set geostat.ingestion.playwright.enabled=true (P3-03b)");
        }
        CorpusEntity corpus = corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Corpus not found"));
        int max = Math.max(1, Math.min(limit, 50));
        List<String> urls = qualityReader.sampleEmptyBodyUrls(
                corpusName, minContentChars, max);
        int updated = 0;
        List<String> refetched = new ArrayList<>();
        for (String url : urls) {
            if (refetchOne(corpus, url)) {
                updated++;
                refetched.add(url);
            }
        }
        return new PlaywrightRefetchReport(corpusName, urls.size(), updated, refetched);
    }

    boolean refetchOne(CorpusEntity corpus, String url) {
        try {
            var fetchOptions = com.geostat.platform.crawl.FetchOptions.forProfile(
                    corpusConfigurationLoader.parseProfileFor(corpus.getName()));
            FetchedPage page = playwrightFetcher.get().fetchPage(url, fetchOptions);
            HtmlContentCleaner.CleanedContent cleaned = contentCleaner.clean(page.html());
            if (cleaned.text().length() < minContentChars) {
                return false;
            }
            self.persistRefetchedPage(corpus, url, page, cleaned);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    void persistRefetchedPage(
            CorpusEntity corpus,
            String url,
            FetchedPage page,
            HtmlContentCleaner.CleanedContent cleaned) {
        String urlHash = UrlHasher.hash(url);
        DocumentEntity document = documentRepository
                .findByCorpusIdAndUrlHash(corpus.getId(), urlHash)
                .orElseGet(DocumentEntity::new);
        document.setCorpus(corpus);
        document.setCanonicalUrl(url);
        document.setUrlHash(urlHash);
        document.setHttpStatus(page.statusCode());
        document.setEtagHttp(page.etagHttp());
        document.setLastModifiedHttp(page.lastModifiedHttp());
        document.setLastModified(page.lastModified());
        document.setFetchedAt(Instant.now());
        document.setTitle(cleaned.title());
        document.setLanguage(UrlLocaleInferer.infer(url, cleaned.language()));
        document.setSectionPath(cleaned.sectionPath());
        document.setContentText(cleaned.text());
        document.setContentHash(UrlHasher.hash(cleaned.text()));
        document.setFetchStatus(DocumentFetchStatus.parsed);
        documentRepository.save(document);
        documentChunkWriter.replaceChunks(
                document, corpus, cleaned.text(), cleaned.sectionPath(), document.getLanguage());
        localePairLinker.link(
                corpus.getId(), document.getId(), document.getCanonicalUrl(), document.getLanguage());
        postPersistPipeline.afterDocumentPersisted(document.getId(), corpus.getId());
    }

    public record PlaywrightRefetchReport(
            String corpusName, int candidates, int updated, List<String> refetchedUrls) {}
}
