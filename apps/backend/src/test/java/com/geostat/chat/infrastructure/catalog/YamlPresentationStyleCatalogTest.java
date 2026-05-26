package com.geostat.chat.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.domain.catalog.LinkTypeStyle;
import com.geostat.chat.domain.catalog.PresentationStyle;
import org.junit.jupiter.api.Test;

class YamlPresentationStyleCatalogTest {

    private final YamlPresentationStyleCatalog catalog = YamlPresentationStyleCatalog.fromClasspath();

    @Test
    void loadsPageKindsAndLinkTypesFromClasspathYaml() {
        assertThat(catalog.pageKindStyle("portal").bgColor()).isEqualTo("#8B5CF6");
        assertThat(catalog.pageKindStyle("dataset").icon()).isEqualTo("metadata");
        assertThat(catalog.linkTypeStyle("portal").labelKa()).isEqualTo("პორტალი");
        assertThat(catalog.linkTypeStyle("statistics").labelEn()).isEqualTo("Statistics");
        assertThat(catalog.linkTypeLabel("news", false)).isEqualTo("News");
    }

    @Test
    void cardTypeForPageKindComesFromYaml() {
        assertThat(catalog.cardTypeForPageKind("portal")).isEqualTo("portal");
        assertThat(catalog.cardTypeForPageKind("report")).isEqualTo("statistics");
        assertThat(catalog.cardTypeForPageKind("news")).isEqualTo("news");
        assertThat(catalog.cardTypeForPageKind("faq")).isEqualTo("general");
    }

    @Test
    void unknownPageKindFallsBackToDefaultLinkType() {
        YamlPresentationStyleCatalog.StyleFile file = new YamlPresentationStyleCatalog.StyleFile();
        file.setDefaultLinkType("general");
        file.setPageKinds(
                java.util.Map.of("portal", entry("portal-icon", "#111111", "#222222", null, null, "portal")));
        file.setLinkTypes(java.util.Map.of(
                "general", entry("general-icon", "#333333", "#444444", "ბმული", "Link", null)));

        YamlPresentationStyleCatalog custom = new YamlPresentationStyleCatalog(file);

        assertThat(custom.pageKindStyle("missing").icon()).isEqualTo("general-icon");
        assertThat(custom.cardTypeForPageKind("missing")).isEqualTo("general");
    }

    private static YamlPresentationStyleCatalog.StyleEntry entry(
            String icon, String bg, String light, String ka, String en, String cardType) {
        YamlPresentationStyleCatalog.StyleEntry entry = new YamlPresentationStyleCatalog.StyleEntry();
        entry.setIcon(icon);
        entry.setBgColor(bg);
        entry.setLightBg(light);
        entry.setLabelKa(ka);
        entry.setLabelEn(en);
        entry.setCardType(cardType);
        return entry;
    }
}
