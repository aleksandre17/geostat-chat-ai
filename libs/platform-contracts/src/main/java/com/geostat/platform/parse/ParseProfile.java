package com.geostat.platform.parse;

import java.util.List;

/** Manifest-driven HTML parse profile (loaded from ops/config/corpus/*-parse.yaml). */
public record ParseProfile(
        String corpus,
        List<String> rootSelectors,
        List<String> removeSelectors,
        BoilerplateMarkers boilerplateMarkers,
        boolean stripLeading,
        boolean stripTrailing,
        boolean extractTables,
        boolean preserveHeadings,
        List<String> languageInferFrom) {

    public ParseProfile {
        corpus = corpus == null ? "" : corpus;
        rootSelectors = rootSelectors == null ? List.of() : List.copyOf(rootSelectors);
        removeSelectors = removeSelectors == null ? List.of() : List.copyOf(removeSelectors);
        boilerplateMarkers = boilerplateMarkers == null ? new BoilerplateMarkers(List.of(), List.of(), List.of()) : boilerplateMarkers;
        languageInferFrom = languageInferFrom == null ? List.of() : List.copyOf(languageInferFrom);
    }

    public ParseProfile forCorpus(String name) {
        return new ParseProfile(
                name,
                rootSelectors,
                removeSelectors,
                boilerplateMarkers,
                stripLeading,
                stripTrailing,
                extractTables,
                preserveHeadings,
                languageInferFrom);
    }
}
