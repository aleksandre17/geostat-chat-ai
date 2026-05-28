package com.geostat.platform.parse;

import com.geostat.platform.crawl.NetworkPolicy;
import com.geostat.platform.crawl.PageKind;
import com.geostat.platform.crawl.PageKindRule;
import com.geostat.platform.crawl.RenderMode;
import java.util.List;

/** Manifest-driven HTML parse profile (loaded from ops/config/corpus/*-parse.yaml). */
public record ParseProfile(
        String corpus,
        List<String> rootSelectors,
        List<String> removeSelectors,
        List<String> addSelectors,
        BoilerplateMarkers boilerplateMarkers,
        boolean stripLeading,
        boolean stripTrailing,
        boolean extractTables,
        boolean preserveHeadings,
        LanguageConfig language,
        RenderMode renderMode,
        NetworkPolicy networkPolicy,
        List<PageKindRule> pageKindRules,
        String defaultPageKind,
        String rootSelectorStrategy) {

    public record LanguageConfig(List<String> inferFrom, String defaultFallback) {

        public LanguageConfig {
            inferFrom = inferFrom == null ? List.of() : List.copyOf(inferFrom);
        }
    }

    public ParseProfile {
        corpus = corpus == null ? "" : corpus;
        rootSelectors = rootSelectors == null ? List.of() : List.copyOf(rootSelectors);
        removeSelectors = removeSelectors == null ? List.of() : List.copyOf(removeSelectors);
        addSelectors = addSelectors == null ? List.of() : List.copyOf(addSelectors);
        boilerplateMarkers = boilerplateMarkers == null
                ? new BoilerplateMarkers(List.of(), List.of(), List.of())
                : boilerplateMarkers;
        language = language == null ? new LanguageConfig(List.of(), null) : language;
        if (renderMode == null) {
            renderMode = RenderMode.STATIC;
        }
        if (networkPolicy == null) {
            networkPolicy = NetworkPolicy.defaults();
        }
        pageKindRules = pageKindRules == null ? List.of() : List.copyOf(pageKindRules);
        if (defaultPageKind == null || defaultPageKind.isBlank()) {
            defaultPageKind = PageKind.UNKNOWN;
        }
        if (rootSelectorStrategy == null || rootSelectorStrategy.isBlank()) {
            rootSelectorStrategy = "firstMatch";
        }
    }

    public List<String> languageInferFrom() {
        return language.inferFrom();
    }

    /** Never null — returns configured fallback or hardcoded {@code ka}. */
    public String languageDefaultFallback() {
        if (language != null
                && language.defaultFallback() != null
                && !language.defaultFallback().isBlank()) {
            return language.defaultFallback();
        }
        return "ka";
    }

    public ParseProfile forCorpus(String name) {
        return new ParseProfile(
                name,
                rootSelectors,
                removeSelectors,
                addSelectors,
                boilerplateMarkers,
                stripLeading,
                stripTrailing,
                extractTables,
                preserveHeadings,
                language,
                renderMode,
                networkPolicy,
                pageKindRules,
                defaultPageKind,
                rootSelectorStrategy);
    }
}
