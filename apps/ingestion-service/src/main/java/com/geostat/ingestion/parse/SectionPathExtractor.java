package com.geostat.ingestion.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** Builds heading breadcrumb (h1→h2→h3) for chunk/RAG metadata. */
public final class SectionPathExtractor {

    private SectionPathExtractor() {}

    public static List<String> extract(Document html) {
        List<String> path = new ArrayList<>();
        Element main = html.selectFirst("main, article, [role=main]");
        Element root = main != null ? main : html.body();
        if (root == null) {
            return path;
        }
        Elements headings = root.select("h1, h2, h3");
        for (Element h : headings) {
            String t = h.text().trim();
            if (!t.isBlank() && t.length() <= 120) {
                path.add(t);
            }
            if (path.size() >= 5) {
                break;
            }
        }
        return List.copyOf(path);
    }

    /**
     * Extracts navigation breadcrumb path from the original document (before removeSelectors).
     *
     * @return joined breadcrumb segments, or {@code null} if no breadcrumb nav found
     */
    public static String extractNavBreadcrumb(Document html) {
        Elements links = html.select(
                ".breadcrumb-wrapper a, "
                        + ".breadcrumb a, "
                        + "nav[aria-label*=breadcrumb] a, "
                        + "ol.breadcrumb li a");
        if (links.isEmpty()) {
            return null;
        }

        String path = links.stream()
                .map(e -> e.text().strip())
                .filter(t -> !t.isBlank() && t.length() > 1)
                .distinct()
                .collect(Collectors.joining(" > "));

        return path.isBlank() ? null : path;
    }

    public static String joinPath(List<String> path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return String.join(" > ", path);
    }
}
