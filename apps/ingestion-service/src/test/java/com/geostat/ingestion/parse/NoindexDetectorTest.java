package com.geostat.ingestion.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class NoindexDetectorTest {

    @Test
    void isNoindex_true_forRobotsNoindex() {
        String html = "<html><head>"
                + "<meta name='robots' content='noindex,nofollow'>"
                + "</head><body><p>text</p></body></html>";
        assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isTrue();
    }

    @Test
    void isNoindex_true_caseInsensitive() {
        String html = "<html><head>"
                + "<meta name='ROBOTS' content='NOINDEX'>"
                + "</head><body></body></html>";
        assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isTrue();
    }

    @Test
    void isNoindex_false_whenNoRobotsMeta() {
        String html = "<html><body><p>normal page</p></body></html>";
        assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isFalse();
    }

    @Test
    void isNoindex_false_whenRobotsIndex() {
        String html = "<html><head>"
                + "<meta name='robots' content='index,follow'>"
                + "</head><body></body></html>";
        assertThat(NoindexDetector.isNoindex(Jsoup.parse(html))).isFalse();
    }
}
