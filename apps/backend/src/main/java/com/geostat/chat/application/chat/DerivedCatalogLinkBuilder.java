package com.geostat.chat.application.chat;

import com.geostat.chat.application.retrieval.SourceUrlNormalizer;
import com.geostat.chat.domain.catalog.CatalogLinkBuilder;
import com.geostat.chat.domain.catalog.CatalogUrls;
import com.geostat.chat.domain.catalog.DerivedCatalogLink;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedClusterMatch;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.PresentationStyleCatalog;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class DerivedCatalogLinkBuilder implements CatalogLinkBuilder {

    private static final int MAX_LINKS = 8;
    private static final int MAX_SPECIFIC = 6;

    private final DerivedCatalogReader derivedCatalogReader;
    private final CatalogProperties catalogProperties;
    private final PresentationStyleCatalog presentationStyles;

    public DerivedCatalogLinkBuilder(
            DerivedCatalogReader derivedCatalogReader,
            CatalogProperties catalogProperties,
            PresentationStyleCatalog presentationStyles) {
        this.derivedCatalogReader = derivedCatalogReader;
        this.catalogProperties = catalogProperties;
        this.presentationStyles = presentationStyles;
    }

    @Override
    public List<LinkCard> buildLinks(List<Topic> topics, String query, boolean isGeorgian) {
        String language = isGeorgian ? "ka" : "en";
        DerivedClusterMatch match =
                derivedCatalogReader.matchClusters(query, language, catalogProperties.maxClusters());
        return buildLinksForClusters(match, isGeorgian);
    }

    List<LinkCard> buildLinksForClusters(DerivedClusterMatch match, boolean isGeorgian) {
        if (match == null || match.isEmpty()) {
            return List.of();
        }
        String language = isGeorgian ? "ka" : "en";
        List<UUID> clusters = match.clusterIds();

        Set<String> seen = new LinkedHashSet<>();
        List<LinkCard> links = new ArrayList<>();

        addLinks(
                links,
                seen,
                derivedCatalogReader.findPortalLinks(clusters, language),
                isGeorgian,
                catalogProperties.maxPortalLinks());

        addLinks(
                links,
                seen,
                derivedCatalogReader.findSpecificLinks(clusters, language, catalogProperties.maxSpecificRank()),
                isGeorgian,
                MAX_SPECIFIC);

        return links.size() > MAX_LINKS ? List.copyOf(links.subList(0, MAX_LINKS)) : List.copyOf(links);
    }

    @Override
    public List<LinkCard> buildPortalLinks(boolean isGeorgian) {
        String language = isGeorgian ? "ka" : "en";
        return derivedCatalogReader.findTopPortalLinks(language, catalogProperties.maxPortalLinks()).stream()
                .map(link -> toLinkCard(link, isGeorgian))
                .toList();
    }

    @Override
    public List<LinkCard> buildFallbackLinks(boolean isGeorgian) {
        return buildPortalLinks(isGeorgian);
    }

    private void addLinks(
            List<LinkCard> links,
            Set<String> seen,
            List<DerivedCatalogLink> source,
            boolean isGeorgian,
            int maxCount) {
        int added = 0;
        for (DerivedCatalogLink link : source) {
            if (links.size() >= MAX_LINKS || added >= maxCount) {
                break;
            }
            String url = CatalogUrls.localeUrl(link.url(), isGeorgian);
            if (url.isBlank() || !seen.add(SourceUrlNormalizer.normalize(url))) {
                continue;
            }
            links.add(toLinkCard(link, isGeorgian));
            added++;
        }
    }

    LinkCard toLinkCard(DerivedCatalogLink link, boolean isGeorgian) {
        String linkType = presentationStyles.cardTypeForPageKind(link.pageKind());
        var style = presentationStyles.pageKindStyle(link.pageKind());
        String title = link.title() != null ? link.title().strip() : "";
        String titleKa = isGeorgian ? title : fallbackTitle(title, link.url(), false);
        String titleEn = isGeorgian ? fallbackTitle(title, link.url(), false) : title;
        String snippet = trimSnippet(link.summary());
        return LinkCard.fromCatalog(
                CatalogUrls.localeUrl(link.url(), isGeorgian),
                titleKa,
                titleEn,
                linkType,
                style.icon(),
                style.bgColor(),
                snippet);
    }

    private static String fallbackTitle(String primary, String url, boolean isGeorgian) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return isGeorgian ? "ბმული" : "Link";
    }

    private static String trimSnippet(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String normalized = summary.strip().replace('\n', ' ');
        return normalized.length() > 240 ? normalized.substring(0, 237) + "..." : normalized;
    }
}
