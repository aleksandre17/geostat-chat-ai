package com.geostat.ingestion.crawl.fetch;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** P3-03b — headless browser fetch for SPA pages (audit trigger only, not default crawl). */
@Component
@ConditionalOnProperty(prefix = "geostat.ingestion.playwright", name = "enabled", havingValue = "true")
public class PlaywrightPageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightPageFetcher.class);

    private final Playwright playwright;
    private final Browser browser;
    private final int timeoutMs;

    public PlaywrightPageFetcher(com.geostat.ingestion.config.IngestionProperties properties) {
        this.timeoutMs = properties.playwright().timeoutMs();
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        log.info("Playwright fetcher enabled (P3-03b audit trigger)");
    }

    public FetchedPage fetch(String url) {
        Page page = browser.newPage();
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(timeoutMs)
                    .setWaitUntil(WaitUntilState.NETWORKIDLE));
            String html = page.content();
            Document document = Jsoup.parse(html, url);
            return new FetchedPage(url, 200, document);
        } finally {
            page.close();
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            browser.close();
        } catch (Exception ignored) {
            // shutting down
        }
        try {
            playwright.close();
        } catch (Exception ignored) {
            // shutting down
        }
    }
}
