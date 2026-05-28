package com.geostat.chat;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit boundary tests enforcing Clean Architecture layer separation in chat-api.
 */
@AnalyzeClasses(
        packages = "com.geostat.chat",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {

    @ArchTest
    static final ArchRule applicationMustNotAccessInfrastructure =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().accessClassesThat().resideInAPackage("..infrastructure..")
                    .because("Application layer must depend on domain ports, not infrastructure adapters.");

    @ArchTest
    static final ArchRule domainMustNotAccessOuterLayers =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().accessClassesThat().resideInAnyPackage("..infrastructure..", "..application..")
                    .because("Domain layer must remain free of application and infrastructure coupling.");

    @ArchTest
    static final ArchRule applicationMustNotUseConcreteConfigOrCatalogLoaders =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure.config..",
                            "..infrastructure.catalog..",
                            "..infrastructure.prompt..",
                            "..infrastructure.query..",
                            "..infrastructure.retrieval..")
                    .because("Application services must use domain ports — not concrete catalog loaders or config beans.");
}
