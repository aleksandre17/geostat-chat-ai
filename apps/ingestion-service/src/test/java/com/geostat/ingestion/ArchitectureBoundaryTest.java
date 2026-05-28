package com.geostat.ingestion;

import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests enforcing architectural layer separation in ingestion-service.
 *
 * <p>These tests fail fast when a developer introduces a coupling violation,
 * preventing silent data overwrite bugs caused by cross-layer repository access.
 */
@AnalyzeClasses(
        packages = "com.geostat.ingestion",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {

    /**
     * Enrichment services must not depend on crawl frontier internals.
     * Enrichment reads from ingestion.document (via DocumentRepository),
     * not from url_frontier. Fixes the coupling bug found in JGraphTPageRankAuthorityDeriver
     * (Phase B of the coupling plan).
     */
    @ArchTest
    static final ArchRule enrichmentMustNotAccessUrlFrontier =
        noClasses()
            .that().resideInAPackage("..enrichment..")
            .should().accessClassesThat().resideInAPackage("..crawl.frontier..")
            .because("Enrichment layer must not depend on crawl internals. " +
                     "Use DocumentLinkRepository for graph data.");

    /**
     * Topic miner must use targeted updateTopicCluster() — not full entity save().
     * A full save() after loading an entity would overwrite concurrent enrichment writes
     * (summary, keywords, page_kind) due to lost update race condition.
     */
    @ArchTest
    static final ArchRule topicMinerMustNotCallFullSave =
        noClasses()
            .that().haveSimpleName("SmileKMeansTopicMiner")
            .should().callMethod(DocumentRepository.class, "save", Object.class)
            .because("SmileKMeansTopicMiner must use updateTopicCluster() targeted UPDATE, " +
                     "not save() which overwrites all columns.");

    /**
     * Authority deriver must not access enrichment results table.
     * Authority scoring is a separate pipeline phase; it reads from url_frontier (graph)
     * and writes only authority_score. It must not read or write enrichment columns.
     */
    @ArchTest
    static final ArchRule authorityDeriverMustNotAccessEnrichmentRunner =
        noClasses()
            .that().haveSimpleName("JGraphTPageRankAuthorityDeriver")
            .should().accessClassesThat().haveSimpleName("EnrichmentRunExecutor")
            .because("Authority deriver must be independent of the enrichment pipeline.");

    @ArchTest
    static final ArchRule parseMustNotAccessCrawlFrontier =
            noClasses()
                    .that().resideInAPackage("..parse..")
                    .should().accessClassesThat().resideInAPackage("..crawl.frontier..")
                    .because("Parse layer must not depend on crawl frontier internals.");

    @ArchTest
    static final ArchRule enrichmentMustNotImportCrawlFetch =
            noClasses()
                    .that().resideInAPackage("..enrichment..")
                    .should().accessClassesThat().resideInAPackage("..crawl.fetch..")
                    .because("Enrichment must not depend on concrete crawl fetch implementations.");

    @ArchTest
    static final ArchRule serviceClassesMustNotAccessInfrastructurePackage =
            noClasses()
                    .that().resideInAnyPackage("..enrichment..", "..quality..", "..parse..", "..index..", "..curation..")
                    .and().haveSimpleNameEndingWith("Service")
                    .should().accessClassesThat().resideInAPackage("..infrastructure..")
                    .because("Service classes must use ports — not concrete infrastructure adapters.");
}
