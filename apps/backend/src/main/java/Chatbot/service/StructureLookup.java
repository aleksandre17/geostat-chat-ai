package Chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BFS crawler for the GeoStat organisational structure (up to 3 levels deep).
 *
 * For each page visited, extracts:
 *   - Visible plain text (headings, paragraphs — where person names appear)
 *   - All geostat.ge links (for BFS continuation and AI reference)
 *
 * Output is injected into the AI clarification prompt so the AI can resolve
 * person-name and department queries without web access of its own.
 *
 * Cache TTL: 1 hour per language.
 */
@Component
public class StructureLookup {

    private static final Logger log = LoggerFactory.getLogger(StructureLookup.class);

    private static final String   BASE           = "https://www.geostat.ge";
    private static final String   URL_KA         = BASE + "/ka/structure";
    private static final String   URL_EN         = BASE + "/en/structure";
    private static final long     TTL_MS         = 60 * 60_000L;  // 1 hour
    private static final int      MAX_DEPTH      = 5;
    private static final int      MAX_PAGES      = 80;
    private static final int      MAX_CHARS      = 200_000;
    private static final int      TEXT_PER_PAGE  = 3_000;         // plain-text chars kept per page
    private static final Duration TIMEOUT        = Duration.ofSeconds(5);

    // Matches <a href="URL">text</a>; text 2–120 chars, no nested tags
    private static final Pattern LINK_RE = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>\\s*([^<]{2,120})\\s*</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // Matches archive-item-container links where the label is inside a nested <span>
    private static final Pattern ARCHIVE_LINK_RE = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(?:(?!</a>).)*?<span>\\s*([^<]{2,120}?)\\s*</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final WebClient webClient;

    private volatile String cachedKa;
    private volatile long   cachedKaAt;
    private volatile String cachedEn;
    private volatile long   cachedEnAt;

