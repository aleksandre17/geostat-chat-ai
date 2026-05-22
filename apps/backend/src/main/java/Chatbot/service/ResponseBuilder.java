package Chatbot.service;

import Chatbot.catalog.NewsCategoryCatalog;
import Chatbot.catalog.SpecificLinkCatalog;
import Chatbot.catalog.TopicRegistry;
import Chatbot.catalog.TopicStyleCatalog;
import Chatbot.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles the ordered LinkCard list for a given set of topics and query.
 *
 * Priority order per topic:
 *   1. Specific keyword matches (high relevance, capped at 6)
 *   2. Special links (organisational topics) → short-circuits remaining steps
 *   3. Portals (capped at 2)
 *   4. Statistics page
 *   5. Metadata
 *   6. Methodology
 *   7. Sectoral accounts (only when explicitly requested)
 *   8. i-Rating (for rating/ranking queries)
 *   9. News (category-specific; promoted to front when "latest" is detected)
 *
 * Cross-topic deduplication is handled internally via a shared URL set.
 * Total result is capped at MAX_LINKS.
 */
@Component
public class ResponseBuilder {

    private static final int MAX_LINKS    = 8;
    private static final int MAX_PORTALS  = 2;
    private static final int MAX_SPECIFIC = 6;

    // ─── Public API ──────────────────────────────────────────────────────────

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
        TopicDefinition.TopicStyle style = TopicRegistry.get(Topic.GENERAL).style();
        TopicStyleCatalog.LinkTypeStyle pls = TopicStyleCatalog.getLinkTypeStyle("portal");
        String icon = pls != null ? pls.icon() : style.icon();
        return TopicRegistry.ALL_PORTALS.stream()
                .map(li -> new LinkCard(li.url(), li.titleKa(), li.titleEn(), "portal", icon, "", style.bgColor()))
                .collect(Collectors.toList());
    }

    // ─── Per-topic assembly ──────────────────────────────────────────────────

    private void addLinksForTopic(Topic primary, String query, boolean isGeorgian,
                                   List<LinkCard> links, Set<String> seen) {
        TopicDefinition def = TopicRegistry.get(primary);
        TopicDefinition.TopicStyle style = def.style();

        addSpecificLinks(query, links, seen, style);

        // GENERAL is the fallback topic: its special/portal links (contact, site-map)
        // are reserved as footer fallback links and must not be injected here.
        // buildFallbackLinks() is the designated entry point for those.
        if (primary == Topic.GENERAL) return;

        if (!def.specialLinks().isEmpty()) {
            addSpecialLinks(def, primary, links, seen, style);
            addNewsIfNeeded(links, seen, primary, query, isGeorgian, style);
            return;
        }

        addPortals(def, links, seen, style);
        addStatisticsLink(def, links, seen, style);
        addMetadataLink(def, links, seen, style);
        addMethodologyLink(def, links, seen, style);
        addSectoralIfNeeded(query, links, seen, style);
        addIRatingIfNeeded(primary, query, links, seen);
        addNewsIfNeeded(links, seen, primary, query, isGeorgian, style);
    }

    /**
     * Returns the GENERAL-topic footer links (contact, site-map).
     * Called only when no content links were found, as a clarification footer.
     */
    public List<LinkCard> buildFallbackLinks() {
        TopicDefinition def = TopicRegistry.get(Topic.GENERAL);
        TopicDefinition.TopicStyle style = def.style();
        List<LinkCard> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LinkInfo li : def.specialLinks()) {
            addLink(result, seen, li.url(), li.titleKa(), li.titleEn(), "contact", style);
        }
        return result;
    }

    // ─── Step methods ────────────────────────────────────────────────────────

    private void addSpecificLinks(String query, List<LinkCard> links, Set<String> seen,
                                   TopicDefinition.TopicStyle style) {
        int added = 0;
        for (LinkInfo li : SpecificLinkCatalog.findMatches(query)) {
            if (added >= MAX_SPECIFIC) break;
            if (seen.add(li.url())) {
                links.add(card(li.url(), li.titleKa(), li.titleEn(), "statistics", style));
                added++;
            }
        }
    }

    private void addSpecialLinks(TopicDefinition def, Topic primary, List<LinkCard> links,
                                  Set<String> seen, TopicDefinition.TopicStyle style) {
        String type = specialLinkType(primary);
        for (LinkInfo li : def.specialLinks()) {
            addLink(links, seen, li.url(), li.titleKa(), li.titleEn(), type, style);
        }
    }

    private void addPortals(TopicDefinition def, List<LinkCard> links, Set<String> seen,
                             TopicDefinition.TopicStyle style) {
        int portals = 0;
        for (PortalInfo p : def.portals()) {
            if (portals >= MAX_PORTALS) break;
            if (seen.add(p.url())) {
                links.add(card(p.url(), p.titleKa(), p.titleEn(), "portal", style));
                portals++;
            }
        }
    }

    private void addStatisticsLink(TopicDefinition def, List<LinkCard> links, Set<String> seen,
                                    TopicDefinition.TopicStyle style) {
        if (def.statistics() != null)
            addLink(links, seen, def.statistics().url(), def.statistics().titleKa(),
                    def.statistics().titleEn(), "statistics", style);
    }

    private void addMetadataLink(TopicDefinition def, List<LinkCard> links, Set<String> seen,
                                  TopicDefinition.TopicStyle style) {
        if (def.metadata() != null)
            addLink(links, seen, def.metadata().url(), def.metadata().titleKa(),
                    def.metadata().titleEn(), "metadata", style);
    }

    private void addMethodologyLink(TopicDefinition def, List<LinkCard> links, Set<String> seen,
                                     TopicDefinition.TopicStyle style) {
        if (def.methodology() != null)
            addLink(links, seen, def.methodology().url(), def.methodology().titleKa(),
                    def.methodology().titleEn(), "methodology", style);
    }

    private void addSectoralIfNeeded(String query, List<LinkCard> links, Set<String> seen,
                                      TopicDefinition.TopicStyle style) {
        String lq = query.toLowerCase();
        if (TopicRegistry.SECTORAL_KEYWORDS.stream().anyMatch(kw -> lq.contains(kw.toLowerCase()))) {
            LinkInfo sa = TopicRegistry.SECTORAL_ACCOUNTS;
            addLink(links, seen, sa.url(), sa.titleKa(), sa.titleEn(), "statistics", style);
        }
    }

    private void addIRatingIfNeeded(Topic primary, String query, List<LinkCard> links, Set<String> seen) {
        String lq = query.toLowerCase();
        if (primary == Topic.I_RATING) return;
        if (lq.contains("რეიტინგ") || lq.contains("rating") ||
                lq.contains("i-rating") || lq.contains("i-რეიტინგ") ||
                lq.contains("რანკინგ")  || lq.contains("ranking")) {
            TopicDefinition iDef = TopicRegistry.get(Topic.I_RATING);
            TopicDefinition.TopicStyle iStyle = iDef.style();
            for (PortalInfo p : iDef.portals()) {
                addLink(links, seen, p.url(), p.titleKa(), p.titleEn(), "portal", iStyle);
            }
        }
    }

    // ─── News helper ─────────────────────────────────────────────────────────

    private void addNewsIfNeeded(List<LinkCard> links, Set<String> seen, Topic primary,
                                  String query, boolean isGeorgian, TopicDefinition.TopicStyle style) {
        String lq = query.toLowerCase();
        boolean wantsLatest = TopicRegistry.LATEST_KEYWORDS.stream()
                .anyMatch(kw -> lq.contains(kw.toLowerCase()));
        boolean newsRelevant = TopicRegistry.NEWS_RELEVANT_TOPICS.contains(primary);
        if (!wantsLatest && !newsRelevant) return;

        LinkInfo news = NewsCategoryCatalog.getCategoryNews(primary, isGeorgian);
        if (!seen.add(news.url())) return;

        TopicStyleCatalog.LinkTypeStyle newsTypeStyle = TopicStyleCatalog.getLinkTypeStyle("news");
        String icon = newsTypeStyle != null ? newsTypeStyle.icon() : "news";
        LinkCard newsCard = new LinkCard(news.url(), news.titleKa(), news.titleEn(),
                "news", icon, "", style.bgColor());

        if (wantsLatest) links.add(0, newsCard);
        else             links.add(newsCard);
    }

    // ─── Card builders ───────────────────────────────────────────────────────

    private void addLink(List<LinkCard> links, Set<String> seen,
                         String url, String ka, String en, String type,
                         TopicDefinition.TopicStyle style) {
        if (seen.add(url)) links.add(card(url, ka, en, type, style));
    }

    private LinkCard card(String url, String ka, String en, String type,
                          TopicDefinition.TopicStyle style) {
        TopicStyleCatalog.LinkTypeStyle ts = TopicStyleCatalog.getLinkTypeStyle(type);
        String icon = ts != null ? ts.icon() : style.icon();
        return new LinkCard(url, ka, en, type, icon, "", style.bgColor());
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