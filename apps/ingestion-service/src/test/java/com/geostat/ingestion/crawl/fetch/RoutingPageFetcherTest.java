package com.geostat.ingestion.crawl.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.platform.crawl.FetchOptions;
import com.geostat.platform.crawl.PageFetchException;
import com.geostat.platform.crawl.RenderMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingPageFetcherTest {

    @Mock
    private Crawler4jStaticPageFetcher staticFetcher;

    @Mock
    private HeadlessBrowserPageFetcher headlessFetcher;

    @Test
    void staticRoute_delegatesToStaticFetcher() throws PageFetchException {
        RoutingPageFetcher router = new RoutingPageFetcher(
                Optional.of(staticFetcher), Optional.empty());
        String url = "https://www.geostat.ge/ka";
        FetchOptions options = FetchOptions.defaults();
        com.geostat.platform.crawl.FetchedPage expected =
                new com.geostat.platform.crawl.FetchedPage(url, null, 200, "text/html", RenderMode.STATIC);

        when(staticFetcher.fetch(eq(url), same(options))).thenReturn(expected);

        com.geostat.platform.crawl.FetchedPage result = router.fetch(url, options);

        verify(staticFetcher).fetch(url, options);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void headlessRoute_delegatesToHeadlessFetcher() throws PageFetchException {
        RoutingPageFetcher router = new RoutingPageFetcher(
                Optional.empty(), Optional.of(headlessFetcher));
        String url = "https://agriculture.geostat.ge/";
        FetchOptions options = new FetchOptions(RenderMode.HEADLESS, 10_000, "GeostatBot/1.0", null);
        com.geostat.platform.crawl.FetchedPage expected =
                new com.geostat.platform.crawl.FetchedPage(url, null, 200, "text/html", RenderMode.HEADLESS);

        com.geostat.platform.crawl.FetchedPage result = router.fetch(url, options);

        verify(headlessFetcher).fetch(url, options);
        assertThat(result).isSameAs(expected);
    }

    @Test
    void missingStaticFetcher_throwsPageFetchException() {
        RoutingPageFetcher router = new RoutingPageFetcher(Optional.empty(), Optional.empty());
        String url = "https://www.geostat.ge/ka";

        assertThatThrownBy(() -> router.fetch(url, FetchOptions.defaults()))
                .isInstanceOf(PageFetchException.class)
                .hasMessageContaining("static fetcher not configured");
    }

    @Test
    void missingHeadlessFetcher_throwsPageFetchException() {
        RoutingPageFetcher router = new RoutingPageFetcher(Optional.empty(), Optional.empty());
        String url = "https://agriculture.geostat.ge/";
        FetchOptions options = new FetchOptions(RenderMode.HEADLESS, 10_000, "GeostatBot/1.0", null);

        assertThatThrownBy(() -> router.fetch(url, options))
                .isInstanceOf(PageFetchException.class)
                .hasMessageContaining("headless fetcher not configured");
    }

    @Test
    void apiMode_throwsPageFetchException() {
        RoutingPageFetcher router = new RoutingPageFetcher(
                Optional.of(staticFetcher), Optional.of(headlessFetcher));
        String url = "https://example.com/data";
        FetchOptions options = new FetchOptions(RenderMode.API, 10_000, "GeostatBot/1.0", null);

        assertThatThrownBy(() -> router.fetch(url, options))
                .isInstanceOf(PageFetchException.class)
                .hasMessageContaining("API mode not yet implemented");
    }
}
