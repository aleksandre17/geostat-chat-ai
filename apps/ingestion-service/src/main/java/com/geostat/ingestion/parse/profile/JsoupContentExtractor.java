package com.geostat.ingestion.parse.profile;

import com.geostat.ingestion.parse.JsonLdExtractor;
import com.geostat.ingestion.parse.NoindexDetector;
import com.geostat.ingestion.parse.PageDisplayMetadataExtractor;
import com.geostat.ingestion.parse.SectionPathExtractor;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import java.time.Instant;
import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.PageKindDetector;
import com.geostat.platform.parse.BoilerplateStripper;
import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.ContentExtractor;
import com.geostat.platform.parse.ExtractionStrategy;
import com.geostat.platform.parse.ExtractionStrategyRegistry;
import com.geostat.platform.parse.HtmlPageInput;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.TextSanitizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class JsoupContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsoupContentExtractor.class);

    private final BoilerplateStripper boilerplateStripper;
    private final PageDisplayMetadataExtractor displayMetadataExtractor;
    private final JsonLdExtractor jsonLdExtractor;
    private final ExtractionStrategyRegistry registry;
    private final PageKindDetector pageKindDetector;

    public JsoupContentExtractor(
            BoilerplateStripper boilerplateStripper,
            PageDisplayMetadataExtractor displayMetadataExtractor,
            JsonLdExtractor jsonLdExtractor,
            @Lazy ExtractionStrategyRegistry registry,
            PageKindDetector pageKindDetector) {
        this.boilerplateStripper = boilerplateStripper;
        this.displayMetadataExtractor = displayMetadataExtractor;
        this.jsonLdExtractor = jsonLdExtractor;
        this.registry = registry;
        this.pageKindDetector = pageKindDetector;
    }

    @Override
    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
        FetchedPage fetchedForDetection = new FetchedPage(
                page.canonicalUrl(),
                page.html(),
                200,
                "text/html",
                profile.renderMode());

        String pageKind = pageKindDetector.detect(fetchedForDetection, profile);

        ExtractionStrategy strategy = registry.resolve(
                profile.corpus() != null ? profile.corpus() : "", pageKind);
        if (strategy.isDefaultFallback()) {
            return extractInternal(page, profile);
        }
        return strategy.extract(page, profile);
    }

    public CleanedDocument extractInternal(HtmlPageInput page, ParseProfile profile) {
        Document html = Jsoup.parse(page.html(), page.canonicalUrl());

        if (NoindexDetector.isNoindex(html)) {
            log.debug("[noindex] page skipped: {}", page.canonicalUrl());
            return CleanedDocument.noindexMarker(page.canonicalUrl());
        }

        JsonLdExtractor.JsonLdResult ldData = jsonLdExtractor.extract(html);

        Document clone = html.clone();
        for (String selector : profile.removeSelectors()) {
            if (selector != null && !selector.isBlank()) {
                clone.select(selector).remove();
            }
        }

        Element root = selectRoot(clone, profile.rootSelectors());
        String title = resolveTitle(html, root);
        String navBreadcrumb = SectionPathExtractor.extractNavBreadcrumb(html);
        if ((navBreadcrumb == null || navBreadcrumb.isBlank()) && ldData.navBreadcrumb() != null) {
            navBreadcrumb = ldData.navBreadcrumb();
        }
        List<String> sectionPath =
                profile.preserveHeadings() ? SectionPathExtractor.extract(html) : List.of();
        String language = inferLanguage(html, page.canonicalUrl(), profile);

        ExtractionStats stats = extractBody(root, profile);
        String body = boilerplateStripper.stripFromBody(stats.joinedText(), profile);
        PageDisplayMetadataExtractor.DisplayMetadata display =
                displayMetadataExtractor.withJsonLdDescriptionFallback(
                        displayMetadataExtractor.extract(html, title, sectionPath),
                        ldData.description(),
                        title,
                        sectionPath);

        return new CleanedDocument(
                title,
                body,
                language,
                sectionPath,
                display.metaDescription(),
                display.leadText(),
                display.displayDescription(),
                stats.totalBlocks(),
                stats.boilerplateBlocks(),
                page.canonicalUrl(),
                mapSchemaTypeToPageKind(ldData.schemaType()),
                navBreadcrumb,
                parsePublishedAt(ldData.publishedAt()));
    }

    private static String mapSchemaTypeToPageKind(String schemaType) {
        if (schemaType == null || schemaType.isBlank()) {
            return null;
        }
        return switch (schemaType) {
            case "Article", "NewsArticle" -> "news";
            case "Dataset" -> "dataset";
            case "FAQPage" -> "faq";
            default -> null;
        };
    }

    private static Instant parsePublishedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Element selectRoot(Document clone, List<String> rootSelectors) {
        for (String selector : rootSelectors) {
            if (selector == null || selector.isBlank()) {
                continue;
            }
            Element match = clone.selectFirst(selector);
            if (match != null) {
                return match;
            }
        }
        return clone.body();
    }

    private ExtractionStats extractBody(Element root, ParseProfile profile) {
        if (root == null) {
            return new ExtractionStats(List.of(), 0, 0);
        }

        List<String> blocks = new ArrayList<>();
        int total = 0;
        int boilerplate = 0;
        Elements candidates = new Elements();
        candidates.addAll(root.select("h1, h2, h3, h4, h5, h6, p, li"));
        for (String s : profile.addSelectors()) {
            if (s != null && !s.isBlank()) {
                candidates.addAll(root.select(s));
            }
        }
        if (profile.extractTables()) {
            candidates.addAll(root.select("table"));
        }

        Set<Element> covered = buildCoveredSet(candidates, root);

        if (candidates.isEmpty()) {
            String fallback = root.text().trim().replaceAll("\\s+", " ");
            if (fallback.isBlank()) {
                return new ExtractionStats(List.of(), 0, 0);
            }
            boolean bp = boilerplateStripper.isBoilerplateParagraph(fallback, profile);
            return new ExtractionStats(bp ? List.of() : List.of(fallback), 1, bp ? 1 : 0);
        }

        for (Element block : candidates) {
            if (covered.contains(block)) {
                continue;
            }

            String raw = block.text().trim();
            String text = TextSanitizer.sanitizeBlock(raw);
            text = text.replaceAll("\\s+", " ");

            if (text.isBlank()) {
                continue;
            }
            total++;
            if (boilerplateStripper.isBoilerplateParagraph(text, profile)) {
                boilerplate++;
                continue;
            }
            blocks.add(text);
        }
        return new ExtractionStats(List.copyOf(blocks), total, boilerplate);
    }

    private static Set<Element> buildCoveredSet(Elements candidates, Element root) {
        Set<Element> covered = new HashSet<>();
        for (Element el : candidates) {
            Element parent = el.parent();
            while (parent != null && parent != root) {
                if (candidates.contains(parent)) {
                    covered.add(el);
                    break;
                }
                parent = parent.parent();
            }
        }
        return covered;
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

    private static String inferLanguage(Document html, String url, ParseProfile profile) {
        for (String source : profile.languageInferFrom()) {
            if ("htmlLang".equalsIgnoreCase(source)) {
                String lang = html.select("html").attr("lang");
                if (!lang.isBlank()) {
                    return lang.split("-")[0].toLowerCase();
                }
            } else if ("urlSegment".equalsIgnoreCase(source)) {
                String fromUrl = UrlLocaleInferer.infer(url, null);
                if (fromUrl != null && !fromUrl.isBlank()) {
                    return fromUrl;
                }
            } else if ("metaContentLanguage".equalsIgnoreCase(source)) {
                String meta = html.select("meta[http-equiv=content-language]").attr("content");
                if (!meta.isBlank()) {
                    return meta.split("-")[0].toLowerCase();
                }
            }
        }
        return profile.languageDefaultFallback();
    }

    private record ExtractionStats(List<String> blocks, int totalBlocks, int boilerplateBlocks) {

        /** Joins blocks preserving paragraph separation (double newline). */
        String joinedText() {
            return String.join("\n\n", blocks);
        }
    }
}
