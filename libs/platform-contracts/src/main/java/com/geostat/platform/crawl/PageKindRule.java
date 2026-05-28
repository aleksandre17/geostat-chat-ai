package com.geostat.platform.crawl;

import java.util.List;

/**
 * A single YAML-driven page-kind detection rule.
 *
 * <p>URL patterns:
 * <ul>
 *   <li>Pattern starting with {@code ^} or ending with {@code $} → full Java regex
 *   <li>Otherwise → substring containment (case-insensitive)
 * </ul>
 *
 * <p>When multiple rules match, the one with highest {@code priority} wins.
 * Tie-breaking: first rule in the list wins.
 */
public record PageKindRule(
        String       kind,
        List<String> urlPatterns,
        int          priority) {

    public PageKindRule {
        urlPatterns = urlPatterns == null ? List.of() : List.copyOf(urlPatterns);
    }
}
