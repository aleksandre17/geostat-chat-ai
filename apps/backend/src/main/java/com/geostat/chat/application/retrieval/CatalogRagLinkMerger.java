package com.geostat.chat.application.retrieval;

import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.TopicStyleCatalog;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Merges static catalog {@link LinkCard}s with RAG {@link RetrievedChunk} sources into one ordered list.
 * Catalog links win on URL collision; RAG fills remaining slots (B-15 / P5-03 / R-03).
 * RAG-L11: citation descriptions from ingested {@code pageDescription}, locale-aware.
 */
@Component
public class CatalogRagLinkMerger {

    static final int MAX_TOTAL = 8;
    public static final int MAX_RAG = 4;
    private static final int SNIPPET_MAX = 240;

    public List<LinkCard> merge(List<LinkCard> catalogLinks, List<RetrievedChunk> ragChunks, boolean isGeorgian) {
        return merge(catalogLinks, ragChunks, isGeorgian, MAX_RAG);
    }

    public List<LinkCard> merge(
            List<LinkCard> catalogLinks, List<RetrievedChunk> ragChunks, boolean isGeorgian, int maxRag) {
        Set<String> seen = new LinkedHashSet<>();
        List<LinkCard> merged = new ArrayList<>();

        if (catalogLinks != null) {
            for (LinkCard card : catalogLinks) {
                if (merged.size() >= MAX_TOTAL) {
                    break;
                }
                String key = SourceUrlNormalizer.normalize(card.url());
                if (key.isEmpty() || !seen.add(key)) {
                    continue;
                }
                merged.add(card);
            }
        }

        int ragAdded = 0;
        if (ragChunks != null) {
            for (RetrievedChunk chunk : ragChunks) {
                if (merged.size() >= MAX_TOTAL || ragAdded >= maxRag) {
                    break;
                }
                LinkCard ragCard = toLinkCard(chunk, isGeorgian);
                if (ragCard == null) {
                    continue;
                }
                String key = SourceUrlNormalizer.normalize(ragCard.url());
                if (key.isEmpty() || !seen.add(key)) {
                    continue;
                }
                merged.add(ragCard);
                ragAdded++;
            }
        }

        return List.copyOf(merged);
    }

    private LinkCard toLinkCard(RetrievedChunk chunk, boolean isGeorgian) {
        if (chunk == null || chunk.sourceUrl() == null || chunk.sourceUrl().isBlank()) {
            return null;
        }
        TopicStyleCatalog.LinkTypeStyle style = TopicStyleCatalog.getLinkTypeStyle("source");
        String titleKa = resolveTitleKa(chunk);
        String titleEn = resolveTitleEn(chunk);
        String snippet = resolveDescription(chunk, isGeorgian);
        return LinkCard.fromRag(
                chunk.sourceUrl().strip(),
                titleKa,
                titleEn,
                snippet,
                chunk.score(),
                style.icon(),
                style.bgColor());
    }

    static String resolveDescription(RetrievedChunk chunk, boolean isGeorgian) {
        String userLocale = isGeorgian ? "ka" : "en";
        String title = chunk.pageTitle();

        if (isUsablePageDescription(chunk, userLocale)
                && !duplicatesTitle(chunk.pageDescription(), title)) {
            return trimDescription(chunk.pageDescription());
        }
        String contentExcerpt = contentExcerpt(chunk.text(), title);
        if (contentExcerpt != null) {
            return contentExcerpt;
        }
        String section = chunk.sectionPath();
        if (section != null && !section.isBlank() && !duplicatesTitle(section, title)) {
            return isGeorgian ? "მონაკვეთი: " + section.strip() : "Section: " + section.strip();
        }
        return null;
    }

