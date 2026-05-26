package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.CatalogResponseAssembler;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedClusterMatch;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class DerivedCatalogResponseAssembler implements CatalogResponseAssembler {

    private final DerivedCatalogReader derivedCatalogReader;
    private final DerivedCatalogLinkBuilder derivedCatalogLinkBuilder;
    private final CatalogProperties catalogProperties;

    public DerivedCatalogResponseAssembler(
            DerivedCatalogReader derivedCatalogReader,
            DerivedCatalogLinkBuilder derivedCatalogLinkBuilder,
            CatalogProperties catalogProperties) {
        this.derivedCatalogReader = derivedCatalogReader;
        this.derivedCatalogLinkBuilder = derivedCatalogLinkBuilder;
        this.catalogProperties = catalogProperties;
    }

    @Override
    public Bundle assemble(List<Topic> detectedTopics, String query, String language, boolean georgian) {
        String resolvedLanguage = language != null && !language.isBlank() ? language : (georgian ? "ka" : "en");
        DerivedClusterMatch match =
                derivedCatalogReader.matchClusters(query, resolvedLanguage, catalogProperties.maxClusters());
        CatalogTopicLabelResolver.Labels topicLabels = labelsFrom(match, detectedTopics, georgian);
        List<LinkCard> links = derivedCatalogLinkBuilder.buildLinksForClusters(match, georgian);
        return new Bundle(topicLabels, links);
    }

    @Override
    public List<LinkCard> buildPortalLinks(boolean georgian) {
        return derivedCatalogLinkBuilder.buildPortalLinks(georgian);
    }

    static CatalogTopicLabelResolver.Labels labelsFrom(
            DerivedClusterMatch match, List<Topic> detectedTopics, boolean georgian) {
        if (match != null && !match.isEmpty()) {
            List<String> labels = match.clusters().stream().map(cluster -> cluster.label(georgian)).toList();
            return new CatalogTopicLabelResolver.Labels(labels.get(0), labels);
        }
        List<Topic> topics = detectedTopics == null || detectedTopics.isEmpty() ? List.of(Topic.GENERAL) : detectedTopics;
        List<String> fallback = topics.stream().map(Topic::name).toList();
        return new CatalogTopicLabelResolver.Labels(fallback.get(0), fallback);
    }
}
