package com.geostat.ingestion.crawl.fetch;

import com.geostat.platform.crawl.FetchOptions;
import com.geostat.platform.crawl.NetworkPolicy;
import com.geostat.platform.crawl.PageFetchException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Playwright-backed headless browser fetcher.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #fetchPage(String, FetchOptions)} — full-options path used by
 *       {@link HeadlessBrowserPageFetcher} during corpus crawl; honours timeout and DNS overrides.
 *   <li>{@link #fetch(String)} — lightweight path kept for the P3-03b audit re-fetch pipeline
 *       ({@code PlaywrightRefetchService}); delegates to {@code fetchPage} with default options.
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "geostat.ingestion.playwright", name = "enabled", havingValue = "true")
public class PlaywrightPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightPageFetcher.class);
    private static final String DEFAULT_USER_AGENT = "GeostatBot/1.0";

    private final Playwright playwright;
    private final Browser browser;
    private final int defaultTimeoutMs;

    public PlaywrightPageFetcher(com.geostat.ingestion.config.IngestionProperties properties) {
        this.defaultTimeoutMs = properties.playwright().timeoutMs();
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        log.info("Playwright fetcher initialised (timeout={}ms)", defaultTimeoutMs);
    }

    /**
     * Full-options fetch: honours per-request timeout, user-agent, and DNS overrides.
     *
     * <p>DNS overrides are applied via Playwright route interception — the TCP connection is made
     * to the override IP while the original {@code Host} header is preserved, allowing virtual-domain
     * corpora (e.g. {@code agriculture.geostat.ge}) to be crawled without modifying system DNS.
     */
    public FetchedPage fetchPage(String url, FetchOptions options) throws PageFetchException {
        String agent = (options.userAgent() != null && !options.userAgent().isBlank())
                ? options.userAgent() : DEFAULT_USER_AGENT;
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setUserAgent(agent));
        try {
            applyDnsOverrides(context, options.network());
            Page page = context.newPage();
            try {
                com.microsoft.playwright.Response response = page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(options.timeoutMs())
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));
                String finalUrl = page.url();
                int status = response != null ? response.status() : 200;
                Document document = Jsoup.parse(page.content(), finalUrl);
                return new FetchedPage(url, finalUrl, status, document, null, null, null, null, false);
            } finally {
                page.close();
            }
        } catch (com.microsoft.playwright.PlaywrightException e) {
            throw new PageFetchException(url, "Playwright navigation failed: " + e.getMessage(), e);
        } finally {
            context.close();
        }
    }

    /**
     * Convenience overload for the P3-03b audit re-fetch path.
     * Uses the globally configured timeout and no DNS overrides.
     */
    public FetchedPage fetch(String url) throws PageFetchException {
        return fetchPage(url, new FetchOptions(
                com.geostat.platform.crawl.RenderMode.HEADLESS,
                defaultTimeoutMs,
                DEFAULT_USER_AGENT,
                NetworkPolicy.defaults()));
    }

    /**
     * Registers Playwright route intercepts that rewrite the TCP-level host to a target IP
     * while injecting the original {@code host} header so the server receives the correct
     * virtual hostname. No-op when the policy has no DNS overrides configured.
     */
    private static void applyDnsOverrides(BrowserContext context, NetworkPolicy network) {
        if (!network.hasDnsOverrides()) {
            return;
        }
        network.dnsOverrides().forEach((hostname, targetIp) ->
                context.route("**://" + hostname + "/**", route -> {
                    Map<String, String> headers = new HashMap<>(route.request().headers());
                    headers.put("host", hostname);
                    String rewritten = route.request().url()
                            .replace("://" + hostname, "://" + targetIp);
                    route.resume(new Route.ResumeOptions()
                            .setUrl(rewritten)
                            .setHeaders(headers));
                })
        );
    }

    @PreDestroy
    void shutdown() {
        try { browser.close();    } catch (Exception ignored) { /* shutting down */ }
        try { playwright.close(); } catch (Exception ignored) { /* shutting down */ }
    }
}
