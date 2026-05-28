package com.geostat.ingestion.parse.strategy;

import com.geostat.ingestion.parse.SectionPathExtractor;
import com.geostat.platform.parse.CleanedDocument;
import com.geostat.platform.parse.ExtractionStrategy;
import com.geostat.platform.parse.HtmlPageInput;
import com.geostat.platform.parse.ParseProfile;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** News-specific extraction: publishedAt from structured metadata (ARCH-02). */
@Component
public class GeostatNewsExtractionStrategy implements ExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(GeostatNewsExtractionStrategy.class);

    private static final Pattern JSON_LD_DATE_PUBLISHED =
            Pattern.compile("\"datePublished\"\\s*:\\s*\"([^\"]+)\"");

    @Override
    public String strategyId() {
        return "geostat-news";
    }

    @Override
    public boolean supports(String corpusName, String pageKind) {
        return "geostat-portal".equals(corpusName) && "news".equals(pageKind);
    }

    @Override
    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile) {
        Document html = Jsoup.parse(page.html(), page.canonicalUrl());

        html.select("script, style, nav, footer, header, aside, "
                        + ".related-articles, .news-attachments, "
                        + ".social-share, .breadcrumb, .rightbar-wrapper")
                .remove();

        Element article = html.selectFirst("article, .news-content, main");
        String title = (article != null && article.selectFirst("h1") != null)
                ? article.selectFirst("h1").text().strip()
                : html.title().strip();

        String leadText = extractNewsLead(article);

        String body = article != null
                ? article.select("p, li, h2, h3, h4").stream()
                        .map(e -> e.text().strip())
                        .filter(t -> t.length() > 40)
                        .filter(t -> !isNewsBoilerplate(t))
                        .collect(Collectors.joining(" "))
                : "";

        String language = html.select("html").attr("lang");
        language = language.isBlank() ? null : language.split("-")[0].toLowerCase();

        Instant publishedAt = extractPublishedAt(html);
        String navBreadcrumb = extractNavBreadcrumb(html);

        return new CleanedDocument(
                title,
                body,
                language,
                List.of(),
                null,
                leadText,
                null,
                0,
                0,
                page.canonicalUrl(),
                "news",
                navBreadcrumb,
                publishedAt);
    }

    public boolean isNewsUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/news/") || lower.contains("/newsroom/");
    }

    public CleanedDocument enrichPublishedAt(CleanedDocument doc, Document html) {
        if (doc.publishedAt() != null) {
            return doc;
        }
        Instant publishedAt = extractPublishedAt(html);
        if (publishedAt == null) {
            return doc;
        }
        return doc.withPublishedAt(publishedAt);
    }

    public CleanedDocument extract(HtmlPageInput page, ParseProfile profile, CleanedDocument base) {
        Document html = Jsoup.parse(page.html(), page.canonicalUrl());
        return enrichPublishedAt(base, html);
    }

    Instant extractPublishedAt(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            Matcher matcher = JSON_LD_DATE_PUBLISHED.matcher(script.html());
            if (matcher.find()) {
                Instant parsed = parseInstant(matcher.group(1));
                if (parsed != null) {
                    return parsed;
                }
            }
        }

        Element time = doc.selectFirst("time[datetime], .news-date[datetime]");
        if (time != null && !time.attr("datetime").isBlank()) {
            Instant parsed = parseInstant(time.attr("datetime"));
            if (parsed != null) {
                return parsed;
            }
        }

        String og = doc.select("meta[property=article:published_time]").attr("content");
        if (!og.isBlank()) {
            Instant parsed = parseInstant(og);
            if (parsed != null) {
                return parsed;
            }
        }

        Element dateEl = doc.selectFirst(".news-items-section .news-date, .article-date, time");
        if (dateEl != null) {
            String text = dateEl.text().strip();
            log.debug("[date-extract] unstructured date text found: {}", text);
        }
        return null;
    }

    private String extractNewsLead(Element article) {
        if (article == null) {
            return null;
        }
        for (Element p : article.select("p")) {
            String text = p.text().strip();
            if (text.length() >= 60 && looksLikeProse(text)) {
                return text;
            }
        }
        return null;
    }

    private boolean looksLikeProse(String text) {
        long letterCount = text.chars().filter(Character::isLetter).count();
        return letterCount > text.length() * 0.5;
    }

    private boolean isNewsBoilerplate(String text) {
        return text.startsWith("თარიღი:")
                || text.startsWith("Date:")
                || text.startsWith("გადმოწერა")
                || text.startsWith("Download")
                || text.startsWith("PDF")
                || text.startsWith("CSV");
    }

    private String extractNavBreadcrumb(Document html) {
        return SectionPathExtractor.extractNavBreadcrumb(html);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (Exception ignored) {
            return null;
        }
    }
}
