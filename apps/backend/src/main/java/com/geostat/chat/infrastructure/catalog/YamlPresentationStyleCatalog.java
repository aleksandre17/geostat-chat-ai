package com.geostat.chat.infrastructure.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.domain.catalog.LinkTypeStyle;
import com.geostat.chat.domain.catalog.PresentationStyle;
import com.geostat.chat.domain.catalog.PresentationStyleCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** YAML-backed adapter for {@link PresentationStyleCatalog}. */
@Component
public class YamlPresentationStyleCatalog implements PresentationStyleCatalog {

    private static final Logger log = LoggerFactory.getLogger(YamlPresentationStyleCatalog.class);
    static final String RESOURCE = "catalog/topic-style.yaml";

    private static final LinkTypeStyle FALLBACK_LINK =
            new LinkTypeStyle("biznes_registri", "#6B7280", "#F3F4F6", "ბმული", "Link");

    private final Map<String, PageKindEntry> pageKinds;
    private final Map<String, LinkTypeStyle> linkTypes;
    private final String defaultLinkType;

    public YamlPresentationStyleCatalog() {
        this(loadStyleFile(new ClassPathResource(RESOURCE)));
    }

    YamlPresentationStyleCatalog(StyleFile file) {
        defaultLinkType = normalizeDefault(file.defaultLinkType());
        pageKinds = toPageKindMap(file.pageKinds());
        linkTypes = toLinkTypeMap(file.linkTypes());
    }

    public static YamlPresentationStyleCatalog fromClasspath() {
        return new YamlPresentationStyleCatalog(loadStyleFile(new ClassPathResource(RESOURCE)));
    }

    @Override
    public PresentationStyle pageKindStyle(String pageKind) {
        String key = normalizeKey(pageKind, "unknown");
        PageKindEntry entry = pageKinds.get(key);
        if (entry != null) {
            return entry.presentation();
        }
        LinkTypeStyle fallback = resolveLinkType(defaultLinkType);
        return new PresentationStyle(fallback.icon(), fallback.bgColor(), fallback.lightBg());
    }

    @Override
    public LinkTypeStyle linkTypeStyle(String linkType) {
        return resolveLinkType(normalizeKey(linkType, defaultLinkType));
    }

    @Override
    public String linkTypeLabel(String linkType, boolean isGeorgian) {
        LinkTypeStyle style = linkTypeStyle(linkType);
        return isGeorgian ? style.labelKa() : style.labelEn();
    }

    @Override
    public String cardTypeForPageKind(String pageKind) {
        String key = normalizeKey(pageKind, "unknown");
        PageKindEntry entry = pageKinds.get(key);
        if (entry != null && entry.cardType() != null && !entry.cardType().isBlank()) {
            return entry.cardType();
        }
        return defaultLinkType;
    }

    private LinkTypeStyle resolveLinkType(String key) {
        LinkTypeStyle style = linkTypes.get(key);
        if (style != null) {
            return style;
        }
        LinkTypeStyle fallback = linkTypes.get(defaultLinkType);
        return fallback != null ? fallback : FALLBACK_LINK;
    }

    private static StyleFile loadStyleFile(ClassPathResource resource) {
        if (!resource.exists()) {
            log.warn("topic-style catalog missing at {}; using empty defaults", RESOURCE);
            return new StyleFile();
        }
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream input = resource.getInputStream()) {
            StyleFile file = yamlMapper.readValue(input, StyleFile.class);
            log.info(
                    "Loaded topic-style catalog: {} pageKinds, {} linkTypes",
                    file.pageKinds() == null ? 0 : file.pageKinds().size(),
                    file.linkTypes() == null ? 0 : file.linkTypes().size());
            return file;
        } catch (IOException e) {
            log.warn("Failed to load topic-style catalog; using empty defaults: {}", e.getMessage());
            return new StyleFile();
        }
    }

    private static String normalizeDefault(String value) {
        return value == null || value.isBlank() ? "general" : value.strip().toLowerCase();
    }

    private static String normalizeKey(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.strip().toLowerCase();
    }

    static Map<String, PageKindEntry> toPageKindMap(Map<String, StyleEntry> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, PageKindEntry> mapped = new LinkedHashMap<>();
        source.forEach((key, entry) -> {
            if (entry != null) {
                mapped.put(key.toLowerCase(), entry.toPageKindEntry());
            }
        });
        return Map.copyOf(mapped);
    }

    static Map<String, LinkTypeStyle> toLinkTypeMap(Map<String, StyleEntry> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, LinkTypeStyle> mapped = new LinkedHashMap<>();
        source.forEach((key, entry) -> {
            if (entry != null) {
                mapped.put(key.toLowerCase(), entry.toLinkTypeStyle());
            }
        });
        return Map.copyOf(mapped);
    }

    record PageKindEntry(PresentationStyle presentation, String cardType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class StyleFile {
        private String defaultLinkType;
        private Map<String, StyleEntry> pageKinds;
        private Map<String, StyleEntry> linkTypes;

        String defaultLinkType() {
            return defaultLinkType;
        }

        Map<String, StyleEntry> pageKinds() {
            return pageKinds;
        }

        Map<String, StyleEntry> linkTypes() {
            return linkTypes;
        }

        public void setDefaultLinkType(String defaultLinkType) {
            this.defaultLinkType = defaultLinkType;
        }

        public void setPageKinds(Map<String, StyleEntry> pageKinds) {
            this.pageKinds = pageKinds;
        }

        public void setLinkTypes(Map<String, StyleEntry> linkTypes) {
            this.linkTypes = linkTypes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class StyleEntry {
        private String icon;
        private String bgColor;
        private String lightBg;
        private String labelKa;
        private String labelEn;
        private String cardType;

        PageKindEntry toPageKindEntry() {
            return new PageKindEntry(
                    new PresentationStyle(
                            valueOrDefault(icon, "general"),
                            valueOrDefault(bgColor, "#6B7280"),
                            valueOrDefault(lightBg, "#F3F4F6")),
                    cardType == null || cardType.isBlank() ? null : cardType.strip().toLowerCase());
        }

        LinkTypeStyle toLinkTypeStyle() {
            return new LinkTypeStyle(
                    valueOrDefault(icon, "general"),
                    valueOrDefault(bgColor, "#6B7280"),
                    valueOrDefault(lightBg, "#F3F4F6"),
                    valueOrDefault(labelKa, "ბმული"),
                    valueOrDefault(labelEn, "Link"));
        }

        private static String valueOrDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.strip();
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public void setBgColor(String bgColor) {
            this.bgColor = bgColor;
        }

        public void setLightBg(String lightBg) {
            this.lightBg = lightBg;
        }

        public void setLabelKa(String labelKa) {
            this.labelKa = labelKa;
        }

        public void setLabelEn(String labelEn) {
            this.labelEn = labelEn;
        }

        public void setCardType(String cardType) {
            this.cardType = cardType;
        }
    }
}
