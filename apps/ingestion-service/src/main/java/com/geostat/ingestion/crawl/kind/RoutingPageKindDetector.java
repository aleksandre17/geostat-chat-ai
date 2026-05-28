package com.geostat.ingestion.crawl.kind;

import com.geostat.platform.crawl.FetchedPage;
import com.geostat.platform.crawl.PageKind;
import com.geostat.platform.crawl.PageKindDetector;
import com.geostat.platform.parse.ParseProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary {@link PageKindDetector} that routes between URL-pattern and HTML-signal detection.
 *
 * <p>Detection order:
 * <ol>
 *   <li>URL patterns ({@link UrlPatternPageKindDetector}) — fast, no HTML parse, highest precision
 *   <li>HTML signals ({@link HtmlSignalPageKindDetector}) — DOM parse, used only if URL gives UNKNOWN
 * </ol>
 *
 * <p>URL patterns are preferred: site URL structure is publisher-controlled and stable.
 * HTML signals are a fallback for corpus configs with sparse or absent URL rules.
 */
@Primary
@Component
public class RoutingPageKindDetector implements PageKindDetector {

    private static final Logger log = LoggerFactory.getLogger(RoutingPageKindDetector.class);

    private final UrlPatternPageKindDetector urlDetector;
    private final HtmlSignalPageKindDetector htmlDetector;

    public RoutingPageKindDetector(
            UrlPatternPageKindDetector urlDetector,
            HtmlSignalPageKindDetector htmlDetector) {
        this.urlDetector  = urlDetector;
        this.htmlDetector = htmlDetector;
    }

    @Override
    public String detect(FetchedPage page, ParseProfile profile) {
        String kind = urlDetector.detect(page, profile);
        if (!PageKind.UNKNOWN.equals(kind)) {
            log.debug("[page-kind] {} → {} (URL pattern)", page.url(), kind);
            return kind;
        }
        kind = htmlDetector.detect(page, profile);
        log.debug("[page-kind] {} → {} (HTML signal)", page.url(), kind);
        return kind;
    }
}