    static boolean duplicatesTitle(String text, String title) {
        if (text == null || title == null) {
            return false;
        }
        String t = text.strip().toLowerCase(Locale.ROOT);
        String h = title.strip().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || h.isEmpty()) {
            return false;
        }
        if (t.equals(h)) {
            return true;
        }
        int diff = Math.abs(t.length() - h.length());
        if (diff > 24) {
            return false;
        }
        return t.startsWith(h) || h.startsWith(t);
    }

    static String contentExcerpt(String text, String title) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (DisplayBoilerplate.isBoilerplate(normalized)) {
            return null;
        }
        if (!looksLikeProse(normalized)) {
            return null;
        }
        String trimmed = trimLeadingPartialWord(normalized);
        if (duplicatesTitle(trimmed, title)) {
            return null;
        }
        return trimDescription(trimmed);
    }

    static String trimLeadingPartialWord(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int firstSpace = text.indexOf(' ');
        if (firstSpace < 0) {
            return text;
        }
        int rest = text.length() - (firstSpace + 1);
        if (rest < 24) {
            return text;
        }
        return text.substring(firstSpace + 1);
    }

    private static boolean isUsablePageDescription(RetrievedChunk chunk, String userLocale) {
        if (chunk.pageDescription() == null || chunk.pageDescription().isBlank()) {
            return false;
        }
        if (DisplayBoilerplate.isBoilerplate(chunk.pageDescription())) {
            return false;
        }
        if (localeMatches(chunk.language(), userLocale)) {
            return true;
        }
        return chunk.language() == null;
    }

    static boolean localeMatches(String documentLocale, String userLocale) {
        if (documentLocale == null || documentLocale.isBlank()) {
            return true;
        }
        return documentLocale.equalsIgnoreCase(userLocale);
    }

    static String trimDescription(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.strip().replace('\n', ' ');
        if (normalized.length() <= SNIPPET_MAX) {
            return normalized;
        }
        int cut = SNIPPET_MAX - 3;
        int lastSpace = normalized.lastIndexOf(' ', cut);
        if (lastSpace > SNIPPET_MAX / 2) {
            cut = lastSpace;
        }
        return normalized.substring(0, cut).strip() + "...";
    }

    static String smartExcerpt(String text, boolean isGeorgian) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.strip().replace('\n', ' ');
        if (!looksLikeProse(normalized)) {
            return isGeorgian ? "იხილეთ სრული ინფორმაცია გვერდზე." : "See the full information on this page.";
        }
        return trimDescription(normalized);
    }

    static boolean looksLikeProse(String text) {
        if (text == null || text.length() < 20) {
            return false;
        }
        long letters = text.chars().filter(Character::isLetter).count();
        return letters >= 20;
    }

    private static String resolveTitleKa(RetrievedChunk chunk) {
        if (chunk.pageTitle() != null
                && !chunk.pageTitle().isBlank()
                && ("ka".equalsIgnoreCase(chunk.language()) || chunk.language() == null)) {
            return chunk.pageTitle();
        }
        return deriveTitleFromChunk(chunk.text(), chunk.sourceUrl(), true);
    }

    private static String resolveTitleEn(RetrievedChunk chunk) {
        if (chunk.pageTitle() != null
                && !chunk.pageTitle().isBlank()
                && "en".equalsIgnoreCase(chunk.language())) {
            return chunk.pageTitle();
        }
        return deriveTitleFromChunk(chunk.text(), chunk.sourceUrl(), false);
    }

    private static String deriveTitleFromChunk(String text, String sourceUrl, boolean isGeorgian) {
        if (isGeorgian) {
            String normalized = text != null ? text.strip() : "";
            if (!normalized.isEmpty() && looksLikeProse(normalized)) {
                String preview = normalized.length() > 72 ? normalized.substring(0, 69) + "..." : normalized;
                return preview.replace('\n', ' ');
            }
        } else {
            String fromUrl = deriveEnglishTitleFromUrl(sourceUrl);
            if (fromUrl != null) {
                return fromUrl;
            }
        }
        String url = sourceUrl != null ? sourceUrl.strip() : "";
        int slash = url.lastIndexOf('/');
        if (slash >= 0 && slash < url.length() - 1) {
            return humanizeSlug(url.substring(slash + 1));
        }
        return isGeorgian ? "წყარო" : "Source";
    }

    static String deriveEnglishTitleFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String path;
        try {
            path = java.net.URI.create(url.strip()).getPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i].trim();
            if (seg.isEmpty() || seg.equals("ka") || seg.equals("en")) {
                continue;
            }
            if (seg.matches("\\d+")) {
                return "GeoStat page";
            }
            return humanizeSlug(seg);
        }
        return "GeoStat source";
    }

    static String humanizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "Source";
        }
        String[] words = slug.replace('_', '-').split("-");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.isEmpty() ? "Source" : sb.toString();
    }
}
