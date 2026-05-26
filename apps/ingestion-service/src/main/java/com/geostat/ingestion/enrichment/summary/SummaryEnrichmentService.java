package com.geostat.ingestion.enrichment.summary;



import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;

import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;

import com.geostat.ingestion.persistence.entity.DocumentEntity;

import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;

import com.geostat.platform.enrichment.DocumentContext;

import com.geostat.platform.enrichment.SummaryDeriver;

import com.geostat.platform.enrichment.SummaryResult;

import java.util.List;

import java.util.Optional;

import java.util.UUID;

import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.context.annotation.Profile;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



@Service

@Profile("db")

@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")

public class SummaryEnrichmentService {



    private final EnrichmentRunExecutor enrichmentRunExecutor;

    private final SummaryDeriver summaryDeriver;

    private final EnrichmentProperties properties;



    public SummaryEnrichmentService(

            EnrichmentRunExecutor enrichmentRunExecutor,

            SummaryDeriver summaryDeriver,

            EnrichmentProperties properties) {

        this.enrichmentRunExecutor = enrichmentRunExecutor;

        this.summaryDeriver = summaryDeriver;

        this.properties = properties;

    }



    @Transactional

    public void enrichDocument(UUID documentId) {

        String modelVersion = properties.modelVersion();

        enrichmentRunExecutor.run(

                documentId,

                EnrichmentDeriverKind.summary,

                modelVersion,

                properties.maxRetries(),

                this::shouldSkip,

                document -> summaryDeriver.derive(toContext(document)),

                this::persistSummary,

                "summary");

    }



    private boolean shouldSkip(DocumentEntity document) {

        return document.getContentText() == null || document.getContentText().isBlank();

    }



    private void persistSummary(DocumentEntity document, SummaryResult result) {

        document.setSummaryKa(result.summaryKa());

        document.setSummaryEn(result.summaryEn());

    }



    private static DocumentContext toContext(DocumentEntity document) {

        String sectionPath = Optional.ofNullable(document.getSectionPath()).orElseGet(List::of).stream()

                .collect(Collectors.joining(" > "));

        return new DocumentContext(

                document.getId(),

                document.getCanonicalUrl(),

                document.getTitle(),

                document.getContentText(),

                document.getLanguage(),

                sectionPath);

    }

}

