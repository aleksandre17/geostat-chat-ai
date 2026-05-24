package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.TopicCatalog;
import com.geostat.chat.domain.catalog.TopicStyleCatalog;
import com.geostat.chat.domain.catalog.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles the ordered LinkCard list for a given set of topics and query.
 */
@Component
public class ResponseBuilder {

    private static final int MAX_LINKS    = 8;
    private static final int MAX_PORTALS  = 2;
    private static final int MAX_SPECIFIC = 6;

    private final TopicCatalog topicCatalog;

    public ResponseBuilder(TopicCatalog topicCatalog) {
        this.topicCatalog = topicCatalog;
    }

    public List<LinkCard> buildLinks(List<Topic> topics, String query, boolean isGeorgian) {
        Set<String> seen = new LinkedHashSet<>();
        List<LinkCard> links = new ArrayList<>();
        for (Topic t : topics) {
            if (links.size() >= MAX_LINKS) break;
            addLinksForTopic(t, query, isGeorgian, links, seen);
        }
        return cap(links);
    }

    public List<LinkCard> buildPortalLinks(boolean isGeorgian) {
        TopicDefinition.TopicStyle style = topicCatalog.get(Topic.GENERAL).style();
        TopicStyleCatalog.LinkTypeStyle pls = TopicStyleCatalog.getLinkTypeStyle("portal");
        String icon = pls != null ? pls.icon() : style.icon();
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

    private void addLinksForTopic(Topic primary, String query, boolean isGeorgian,
                                   List<LinkCard> links, Set<String> seen) {
        TopicDefinition def = topicCatalog.get(primary);
        TopicDefinition.TopicStyle style = def.style();

        addSpecificLinks(query, isGeorgian, links, seen, style);

        if (primary == Topic.GENERAL) return;

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

    private void addSpecificLinks(String query, boolean isGeorgian, List<LinkCard> links, Set<String> seen,
                                   TopicDefinition.TopicStyle style) {
        int added = 0;
        for (LinkInfo li : topicCatalog.matchSpecificLinks(query)) {
            if (added >= MAX_SPECIFIC) break;
            if (seen.add(li.resolvedUrl(isGeorgian))) {
                links.add(cardForLink(li, "statistics", style, isGeorgian));
                added++;
            }
        }
    }

    private void addSpecialLinks(TopicDefinition def, Topic primary, boolean isGeorgian, List<LinkCard> links,
                                  Set<String> seen, TopicDefinition.TopicStyle style) {
        String type = specialLinkType(primary);
        for (LinkInfo li : def.specialLinks()) {
            addLink(links, seen, li.url(), li.titleKa(), li.titleEn(), type, style, isGeorgian);
        }
    }

    private void addPortals(TopicDefinition def, boolean isGeorgian, List<LinkCard> links, Set<String> seen,
                             TopicDefinition.TopicStyle style) {
        int portals = 0;
        for (PortalInfo p : def.portals()) {
            if (portals >= MAX_PORTALS) break;
            String url = p.resolvedUrl(isGeorgian);
            if (seen.add(url)) {
                links.add(card(url, p.titleKa(), p.titleEn(), "portal", style, isGeorgian, portalSnippet(p, isGeorgian)));
                portals++;
            }
        }
    }

    private void addStatisticsLink(TopicDefinition def, boolean isGeorgian, List<LinkCard> links, Set<String> seen,
                                    TopicDefinition.TopicStyle style) {
        if (def.statistics() != null) {
            LinkInfo li = def.statistics();
            addLink(links, seen, li, "statistics", style, isGeorgian, linkSnippet(li, isGeorgian));
        }
    }

    private void addMetadataLink(TopicDefinition def, boolean isGeorgian, List<LinkCard> links, Set<String> seen,
                                  TopicDefinition.TopicStyle style) {
        if (def.metadata() != null) {
            LinkInfo li = def.metadata();
            addLink(links, seen, li, "metadata", style, isGeorgian, linkSnippet(li, isGeorgian));
        }
    }

    private void addMethodologyLink(TopicDefinition def, boolean isGeorgian, List<LinkCard> links, Set<String> seen,
                                     TopicDefinition.TopicStyle style) {
        if (def.methodology() != null) {
            LinkInfo li = def.methodology();
            addLink(links, seen, li, "methodology", style, isGeorgian, linkSnippet(li, isGeorgian));
        }
    }

    private void addSectoralIfNeeded(String query, boolean isGeorgian, List<LinkCard> links, Set<String> seen,
                                      TopicDefinition.TopicStyle style) {
        String lq = query.toLowerCase();
        if (topicCatalog.sectoralKeywords().stream().anyMatch(kw -> lq.contains(kw.toLowerCase()))) {
            LinkInfo sa = topicCatalog.sectoralAccounts();
            addLink(links, seen, sa.url(), sa.titleKa(), sa.titleEn(), "statistics", style, isGeorgian);
        }
    }

    private void addIRatingIfNeeded(Topic primary, String query, boolean isGeorgian, List<LinkCard> links,
                                   Set<String> seen) {
        String lq = query.toLowerCase();
        if (primary == Topic.I_RATING) return;
        if (lq.contains("რეიტინგ") || lq.contains("rating") ||
                lq.contains("i-rating") || lq.contains("i-რეიტინგ") ||
                lq.contains("რანკინგ")  || lq.contains("ranking")) {
            TopicDefinition iDef = topicCatalog.get(Topic.I_RATING);
            TopicDefinition.TopicStyle iStyle = iDef.style();
            for (PortalInfo p : iDef.portals()) {
                addLink(links, seen, p.resolvedUrl(isGeorgian), p.titleKa(), p.titleEn(), "portal", iStyle, isGeorgian);
            }
        }
    }

    private void addNewsIfNeeded(List<LinkCard> links, Set<String> seen, Topic primary,
                                  String query, boolean isGeorgian, TopicDefinition.TopicStyle style) {
        String lq = query.toLowerCase();
        boolean wantsLatest = topicCatalog.latestKeywords().stream()
                .anyMatch(kw -> lq.contains(kw.toLowerCase()));
        boolean newsRelevant = topicCatalog.newsRelevantTopics().contains(primary);
        if (!wantsLatest && !newsRelevant) return;

        LinkInfo news = topicCatalog.categoryNews(primary, isGeorgian);
        String url = CatalogUrls.localeUrl(news.url(), isGeorgian);
        if (!seen.add(url)) return;

        TopicStyleCatalog.LinkTypeStyle newsTypeStyle = TopicStyleCatalog.getLinkTypeStyle("news");
        String icon = newsTypeStyle != null ? newsTypeStyle.icon() : "news";
        LinkCard newsCard = new LinkCard(url, news.titleKa(), news.titleEn(),
                "news", icon, "", style.bgColor());

        if (wantsLatest) links.add(0, newsCard);
        else             links.add(newsCard);
    }

    private LinkCard cardForLink(LinkInfo li, String type, TopicDefinition.TopicStyle style, boolean isGeorgian) {
        TopicStyleCatalog.LinkTypeStyle ts = TopicStyleCatalog.getLinkTypeStyle(type);
        String icon = ts != null ? ts.icon() : style.icon();
        return catalogCard(li.resolvedUrl(isGeorgian), li.titleKa(), li.titleEn(), type, icon, style.bgColor(),
                linkSnippet(li, isGeorgian));
    }

    private void addLink(List<LinkCard> links, Set<String> seen,
                         String url, String ka, String en, String type,
                         TopicDefinition.TopicStyle style, boolean isGeorgian) {
        addLink(links, seen, new LinkInfo(url, ka, en), type, style, isGeorgian, linkSnippet(ka, en, isGeorgian));
    }

    private void addLink(List<LinkCard> links, Set<String> seen, LinkInfo li, String type,
                         TopicDefinition.TopicStyle style, boolean isGeorgian, String snippet) {
        if (seen.add(li.resolvedUrl(isGeorgian))) {
            TopicStyleCatalog.LinkTypeStyle ts = TopicStyleCatalog.getLinkTypeStyle(type);
            String icon = ts != null ? ts.icon() : style.icon();
            links.add(catalogCard(li.resolvedUrl(isGeorgian), li.titleKa(), li.titleEn(), type, icon, style.bgColor(),
                    snippet));
        }
    }

    private LinkCard card(String url, String ka, String en, String type,
                          TopicDefinition.TopicStyle style, boolean isGeorgian, String snippet) {
        TopicStyleCatalog.LinkTypeStyle ts = TopicStyleCatalog.getLinkTypeStyle(type);
        String icon = ts != null ? ts.icon() : style.icon();
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

    private static String linkSnippet(LinkInfo link, boolean isGeorgian) {
        return null;
    }

    private static String linkSnippet(String titleKa, String titleEn, boolean isGeorgian) {
        return null;
    }

    private String specialLinkType(Topic t) {
        return switch (t) {
            case MANAGEMENT, ABOUT_US -> "management";
            case CONTACT              -> "contact";
            case STRUCTURE            -> "structure";
            case LEGISLATION          -> "legislation";
            case DATABASE             -> "database";
            case SURVEYS              -> "surveys";
            case PUBLICATIONS, TENDERS -> "publications";
            case METHODOLOGY, DATA_QUALITY -> "methodology";
            default                   -> "general";
        };
    }

    private List<LinkCard> cap(List<LinkCard> links) {
        return links.size() > MAX_LINKS ? new ArrayList<>(links.subList(0, MAX_LINKS)) : links;
    }
}
