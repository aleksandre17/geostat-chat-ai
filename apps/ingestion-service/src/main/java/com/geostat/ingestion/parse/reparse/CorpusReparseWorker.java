package com.geostat.ingestion.parse.reparse;

import com.geostat.ingestion.chunk.DocumentChunkWriter;
import com.geostat.ingestion.crawl.archive.RawHtmlArchivePort;
import com.geostat.ingestion.crawl.fetch.Crawler4jPageFetcher;
import com.geostat.ingestion.crawl.fetch.FetchedPage;
import com.geostat.ingestion.crawl.frontier.UrlHasher;
import com.geostat.ingestion.events.DocumentPostPersistPipeline;
import com.geostat.ingestion.parse.DocumentDisplayFields;
import com.geostat.ingestion.parse.HtmlContentCleaner;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.parse.CorpusQualityGate;
import com.geostat.platform.parse.QualityThresholds;
import java.time.Instant;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One-document reparse in a single transaction (safe from async worker threads). */
@Component
@Profile("db")
public class CorpusReparseWorker {

    private final DocumentRepository documentRepository;
    private final HtmlContentCleaner contentCleaner;
    private final Crawler4jPageFetcher pageFetcher;
    private final RawHtmlArchivePort rawHtmlArchive;
    private final DocumentChunkWriter documentChunkWriter;
    private final DocumentPostPersistPipeline postPersistPipeline;
    private final CorpusQualityGate corpusQualityGate;
    private final QualityThresholds qualityThresholds;

    public CorpusReparseWorker(
            DocumentRepository documentRepository,
            HtmlContentCleaner contentCleaner,
            Crawler4jPageFetcher pageFetcher,
            RawHtmlArchivePort rawHtmlArchive,
            DocumentChunkWriter documentChunkWriter,
            DocumentPostPersistPipeline postPersistPipeline,
            CorpusQualityGate corpusQualityGate,
            QualityThresholds qualityThresholds) {
        this.documentRepository = documentRepository;
        this.contentCleaner = contentCleaner;
        this.pageFetcher = pageFetcher;
        this.rawHtmlArchive = rawHtmlArchive;
        this.documentChunkWriter = documentChunkWriter;
        this.postPersistPipeline = postPersistPipeline;
        this.corpusQualityGate = corpusQualityGate;
        this.qualityThresholds = qualityThresholds;
    }

    @Transactional
    public ReparseOutcome reparseDocument(CorpusEntity corpus, UUID documentId) throws Exception {
        DocumentEntity document = documentRepository.findById(documentId).orElseThrow();
        var html = loadHtml(corpus, document);
        HtmlContentCleaner.ProfileCleanResult cleanResult =
                contentCleaner.clean(html, document.getCanonicalUrl(), corpus.getName());
        HtmlContentCleaner.CleanedContent cleaned = cleanResult.content();

        document.setTitle(cleaned.title());
        document.setLanguage(UrlLocaleInferer.infer(document.getCanonicalUrl(), cleaned.language()));
        document.setSectionPath(cleaned.sectionPath());
        document.setContentText(cleaned.text());
        document.setContentHash(UrlHasher.hash(cleaned.text()));
        DocumentDisplayFields.apply(document, cleaned);
        document.setFetchedAt(Instant.now());

        CorpusQualityGate.Decision decision = CorpusQualityGate.Decision.ACCEPT;
        if (cleanResult.profileDocument().isPresent()) {
            decision = corpusQualityGate.evaluate(cleanResult.profileDocument().get(), qualityThresholds);
        }
        if (decision != CorpusQualityGate.Decision.ACCEPT) {
            document.setFetchStatus(DocumentFetchStatus.skipped);
            documentRepository.save(document);
            return ReparseOutcome.SKIPPED;
        }

        document.setFetchStatus(DocumentFetchStatus.parsed);
        documentRepository.save(document);
        documentChunkWriter.replaceChunks(
                document, corpus, cleaned.text(), cleaned.sectionPath(), document.getLanguage());
        postPersistPipeline.afterDocumentPersisted(document.getId(), corpus.getId());
        return ReparseOutcome.ACCEPTED;
    }

    private org.jsoup.nodes.Document loadHtml(CorpusEntity corpus, DocumentEntity document) throws Exception {
        if (document.getRawStorageKey() != null && !document.getRawStorageKey().isBlank()) {
            var archived = rawHtmlArchive.load(document.getRawStorageKey());
            if (archived.isPresent()) {
                return Jsoup.parse(new String(archived.get()), document.getCanonicalUrl());
            }
        }
        return refetch(corpus, document).html();
    }

    private FetchedPage refetch(CorpusEntity corpus, DocumentEntity document) throws Exception {
        return pageFetcher.fetch(document.getCanonicalUrl(), corpus);
    }

    enum ReparseOutcome {
        ACCEPTED,
        SKIPPED
    }
}
