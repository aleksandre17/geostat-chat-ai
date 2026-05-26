package com.geostat.platform.parse;

import java.util.List;

/** YAML-driven crawl/enqueue policy (ops/config/corpus/*-policy.yaml). */
public record CorpusPolicyV2(
        String corpus,
        List<String> seeds,
        List<String> curatedUrls,
        List<String> allowedHosts,
        SubdomainMode subdomainMode,
        List<String> subdomainAllow,
        List<String> excludePatterns,
        List<String> includePatterns) {

    public CorpusPolicyV2 {
        corpus = corpus == null ? "" : corpus;
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
        curatedUrls = curatedUrls == null ? List.of() : List.copyOf(curatedUrls);
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
        subdomainMode = subdomainMode == null ? SubdomainMode.all : subdomainMode;
        subdomainAllow = subdomainAllow == null ? List.of() : List.copyOf(subdomainAllow);
        excludePatterns = excludePatterns == null ? List.of() : List.copyOf(excludePatterns);
        includePatterns = includePatterns == null ? List.of() : List.copyOf(includePatterns);
    }
}
