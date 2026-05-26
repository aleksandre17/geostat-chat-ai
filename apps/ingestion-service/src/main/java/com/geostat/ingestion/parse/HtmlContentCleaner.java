package com.geostat.ingestion.parse;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.ContentExtractor;
import com.geostat.platform.parse.HtmlPageInput;
import com.geostat.platform.parse.ParseProfile;
import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** Facade over legacy Jsoup cleanup and profile-driven extraction (P0 L1). */
@Component
public class HtmlContentCleaner {

    private final PageDisplayMetadataExtractor displayMetadataExtractor;
    private final ParseProperties parseProperties;
    private final CorpusConfigurationLoader configurationLoader;
    private final ContentExtractor contentExtractor;

    public HtmlContentCleaner(
            PageDisplayMetadataExtractor displayMetadataExtractor,
            ParseProperties parseProperties,
            CorpusConfigurationLoader configurationLoader,
            ContentExtractor contentExtractor) {
        this.displayMetadataExtractor = displayMetadataExtractor;
        this.parseProperties = parseProperties;
        this.configurationLoader = configurationLoader;
        this.contentExtractor = contentExtractor;
    }

    public CleanedContent clean(Document html) {
        return clean(html, "", null).content();
    }

    public ProfileCleanResult clean(Document html, String canonicalUrl, String corpusName) {
        if (parseProperties.profile().enabled() && corpusName != null && !corpusName.isBlank()) {
            ParseProfile profile = configurationLoader.parseProfileFor(corpusName);
            CleanedDocument extracted = contentExtractor.extract(new HtmlPageInput(html.html(), canonicalUrl), profile);
            return new ProfileCleanResult(toCleanedContent(extracted, canonicalUrl), Optional.of(extracted));
        }
        return new ProfileCleanResult(legacyClean(html), Optional.empty());
    }

    private CleanedContent legacyClean(Document html) {
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

    private CleanedContent toCleanedContent(CleanedDocument doc, String canonicalUrl) {
        String language = UrlLocaleInferer.infer(canonicalUrl, doc.language());
        return new CleanedContent(
                doc.title(),
                doc.bodyText(),
                language,
                doc.sectionPath(),
                doc.metaDescription(),
                doc.leadText(),
                doc.displayDescription());
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

    public record ProfileCleanResult(CleanedContent content, Optional<CleanedDocument> profileDocument) {}
}
