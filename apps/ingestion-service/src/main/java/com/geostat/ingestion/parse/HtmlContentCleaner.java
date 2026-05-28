package com.geostat.ingestion.parse;

import com.geostat.ingestion.parse.profile.CorpusConfigurationLoader;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.parse.strategy.GeostatNewsExtractionStrategy;
import com.geostat.ingestion.parse.validation.DocumentValidationPipeline;
import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.ContentExtractor;
import com.geostat.platform.parse.HtmlPageInput;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.StatisticalContentGuard;
import com.geostat.platform.parse.TextSanitizer;
import com.geostat.platform.parse.ValidationOutcome;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Facade over legacy Jsoup cleanup and profile-driven extraction (P0 L1). */
@Component
public class HtmlContentCleaner {

    private static final Logger log = LoggerFactory.getLogger(HtmlContentCleaner.class);

    // Minimal hardcoded boilerplate phrases — profile-agnostic, always safe to strip
    private static final List<String> LEGACY_BOILERPLATE_STARTS = List.of(
            "skip to content",
            "გამოიწერეთ სიახლეები",
            "csv download",
            "უკან დაბრუნება",
            "სრულად ნახვა",
            "read more",
            "archive",
            "subscribe to news",
            "audio narration");
    private static final List<String> LEGACY_BOILERPLATE_CONTAINS = List.of(
            "crafted by",
            "ვებგვერდის ადაპტირებული ვერსია",
            "official website of geostat",
            "საქსტატის ოფიციალური ვებგვერდი");

    private final PageDisplayMetadataExtractor displayMetadataExtractor;
    private final ParseProperties parseProperties;
    private final CorpusConfigurationLoader configurationLoader;
    private final ContentExtractor contentExtractor;
    private final DocumentValidationPipeline validationPipeline;
    private final GeostatNewsExtractionStrategy newsExtractionStrategy;

    public HtmlContentCleaner(
            PageDisplayMetadataExtractor displayMetadataExtractor,
            ParseProperties parseProperties,
            CorpusConfigurationLoader configurationLoader,
            ContentExtractor contentExtractor,
            DocumentValidationPipeline validationPipeline,
            GeostatNewsExtractionStrategy newsExtractionStrategy) {
        this.displayMetadataExtractor = displayMetadataExtractor;
        this.parseProperties = parseProperties;
        this.configurationLoader = configurationLoader;
        this.contentExtractor = contentExtractor;
        this.validationPipeline = validationPipeline;
        this.newsExtractionStrategy = newsExtractionStrategy;
    }

    @PostConstruct
    void warnIfProfileDisabled() {
        if (!parseProperties.profile().enabled()) {
            log.warn(
                    "[config] parse.profile.enabled=false — extraction quality degraded; "
                            + "set INGESTION_PARSE_PROFILE_ENABLED=true");
        }
    }

    public CleanedContent clean(Document html) {
        return clean(html, "", null).content();
    }

    public ProfileCleanResult clean(Document html, String canonicalUrl, String corpusName) {
        if (parseProperties.profile().enabled() && corpusName != null && !corpusName.isBlank()) {
            ParseProfile profile = configurationLoader.parseProfileFor(corpusName);
            CleanedDocument extracted =
                    contentExtractor.extract(new HtmlPageInput(html.html(), canonicalUrl), profile);
            if (newsExtractionStrategy.isNewsUrl(canonicalUrl)) {
                extracted = newsExtractionStrategy.extract(
                        new HtmlPageInput(html.html(), canonicalUrl), profile, extracted);
            }
            ValidationOutcome outcome = validationPipeline.validate(extracted, profile);
            CleanedDocument validated = outcome.document();
            return new ProfileCleanResult(
                    toCleanedContent(validated, canonicalUrl),
                    Optional.of(validated),
                    Optional.of(outcome));
        }
        return new ProfileCleanResult(legacyClean(html), Optional.empty(), Optional.empty());
    }

    private CleanedContent legacyClean(Document html) {
        log.warn(
                "[legacy-clean] No ParseProfile resolved — falling back to legacy extraction. "
                        + "Boilerplate filtering will be limited. Ensure corpusName is set and "
                        + "parseProperties.profile.enabled=true for production use.");

        Document clone = html.clone();
        clone.select(
                        "script, style, nav, footer, header, noscript, iframe, svg, "
                                + "aside, figure, figcaption, [hidden], [aria-hidden=true], "
                                + ".breadcrumb, .breadcrumb-wrapper, .pagination, "
                                + ".cookie-banner, .social-share, .modal, .modal-csv")
                .remove();

        Element main = clone.selectFirst("main, article, [role=main]");
        Element root = main != null ? main : clone.body();

        String title = resolveTitle(html, root);
        List<String> sectionPath = SectionPathExtractor.extract(html);
        String language = inferLegacyLanguage(html);

        List<String> cleanBlocks = new ArrayList<>();
        if (root != null) {
            for (Element el : root.select("h1,h2,h3,h4,h5,h6,p,li")) {
                String raw = el.text().trim();
                String block = TextSanitizer.sanitizeBlock(raw);
                if (block.isBlank()) {
                    continue;
                }
                if (isLegacyBoilerplate(block)) {
                    continue;
                }
                cleanBlocks.add(block);
            }
            if (cleanBlocks.isEmpty()) {
                String fallback = TextSanitizer.sanitize(root.text());
                if (!fallback.isBlank()) {
                    cleanBlocks.add(fallback);
                }
            }
        }
        String text = String.join("\n\n", cleanBlocks);

        PageDisplayMetadataExtractor.DisplayMetadata display =
                displayMetadataExtractor.extract(html, title, sectionPath);
        return new CleanedContent(
                title,
                text,
                language,
                sectionPath,
                display.metaDescription(),
                display.leadText(),
                display.displayDescription(),
                SectionPathExtractor.extractNavBreadcrumb(html),
                null);
    }

    private static boolean isLegacyBoilerplate(String text) {
        if (StatisticalContentGuard.isStatisticalContent(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String s : LEGACY_BOILERPLATE_STARTS) {
            if (lower.startsWith(s)) {
                return true;
            }
        }
        for (String s : LEGACY_BOILERPLATE_CONTAINS) {
            if (lower.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private static String inferLegacyLanguage(Document html) {
        String lang = html.select("html").attr("lang");
        if (!lang.isBlank()) {
            return lang.split("-")[0].toLowerCase();
        }
        return "ka";
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
                doc.displayDescription(),
                doc.navBreadcrumb(),
                doc.publishedAt());
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
            String displayDescription,
            String navBreadcrumb,
            Instant publishedAt) {}

    public record ProfileCleanResult(
            CleanedContent content, Optional<CleanedDocument> profileDocument, Optional<ValidationOutcome> validationOutcome) {}
}
