package com.geostat.ingestion.parse.reparse;

import com.geostat.ingestion.chunk.DocumentChunkWriter;
import com.geostat.ingestion.crawl.archive.RawHtmlArchivePort;
import com.geostat.ingestion.crawl.fetch.FetchedPage;
import com.geostat.platform.crawl.PageFetcher;
import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.events.DocumentPostPersistPipeline;
import com.geostat.ingestion.parse.DocumentDisplayFields;
import com.geostat.ingestion.parse.HtmlContentCleaner;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfigLoader;
import com.geostat.ingestion.parse.validation.ValidationViolationCodes;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.parse.CorpusQualityGate;
import com.geostat.platform.parse.QualityThresholds;
import com.geostat.platform.parse.ValidationOutcome;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One-document reparse: short read TX, HTML load outside TX, parse/persist TX. */
@Component
@Profile("db")
public class CorpusReparseWorker {

    private final DocumentRepository documentRepository;
    private final HtmlContentCleaner contentCleaner;
    private final PageFetcher routingPageFetcher;
    private final CorpusConfigurationLoader corpusConfigurationLoader;
    private final RawHtmlArchivePort rawHtmlArchive;
    private final DocumentChunkWriter documentChunkWriter;
    private final DocumentPostPersistPipeline postPersistPipeline;
    private final CorpusQualityGate corpusQualityGate;
    private final QualityThresholds qualityThresholds;
    private final CorpusQualityGateConfigLoader gateConfigLoader;

    private CorpusReparseWorker self;

    public CorpusReparseWorker(
            DocumentRepository documentRepository,
            HtmlContentCleaner contentCleaner,
            PageFetcher routingPageFetcher,
            CorpusConfigurationLoader corpusConfigurationLoader,
            RawHtmlArchivePort rawHtmlArchive,
            DocumentChunkWriter documentChunkWriter,
            DocumentPostPersistPipeline postPersistPipeline,
            CorpusQualityGate corpusQualityGate,
            QualityThresholds qualityThresholds,
            CorpusQualityGateConfigLoader gateConfigLoader) {
        this.documentRepository = documentRepository;
        this.contentCleaner = contentCleaner;
        this.routingPageFetcher = routingPageFetcher;
        this.corpusConfigurationLoader = corpusConfigurationLoader;
        this.rawHtmlArchive = rawHtmlArchive;
        this.documentChunkWriter = documentChunkWriter;
        this.postPersistPipeline = postPersistPipeline;
        this.corpusQualityGate = corpusQualityGate;
        this.qualityThresholds = qualityThresholds;
        this.gateConfigLoader = gateConfigLoader;
    }

    @Autowired
    @Lazy
    void setSelf(CorpusReparseWorker self) {
        this.self = self;
    }

    public ReparseOutcome reparseDocument(CorpusEntity corpus, UUID documentId) throws Exception {
        DocumentEntity document = self.loadDocument(documentId);
        org.jsoup.nodes.Document html = loadHtml(corpus, document);
        return self.parseAndPersist(corpus, document, html);
    }

    @Transactional(readOnly = true)
    DocumentEntity loadDocument(UUID documentId) {
        return documentRepository.findById(documentId).orElseThrow();
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

    @Transactional
    ReparseOutcome parseAndPersist(
            CorpusEntity corpus, DocumentEntity document, org.jsoup.nodes.Document html) {
        HtmlContentCleaner.ProfileCleanResult cleanResult =
                contentCleaner.clean(html, document.getCanonicalUrl(), corpus.getName());
        HtmlContentCleaner.CleanedContent cleaned = cleanResult.content();
        Optional<ValidationOutcome> validationOutcome = cleanResult.validationOutcome();

        if (validationOutcome.isPresent() && validationOutcome.get().isRejected()) {
            return ReparseOutcome.SKIPPED;
        }

        document.setTitle(cleaned.title());
        document.setLanguage(UrlLocaleInferer.infer(document.getCanonicalUrl(), cleaned.language()));
        document.setSectionPath(cleaned.sectionPath());
        document.setContentText(cleaned.text());
        document.setContentHash(UrlHasher.hash(cleaned.text()));
        DocumentDisplayFields.apply(document, cleaned);
        document.setFetchedAt(Instant.now());

        validationOutcome.ifPresent(outcome -> {
            document.setQualityScore(outcome.quality().name().toLowerCase());
            document.setValidationViolations(ValidationViolationCodes.toArray(outcome.violations()));
        });

        if (validationOutcome.isPresent() && validationOutcome.get().isSkip()) {
            document.setFetchStatus(DocumentFetchStatus.parsed);
            documentRepository.save(document);
            return ReparseOutcome.SKIPPED;
        }

        CorpusQualityGate.Decision decision = CorpusQualityGate.Decision.ACCEPT;
        if (cleanResult.profileDocument().isPresent()) {
            QualityThresholds thresholds = gateConfigLoader
                    .thresholdsForCorpus(corpus.getName())
                    .map(t -> mergeWithGlobal(t, qualityThresholds))
                    .orElse(qualityThresholds);
            decision = corpusQualityGate.evaluate(cleanResult.profileDocument().get(), thresholds);
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

    private FetchedPage refetch(CorpusEntity corpus, DocumentEntity document) throws Exception {
        var platformOptions = com.geostat.platform.crawl.FetchOptions.forProfile(
                corpusConfigurationLoader.parseProfileFor(corpus.getName()));
        var platformPage = routingPageFetcher.fetch(document.getCanonicalUrl(), platformOptions);
        return FetchedPage.fromPlatform(document.getCanonicalUrl(), platformPage);
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

    enum ReparseOutcome {
        ACCEPTED,
        SKIPPED
    }
}
