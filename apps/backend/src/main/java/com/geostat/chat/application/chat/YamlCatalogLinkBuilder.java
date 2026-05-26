package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.CatalogLinkBuilder;
import com.geostat.chat.domain.catalog.CatalogUrls;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkInfo;
import com.geostat.chat.domain.catalog.PortalInfo;
import com.geostat.chat.domain.catalog.PresentationStyleCatalog;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicDefinition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** YAML topics catalog link assembly (default until eval gate cutover). */
@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "yaml", matchIfMissing = true)
public class YamlCatalogLinkBuilder implements CatalogLinkBuilder {

    private static final int MAX_LINKS = 8;
    private static final int MAX_PORTALS = 2;
    private static final int MAX_SPECIFIC = 6;

    private final TopicCatalog topicCatalog;
    private final PresentationStyleCatalog presentationStyles;

    public YamlCatalogLinkBuilder(TopicCatalog topicCatalog, PresentationStyleCatalog presentationStyles) {
        this.topicCatalog = topicCatalog;
        this.presentationStyles = presentationStyles;
    }

    @Override
    public List<LinkCard> buildLinks(List<Topic> topics, String query, boolean isGeorgian) {
        Set<String> seen = new LinkedHashSet<>();
        List<LinkCard> links = new ArrayList<>();
        for (Topic topic : topics) {
            if (links.size() >= MAX_LINKS) {
                break;
            }
            addLinksForTopic(topic, query, isGeorgian, links, seen);
        }
        return cap(links);
    }

    @Override
    public List<LinkCard> buildPortalLinks(boolean isGeorgian) {
        TopicDefinition.TopicStyle style = topicCatalog.get(Topic.GENERAL).style();
        var pls = presentationStyles.linkTypeStyle("portal");
        String icon = pls.icon();
        return topicCatalog.allPortals().stream()
                .map(li -> new LinkCard(
                        CatalogUrls.localeUrl(li.url(), isGeorgian),
                        li.titleKa(),
                        li.titleEn(),
                        "portal",
                        icon,
                        "",
                        style.bgColor()))
                .collect(Collectors.toList());
    }

