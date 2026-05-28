package com.geostat.ingestion.crawl.kind;

import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.PageKind;
import com.geostat.platform.crawl.PageKindDetector;
import com.geostat.platform.parse.ParseProfile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * HTML-signal-based page kind detector.
 *
 * <p>Fallback when URL patterns give {@link PageKind#UNKNOWN}.
 * Signal priority (highest to lowest):
 * <ol>
 *   <li>JSON-LD {@code @type} — publisher-declared, most authoritative
 *   <li>OpenGraph {@code og:type}
 *   <li>Schema.org {@code itemtype} attribute
 *   <li>DOM structural signals (article+h1, table density)
 * </ol>
 *
 * <p>Parses HTML — only invoked when URL patterns yield no match.
 */
@Component
public class HtmlSignalPageKindDetector implements PageKindDetector {

    @Override
    public String detect(FetchedPage page, ParseProfile profile) {
        Document doc = Jsoup.parse(page.html(), page.url());

        String fromJsonLd = detectFromJsonLd(doc);
        if (!PageKind.UNKNOWN.equals(fromJsonLd)) {
            return fromJsonLd;
        }

        String ogType = doc.select("meta[property=og:type]").attr("content").toLowerCase();
        if ("article".equals(ogType) || "newsarticle".equals(ogType)) {
            return PageKind.NEWS;
        }

        String schemaType = doc.select("[itemtype]").attr("itemtype").toLowerCase();
        if (schemaType.contains("schema.org/dataset")) {
            return PageKind.DATASET;
        }
        if (schemaType.contains("schema.org/newsarticle")) {
            return PageKind.NEWS;
        }
        if (schemaType.contains("schema.org/article")) {
            return PageKind.NEWS;
        }

        if (doc.selectFirst("article") != null
                && doc.selectFirst("article h1") != null) {
            return PageKind.NEWS;
        }
        if (countDataTables(doc) >= 2) {
            return PageKind.DATASET;
        }

        return profile.defaultPageKind();
    }

    private String detectFromJsonLd(Document doc) {
        for (Element el : doc.select("script[type=application/ld+json]")) {
            String json = el.html().toLowerCase();
            if (json.contains("\"newsarticle\"") || json.contains("\"article\"")) {
                return PageKind.NEWS;
            }
            if (json.contains("\"dataset\"")) {
                return PageKind.DATASET;
            }
            if (json.contains("\"webpage\"") && json.contains("\"breadcrumb\"")) {
                return PageKind.PORTAL;
            }
        }
        return PageKind.UNKNOWN;
    }

    private int countDataTables(Document doc) {
        return (int) doc.select("table").stream()
                .filter(t -> t.select("tr").size() >= 3
                          && t.select("tr:first-child td, tr:first-child th").size() >= 2)
                .count();
    }
}
