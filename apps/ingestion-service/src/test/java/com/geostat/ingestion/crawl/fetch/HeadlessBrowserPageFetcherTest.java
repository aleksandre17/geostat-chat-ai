package com.geostat.ingestion.crawl.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.platform.crawl.FetchOptions;
import com.geostat.platform.crawl.NetworkPolicy;
import com.geostat.platform.crawl.PageFetchException;
import com.geostat.platform.crawl.RenderMode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HeadlessBrowserPageFetcherTest {

    @Mock
    private PlaywrightPageFetcher playwrightFetcher;

    @InjectMocks
    private HeadlessBrowserPageFetcher fetcher;

    @Test
    void delegatesToPlaywrightFetcher() throws PageFetchException {
        String url = "https://agriculture.geostat.ge/";
        FetchOptions options = FetchOptions.defaults();
        Document doc = Jsoup.parse("<html><body><p>Test</p></body></html>", url);
        FetchedPage internal = new FetchedPage(url, 200, doc);

        when(playwrightFetcher.fetchPage(eq(url), same(options))).thenReturn(internal);

        com.geostat.platform.crawl.FetchedPage result = fetcher.fetch(url, options);

        verify(playwrightFetcher).fetchPage(url, options);
        assertThat(result.url()).isEqualTo(url);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.contentType()).isEqualTo("text/html");
        assertThat(result.renderMode()).isEqualTo(RenderMode.HEADLESS);
        assertThat(result.html()).contains("<p>Test</p>");
    }

    @Test
    void adaptsFinalUrlFromRedirect() throws PageFetchException {
        String requestedUrl = "https://agriculture.geostat.ge/";
        String finalUrl     = "https://agriculture.geostat.ge/ka/";
        FetchOptions options = FetchOptions.defaults();
        Document doc = Jsoup.parse("<html><body>Redirected</body></html>", finalUrl);
        FetchedPage internal = new FetchedPage(requestedUrl, finalUrl, 200, doc, null, null, null, null, false);

        when(playwrightFetcher.fetchPage(eq(requestedUrl), same(options))).thenReturn(internal);

        com.geostat.platform.crawl.FetchedPage result = fetcher.fetch(requestedUrl, options);

        // canonicalUrl() returns finalUrl when a redirect occurred
        assertThat(result.url()).isEqualTo(finalUrl);
    }

    @Test
    void propagatesPageFetchException() throws PageFetchException {
        String url = "https://agriculture.geostat.ge/broken";
        FetchOptions options = FetchOptions.defaults();

        when(playwrightFetcher.fetchPage(eq(url), same(options)))
                .thenThrow(new PageFetchException(url, "navigation timeout", null));

        assertThatThrownBy(() -> fetcher.fetch(url, options))
                .isInstanceOf(PageFetchException.class)
                .hasMessageContaining("navigation timeout");
    }

    @Test
    void passesFetchOptionsUnmodifiedToPlaywright() throws PageFetchException {
        String url = "https://agriculture.geostat.ge/";
        FetchOptions options = new FetchOptions(
                RenderMode.HEADLESS, 15_000, "TestAgent/1.0",
                new NetworkPolicy(true, true, null,
                        java.util.Map.of("agriculture.geostat.ge", "10.0.0.5"), null));
        Document doc = Jsoup.parse("<html></html>", url);
        FetchedPage internal = new FetchedPage(url, 200, doc);

        when(playwrightFetcher.fetchPage(eq(url), same(options))).thenReturn(internal);

        fetcher.fetch(url, options);

        // FetchOptions (including dnsOverrides) must be forwarded as-is — no mutation
        verify(playwrightFetcher).fetchPage(url, options);
    }
}
