package com.geostat.ingestion.crawl.kind;

import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.PageKindDetector;
import com.geostat.platform.crawl.PageKindRule;
import com.geostat.platform.parse.ParseProfile;
import java.util.Comparator;
import org.springframework.stereotype.Component;

/**
 * URL-pattern-based page kind detector.
 *
 * <p>Rules are loaded from {@code pageKindRules} in the parse profile YAML.
 * Pattern matching:
 * <ul>
 *   <li>Pattern starting with {@code ^} or ending with {@code $} → full Java regex (case-insensitive)
 *   <li>Otherwise → substring containment (case-insensitive)
 * </ul>
 * When multiple rules match, the one with the highest priority wins.
 * Tie-breaking: first rule in the list wins.
 *
 * <p>Fast: no HTML parsing — URL only.
 */
@Component
public class UrlPatternPageKindDetector implements PageKindDetector {

    @Override
    public String detect(FetchedPage page, ParseProfile profile) {
        String url = page.url().toLowerCase(java.util.Locale.ROOT);
        return profile.pageKindRules().stream()
                .filter(rule -> rule.urlPatterns().stream().anyMatch(p -> matches(url, p)))
                .max(Comparator.comparingInt(PageKindRule::priority))
                .map(PageKindRule::kind)
                .orElse(profile.defaultPageKind());
    }

    private static boolean matches(String url, String pattern) {
        String p = pattern.toLowerCase(java.util.Locale.ROOT);
        if (p.startsWith("^") || p.endsWith("$")) {
            return url.matches(p);
        }
        return url.contains(p);
    }
}
