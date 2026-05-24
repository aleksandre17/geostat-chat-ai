package com.geostat.chat.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.catalog.CatalogUrls;
import org.junit.jupiter.api.Test;

class ChatLanguageDetectorTest {

    private final ChatLanguageDetector detector = new ChatLanguageDetector();

    @Test
    void usesUiHintWhenQueryIsAmbiguous() {
        assertThat(detector.resolveLocale("hello", "en")).isEqualTo("en");
        assertThat(detector.resolveLocale("", "en")).isEqualTo("en");
    }

    @Test
    void detectsGeorgianFromScript() {
        assertThat(detector.resolveLocale("სტატისტიკა", null)).isEqualTo("ka");
    }

    @Test
    void localeUrlRewritesKaToEn() {
        assertThat(CatalogUrls.localeUrl("https://www.geostat.ge/ka/modules/categories/41", false))
                .isEqualTo("https://www.geostat.ge/en/modules/categories/41");
    }
}
