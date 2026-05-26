package com.geostat.ingestion.parse.profile;

import com.geostat.ingestion.crawl.policy.CorpusPolicy;
import com.geostat.ingestion.parse.SectionPathExtractor;
import com.geostat.ingestion.parse.UrlLocaleInferer;
import com.geostat.ingestion.parse.PageDisplayMetadataExtractor;
import com.geostat.platform.parse.BoilerplateStripper;
import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.ContentExtractor;
import com.geostat.platform.parse.HtmlPageInput;
import com.geostat.platform.parse.ParseProfile;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class JsoupContentExtractor implements ContentExtractor {

    private final BoilerplateStripper boilerplateStripper;
    private final PageDisplayMetadataExtractor displayMetadataExtractor;

    public JsoupContentExtractor(
            BoilerplateStripper boilerplateStripper, PageDisplayMetadataExtractor displayMetadataExtractor) {
        this.boilerplateStripper = boilerplateStripper;
        this.displayMetadataExtractor = displayMetadataExtractor;
    }

    @Override
    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
        Document html = Jsoup.parse(page.html(), page.canonicalUrl());
        Document clone = html.clone();
        for (String selector : profile.removeSelectors()) {
            if (selector != null && !selector.isBlank()) {
                clone.select(selector).remove();
            }
        }

        Element root = selectRoot(clone, profile.rootSelectors());
        String title = resolveTitle(html, root);
        List<String> sectionPath = profile.preserveHeadings() ? SectionPathExtractor.extract(html) : List.of();
        String language = inferLanguage(html, page.canonicalUrl(), profile);

        ExtractionStats stats = extractBody(root, profile);
        String body = boilerplateStripper.stripFromBody(stats.joinedText(), profile);
        PageDisplayMetadataExtractor.DisplayMetadata display =
                displayMetadataExtractor.extract(html, title, sectionPath);

        return new CleanedDocument(
                title,
                body,
                language,
                sectionPath,
                display.metaDescription(),
                display.leadText(),
                display.displayDescription(),
                stats.totalBlocks(),
                stats.boilerplateBlocks());
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
            return new ExtractionStats("", 0, 0);
        }
        List<String> blocks = new ArrayList<>();
        int total = 0;
        int boilerplate = 0;
        Elements candidates = root.select("h1, h2, h3, h4, h5, h6, p, li");
        if (profile.extractTables()) {
            candidates.addAll(root.select("table"));
        }
        if (candidates.isEmpty()) {
            String fallback = root.text().trim().replaceAll("\\s+", " ");
            total = fallback.isBlank() ? 0 : 1;
            if (!fallback.isBlank() && boilerplateStripper.isBoilerplateParagraph(fallback, profile)) {
                boilerplate = 1;
            }
            return new ExtractionStats(fallback, total, boilerplate);
        }
        for (Element block : candidates) {
            String text = block.tagName().equals("table")
                    ? block.text().trim().replaceAll("\\s+", " ")
                    : block.text().trim().replaceAll("\\s+", " ");
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
        return new ExtractionStats(String.join(" ", blocks), total, boilerplate);
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
        return null;
    }

    private record ExtractionStats(String joinedText, int totalBlocks, int boilerplateBlocks) {}
}