    @Override
    public List<LinkCard> buildFallbackLinks(boolean isGeorgian) {
        TopicDefinition def = topicCatalog.get(Topic.GENERAL);
        TopicDefinition.TopicStyle style = def.style();
        List<LinkCard> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LinkInfo li : def.specialLinks()) {
            addLink(result, seen, li.url(), li.titleKa(), li.titleEn(), "contact", style, isGeorgian);
        }
        return result;
    }

    private void addLinksForTopic(
            Topic primary, String query, boolean isGeorgian, List<LinkCard> links, Set<String> seen) {
        TopicDefinition def = topicCatalog.get(primary);
        TopicDefinition.TopicStyle style = def.style();

        addSpecificLinks(query, isGeorgian, links, seen, style);

        if (primary == Topic.GENERAL) {
            return;
        }

        if (!def.specialLinks().isEmpty()) {
            addSpecialLinks(def, primary, isGeorgian, links, seen, style);
            addNewsIfNeeded(links, seen, primary, query, isGeorgian, style);
            return;
        }

        addPortals(def, isGeorgian, links, seen, style);
        addStatisticsLink(def, isGeorgian, links, seen, style);
        addMetadataLink(def, isGeorgian, links, seen, style);
        addMethodologyLink(def, isGeorgian, links, seen, style);
        addSectoralIfNeeded(query, isGeorgian, links, seen, style);
        addIRatingIfNeeded(primary, query, isGeorgian, links, seen);
        addNewsIfNeeded(links, seen, primary, query, isGeorgian, style);
    }

    private void addSpecificLinks(
            String query, boolean isGeorgian, List<LinkCard> links, Set<String> seen, TopicDefinition.TopicStyle style) {
        int added = 0;
        for (LinkInfo li : topicCatalog.matchSpecificLinks(query)) {
            if (added >= MAX_SPECIFIC) {
                break;
            }
            if (seen.add(li.resolvedUrl(isGeorgian))) {
                links.add(cardForLink(li, "statistics", style, isGeorgian));
                added++;
            }
        }
    }

    private void addSpecialLinks(
            TopicDefinition def,
            Topic primary,
            boolean isGeorgian,
            List<LinkCard> links,
            Set<String> seen,
            TopicDefinition.TopicStyle style) {
        String type = specialLinkType(primary);
        for (LinkInfo li : def.specialLinks()) {
            addLink(links, seen, li.url(), li.titleKa(), li.titleEn(), type, style, isGeorgian);
        }
    }

    private void addPortals(
            TopicDefinition def,
            boolean isGeorgian,
            List<LinkCard> links,
            Set<String> seen,
            TopicDefinition.TopicStyle style) {
        int portals = 0;
        for (PortalInfo portal : def.portals()) {
            if (portals >= MAX_PORTALS) {
                break;
            }
            String url = portal.resolvedUrl(isGeorgian);
            if (seen.add(url)) {
                links.add(card(
                        url,
                        portal.titleKa(),
                        portal.titleEn(),
                        "portal",
                        style,
                        isGeorgian,
                        portalSnippet(portal, isGeorgian)));
                portals++;
            }
        }
    }

    private void addStatisticsLink(
            TopicDefinition def,
            boolean isGeorgian,
            List<LinkCard> links,
            Set<String> seen,
            TopicDefinition.TopicStyle style) {
        if (def.statistics() != null) {
            LinkInfo li = def.statistics();
            addLink(links, seen, li, "statistics", style, isGeorgian, null);
        }
    }

    private void addMetadataLink(
            TopicDefinition def,
            boolean isGeorgian,
            List<LinkCard> links,
            Set<String> seen,
            TopicDefinition.TopicStyle style) {
        if (def.metadata() != null) {
            LinkInfo li = def.metadata();
            addLink(links, seen, li, "metadata", style, isGeorgian, null);
        }
    }

    private void addMethodologyLink(
            TopicDefinition def,
            boolean isGeorgian,
            List<LinkCard> links,
            Set<String> seen,
            TopicDefinition.TopicStyle style) {
        if (def.methodology() != null) {
            LinkInfo li = def.methodology();
            addLink(links, seen, li, "methodology", style, isGeorgian, null);
        }
    }

    private void addSectoralIfNeeded(
            String query, boolean isGeorgian, List<LinkCard> links, Set<String> seen, TopicDefinition.TopicStyle style) {
        String lq = query.toLowerCase();
        if (topicCatalog.sectoralKeywords().stream().anyMatch(kw -> lq.contains(kw.toLowerCase()))) {
            LinkInfo sa = topicCatalog.sectoralAccounts();
            addLink(links, seen, sa.url(), sa.titleKa(), sa.titleEn(), "statistics", style, isGeorgian);
        }
    }

    private void addIRatingIfNeeded(
            Topic primary, String query, boolean isGeorgian, List<LinkCard> links, Set<String> seen) {
        String lq = query.toLowerCase();
        if (primary == Topic.I_RATING) {
            return;
        }
        if (lq.contains("რეიტინგ")
                || lq.contains("rating")
                || lq.contains("i-rating")
                || lq.contains("i-რეიტინგ")
                || lq.contains("რანკინგ")
                || lq.contains("ranking")) {
            TopicDefinition iDef = topicCatalog.get(Topic.I_RATING);
            TopicDefinition.TopicStyle iStyle = iDef.style();
            for (PortalInfo portal : iDef.portals()) {
                addLink(
                        links,
                        seen,
                        portal.resolvedUrl(isGeorgian),
                        portal.titleKa(),
                        portal.titleEn(),
                        "portal",
                        iStyle,
                        isGeorgian);
            }
        }
    }

    private void addNewsIfNeeded(
            List<LinkCard> links,
            Set<String> seen,
            Topic primary,
            String query,
            boolean isGeorgian,
            TopicDefinition.TopicStyle style) {
        String lq = query.toLowerCase();
        boolean wantsLatest =
                topicCatalog.latestKeywords().stream().anyMatch(kw -> lq.contains(kw.toLowerCase()));
        boolean newsRelevant = topicCatalog.newsRelevantTopics().contains(primary);
        if (!wantsLatest && !newsRelevant) {
            return;
        }

        LinkInfo news = topicCatalog.categoryNews(primary, isGeorgian);
        String url = CatalogUrls.localeUrl(news.url(), isGeorgian);
        if (!seen.add(url)) {
            return;
        }

        var newsTypeStyle = presentationStyles.linkTypeStyle("news");
        LinkCard newsCard = new LinkCard(
                url, news.titleKa(), news.titleEn(), "news", newsTypeStyle.icon(), "", style.bgColor());

        if (wantsLatest) {
            links.add(0, newsCard);
        } else {
            links.add(newsCard);
        }
    }

    private LinkCard cardForLink(LinkInfo li, String type, TopicDefinition.TopicStyle style, boolean isGeorgian) {
        var ts = presentationStyles.linkTypeStyle(type);
        String icon = ts.icon();
        return catalogCard(li.resolvedUrl(isGeorgian), li.titleKa(), li.titleEn(), type, icon, style.bgColor(), null);
    }

    private void addLink(
            List<LinkCard> links,
            Set<String> seen,
            String url,
            String ka,
            String en,
            String type,
            TopicDefinition.TopicStyle style,
            boolean isGeorgian) {
        addLink(links, seen, new LinkInfo(url, ka, en), type, style, isGeorgian, null);
    }

    private void addLink(
            List<LinkCard> links,
            Set<String> seen,
            LinkInfo li,
            String type,
            TopicDefinition.TopicStyle style,
            boolean isGeorgian,
            String snippet) {
        if (seen.add(li.resolvedUrl(isGeorgian))) {
            var ts = presentationStyles.linkTypeStyle(type);
            String icon = ts.icon();
            links.add(catalogCard(li.resolvedUrl(isGeorgian), li.titleKa(), li.titleEn(), type, icon, style.bgColor(), snippet));
        }
    }

    private LinkCard card(
            String url,
            String ka,
            String en,
            String type,
            TopicDefinition.TopicStyle style,
            boolean isGeorgian,
            String snippet) {
        var ts = presentationStyles.linkTypeStyle(type);
        String icon = ts.icon();
        return catalogCard(CatalogUrls.localeUrl(url, isGeorgian), ka, en, type, icon, style.bgColor(), snippet);
    }

    private static LinkCard catalogCard(
            String url, String ka, String en, String type, String icon, String bgColor, String snippet) {
        return new LinkCard(url, ka, en, type, icon, "", bgColor, "catalog", snippet, null);
    }

    private static String portalSnippet(PortalInfo portal, boolean isGeorgian) {
        if (isGeorgian && portal.descriptionKa() != null && !portal.descriptionKa().isBlank()) {
            return portal.descriptionKa();
        }
        return null;
    }

    private static String specialLinkType(Topic topic) {
        return switch (topic) {
            case MANAGEMENT, ABOUT_US -> "management";
            case CONTACT -> "contact";
            case STRUCTURE -> "structure";
            case LEGISLATION -> "legislation";
            case DATABASE -> "database";
            case SURVEYS -> "surveys";
            case PUBLICATIONS, TENDERS -> "publications";
            case METHODOLOGY, DATA_QUALITY -> "methodology";
            default -> "general";
        };
    }

    private static List<LinkCard> cap(List<LinkCard> links) {
        return links.size() > MAX_LINKS ? new ArrayList<>(links.subList(0, MAX_LINKS)) : links;
    }
}