    public StructureLookup(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    /**
     * Returns crawled structure content for the requested language.
     * First call per language triggers the BFS crawl; subsequent calls within TTL are instant.
     */
    public String get(boolean isGeorgian) {
        if (isGeorgian) {
            if (cachedKa == null || stale(cachedKaAt)) {
                cachedKa  = crawl(URL_KA);
                cachedKaAt = now();
            }
            return cachedKa;
        } else {
            if (cachedEn == null || stale(cachedEnAt)) {
                cachedEn  = crawl(URL_EN);
                cachedEnAt = now();
            }
            return cachedEn;
        }
    }

    // ─── BFS crawler ─────────────────────────────────────────────────────────

    private String crawl(String rootUrl) {
        StringBuilder result   = new StringBuilder();
        Set<String>   visited  = new LinkedHashSet<>();
        Set<String>   seenUrls = new LinkedHashSet<>();

        ArrayDeque<String[]> queue = new ArrayDeque<>();
        visited.add(rootUrl);
        queue.add(new String[]{rootUrl, "0"});

        while (!queue.isEmpty()
                && visited.size() <= MAX_PAGES
                && result.length() < MAX_CHARS) {

            String[] entry = queue.poll();
            String   url   = entry[0];
            int      depth = Integer.parseInt(entry[1]);

            log.debug("Structure crawl [d={}] {}", depth, url);
            String html = fetchHtml(url);
            if (html.isEmpty()) continue;

            collectPage(url, html, result, seenUrls);

            // Bureau pages (/272-/282) are leaf nodes — collect content but follow no sub-links
            boolean isBureauLeaf = isBureauPage(url);
            if (depth < MAX_DEPTH && !isBureauLeaf) {
                for (String sub : extractGeostatLinks(html)) {
                    if (!visited.contains(sub) && visited.size() < MAX_PAGES) {
                        visited.add(sub);
                        queue.add(new String[]{sub, String.valueOf(depth + 1)});
                    }
                }
            }
        }

        log.info("Structure crawl done: {} pages, {} chars", visited.size(), result.length());
        return result.toString().strip();
    }

    // ─── Per-page content extraction ─────────────────────────────────────────

    /**
     * Appends a page section:
     *   === URL ===
     *   [plain-text content — where names live]
     *   [geostat.ge links]
     */
    private void collectPage(String pageUrl, String html,
                              StringBuilder result, Set<String> seenUrls) {
        if (result.length() >= MAX_CHARS) return;

        result.append("\n=== ").append(pageUrl).append(" ===\n");

        // Plain text — strip tags, normalise whitespace, cap per page
        String text = extractText(html);
        if (!text.isBlank()) {
            String snippet = text.length() > TEXT_PER_PAGE
                    ? text.substring(0, TEXT_PER_PAGE) + "…"
                    : text;
            result.append(snippet).append("\n");
        }

        // Links from this page (deduplicated across all pages)
        appendLinks(html, LINK_RE,         result, seenUrls);
        appendLinks(html, ARCHIVE_LINK_RE, result, seenUrls);
    }

    private void appendLinks(String html, Pattern pattern,
                              StringBuilder result, Set<String> seenUrls) {
        Matcher m = pattern.matcher(html);
        while (m.find() && result.length() < MAX_CHARS) {
            String href  = resolveUrl(m.group(1).strip());
            String label = m.group(2).strip().replaceAll("\\s+", " ");
            if (label.isBlank() || href == null) continue;
            if (!href.contains("geostat.ge")) continue;
            if (seenUrls.add(href)) {
                result.append("  ").append(label).append(" → ").append(href).append("\n");
            }
        }
    }

    /**
     * Extracts visible text from the page.
     * Tries to isolate the main content area (skipping nav/header/footer)
     * so the TEXT_PER_PAGE budget covers meaningful content, not navigation boilerplate.
     * Falls back to the full page if no content container is found.
     */
    private String extractText(String html) {
        String source = mainContentArea(html);
        if (source.isBlank()) source = html;
        return source
                .replaceAll("(?si)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?si)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?si)<nav[^>]*>.*?</nav>", " ")
                .replaceAll("(?si)<header[^>]*>.*?</header>", " ")
                .replaceAll("(?si)<footer[^>]*>.*?</footer>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&[a-zA-Z0-9#]+;", "")
                .replaceAll("\\s{2,}", " ")
                .strip();
    }

    /**
     * Tries common content container patterns in order of preference.
     * Returns the inner HTML of the first match, or blank string if none found.
     */
    private static final Pattern[] CONTENT_PATTERNS = {
            Pattern.compile("(?si)<div[^>]+class=[\"'][^\"']*value-databases-table[^\"']*[\"'][^>]*>(.*?)</div>"),
            Pattern.compile("(?si)<div[^>]+class=[\"'][^\"']*history-text[^\"']*[\"'][^>]*>(.*?)</div>"),
            Pattern.compile("(?si)<main[^>]*>(.*?)</main>"),
            Pattern.compile("(?si)<article[^>]*>(.*?)</article>"),
            Pattern.compile("(?si)<div[^>]+id=[\"']content[\"'][^>]*>(.*?)</div>"),
            Pattern.compile("(?si)<div[^>]+class=[\"'][^\"']*\\bcontent\\b[^\"']*[\"'][^>]*>(.*?)</div>"),
            Pattern.compile("(?si)<div[^>]+class=[\"'][^\"']*\\bmain\\b[^\"']*[\"'][^>]*>(.*?)</div>"),
    };

    private String mainContentArea(String html) {
        for (Pattern p : CONTENT_PATTERNS) {
            Matcher m = p.matcher(html);
            if (m.find()) return m.group(1);
        }
        return "";
    }

    /**
     * Returns geostat.ge hrefs that are worth following in BFS.
     * Only /structure and /page/ paths — skips statistics, news, modules, etc.
     * This prevents navigation-menu links from exhausting the page budget.
     */
    // Exact category IDs allowed to be followed: /305 (entry) + bureau pages 272-282
    private static final Set<String> ALLOWED_CATEGORY_IDS = Set.of(
            "272", "273", "274", "275", "276", "277", "278", "279", "280", "281", "282", "305");

    private List<String> extractGeostatLinks(String html) {
        Set<String> links = new LinkedHashSet<>();
        for (Pattern p : List.of(LINK_RE, ARCHIVE_LINK_RE)) {
            Matcher m = p.matcher(html);
            while (m.find()) {
                String href = resolveUrl(m.group(1).strip());
                if (isFollowable(href)) links.add(href);
            }
        }
        return new ArrayList<>(links);
    }

    /** Returns true if a URL is a bureau/territorial office page (leaf — no sub-links). */
    private boolean isBureauPage(String url) {
        return ALLOWED_CATEGORY_IDS.stream()
                .filter(id -> !id.equals("305"))
                .anyMatch(id -> url.contains("/ka/modules/categories/" + id));
    }

    private static final java.util.regex.Pattern NON_HTML_EXT = java.util.regex.Pattern.compile(
            "(?i)\\.(png|jpe?g|gif|svg|webp|ico|pdf|zip|xls[xm]?|doc[xm]?|csv)([?#].*)?$");

    private boolean isFollowable(String url) {
        if (url == null) return false;
        if (!url.startsWith("https://www.geostat.ge") && !url.startsWith("http://www.geostat.ge"))
            return false;
        if (NON_HTML_EXT.matcher(url).find()) return false;
        // Structure pages and person/department pages
        if (url.contains("/structure") || url.contains("/ka/page/") || url.contains("/en/page/"))
            return true;
        // Territorial: only exact known category IDs (272-282 bureaus + 305 entry)
        return ALLOWED_CATEGORY_IDS.stream()
                .anyMatch(id -> url.contains("/ka/modules/categories/" + id));
    }

    // ─── HTTP & URL helpers ───────────────────────────────────────────────────

    // Detects soft redirects returned as body text (HTTP 200 with "Redirecting to URL")
    private static final Pattern SOFT_REDIRECT_RE = Pattern.compile(
            "Redirecting to (https?://[^\\s<\"']+)", Pattern.CASE_INSENSITIVE);

    private String fetchHtml(String url) {
        return fetchHtml(url, 3);
    }

    private String fetchHtml(String url, int redirectsLeft) {
        try {
            Thread.sleep(200); // avoid rate-limiting
            String html = webClient.get()
                    .uri(url)
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ka,en-US;q=0.9,en;q=0.8")
                    .header("Referer", BASE)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            if (html == null) return "";

            // Follow soft redirects (body says "Redirecting to URL" with HTTP 200)
            if (redirectsLeft > 0) {
                Matcher m = SOFT_REDIRECT_RE.matcher(html);
                if (m.find()) {
                    String target = m.group(1).strip();
                    log.debug("Soft redirect {} → {}", url, target);
                    return fetchHtml(target, redirectsLeft - 1);
                }
            }
            return html;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            log.warn("Fetch failed ({}): {}", url, e.getMessage());
            return "";
        }
    }

    private String resolveUrl(String href) {
        if (href.startsWith("/"))    return BASE + href;
        if (href.startsWith("http")) return href;
        return null;
    }

    /** Invalidates both language caches. Next call to get() triggers a fresh crawl. */
    public void clearCache() {
        cachedKa   = null;
        cachedKaAt = 0;
        cachedEn   = null;
        cachedEnAt = 0;
        log.info("Structure cache cleared");
    }

    private boolean stale(long t) { return now() - t > TTL_MS; }
    private static long now()     { return System.currentTimeMillis(); }
}