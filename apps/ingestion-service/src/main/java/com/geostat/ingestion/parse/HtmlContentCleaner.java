package com.geostat.ingestion.parse;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class HtmlContentCleaner {

    public CleanedContent clean(Document html) {
        Document clone = html.clone();
        clone.select("script, style, nav, footer, header, noscript, iframe, svg").remove();

        Element main = clone.selectFirst("main, article, [role=main]");
        Element root = main != null ? main : clone.body();

        String title = html.title();
        String text = root == null ? "" : root.text().trim().replaceAll("\\s+", " ");
        String language = html.select("html").attr("lang");
        if (language.isBlank()) {
            language = null;
        } else {
            language = language.split("-")[0].toLowerCase();
        }
        return new CleanedContent(title, text, language);
    }

    public record CleanedContent(String title, String text, String language) {}
}
