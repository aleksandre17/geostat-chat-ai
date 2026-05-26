package com.geostat.ingestion.enrichment.pagekind;

import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.PageKindResult;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** URL/title heuristic pre-filter — skips Gemini on clear cases (spec §U01f). */
public final class PageKindUrlHeuristic {

    private static final Pattern DATASET_EXTENSION =
            Pattern.compile(".*\\.(xls|xlsx|csv|tsv|zip|sav|dta)(\\?.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORT_EXTENSION =
            Pattern.compile(".*\\.(pdf|doc|docx)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    private PageKindUrlHeuristic() {}

    public static Optional<PageKindResult> classify(DocumentContext document, String modelVersion) {
        String heuristicVersion = modelVersion + "-heuristic";
        String path = extractPath(document.url());
        if (path.isBlank()) {
            return Optional.empty();
        }
        String lowerPath = path.toLowerCase(Locale.ROOT);

        if (lowerPath.contains("/news/") || lowerPath.contains("/newsroom/")) {
            return result(PageKindValues.NEWS, 0.95, heuristicVersion);
        }
        if (DATASET_EXTENSION.matcher(lowerPath).matches()
                || lowerPath.contains("/download/")
                || lowerPath.contains("/microdata/")) {
            return result(PageKindValues.DATASET, 0.95, heuristicVersion);
        }
        if (REPORT_EXTENSION.matcher(lowerPath).matches()
                || lowerPath.contains("/publications/")
                || lowerPath.contains("/publication/")) {
            return result(PageKindValues.REPORT, 0.92, heuristicVersion);
        }
        if (lowerPath.contains("/faq") || lowerPath.endsWith("/questions")) {
            return result(PageKindValues.FAQ, 0.9, heuristicVersion);
        }
        if (isNavigationPath(lowerPath)) {
            return result(PageKindValues.NAVIGATION, 0.9, heuristicVersion);
        }
        if (isPortalLanding(lowerPath)) {
            return result(PageKindValues.PORTAL, 0.88, heuristicVersion);
        }
        return Optional.empty();
    }

    static boolean isNavigationPath(String lowerPath) {
        return lowerPath.contains("/sitemap")
                || lowerPath.contains("/about-us")
                || lowerPath.contains("/about/")
                || lowerPath.contains("/contact")
                || lowerPath.contains("/privacy")
                || lowerPath.contains("/terms")
                || lowerPath.endsWith("/search");
    }

    static boolean isPortalLanding(String lowerPath) {
        return lowerPath.equals("/ka")
                || lowerPath.equals("/en")
                || lowerPath.equals("/ka/")
                || lowerPath.equals("/en/")
                || lowerPath.matches("^/(ka|en)/modules/categories/?$");
    }

    static String extractPath(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String path = URI.create(url.strip()).getPath();
            return path != null ? path : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static Optional<PageKindResult> result(String kind, double confidence, String modelVersion) {
        return Optional.of(new PageKindResult(kind, confidence, modelVersion));
    }
}
