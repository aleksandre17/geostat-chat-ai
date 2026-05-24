package com.geostat.ingestion.parse;

import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class HtmlContentCleaner {

    private final PageDisplayMetadataExtractor displayMetadataExtractor;

    public HtmlContentCleaner(PageDisplayMetadataExtractor displayMetadataExtractor) {
        this.displayMetadataExtractor = displayMetadataExtractor;
    }

    public CleanedContent clean(Document html) {
        Document clone = html.clone();
        clone.select("script, style, nav, footer, header, noscript, iframe, svg").remove();

        Element main = clone.selectFirst("main, article, [role=main]");
        Element root = main != null ? main : clone.body();

        String title = resolveTitle(html, root);
        String text = root == null ? "" : root.text().trim().replaceAll("\\s+", " ");
        List<String> sectionPath = SectionPathExtractor.extract(html);
        String language = html.select("html").attr("lang");
        if (language.isBlank()) {
            language = null;
        } else {
            language = language.split("-")[0].toLowerCase();
        }
        PageDisplayMetadataExtractor.DisplayMetadata display =
                displayMetadataExtractor.extract(html, title, sectionPath);
        return new CleanedContent(
                title,
                text,
                language,
                sectionPath,
                display.metaDescription(),
                display.leadText(),
                display.displayDescription());
    }

    private static String resolveTitle(Document html, Element root) {
        String title = html.title() != null ? html.title().strip() : "";
        if (!title.isBlank()) {
            return title;
        }
        if (root != null) {
            Element h1 = root.selectFirst("h1");
            if (h1 != null && !h1.text().isBlank()) {
                return h1.text().strip();
            }
        }
        return title;
    }

    public record CleanedContent(
            String title,
            String text,
            String language,
            List<String> sectionPath,
            String metaDescription,
            String leadText,
            String displayDescription) {}
}
