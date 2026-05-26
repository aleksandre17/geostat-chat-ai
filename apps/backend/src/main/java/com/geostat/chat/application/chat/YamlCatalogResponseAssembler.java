package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.CatalogLinkBuilder;
import com.geostat.chat.domain.catalog.CatalogResponseAssembler;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "yaml", matchIfMissing = true)
public class YamlCatalogResponseAssembler implements CatalogResponseAssembler {

    private final CatalogLinkBuilder catalogLinkBuilder;
    private final CatalogTopicLabelResolver catalogTopicLabelResolver;

    public YamlCatalogResponseAssembler(
            CatalogLinkBuilder catalogLinkBuilder, CatalogTopicLabelResolver catalogTopicLabelResolver) {
        this.catalogLinkBuilder = catalogLinkBuilder;
        this.catalogTopicLabelResolver = catalogTopicLabelResolver;
    }

    @Override
    public Bundle assemble(List<Topic> detectedTopics, String query, String language, boolean georgian) {
        CatalogTopicLabelResolver.Labels topicLabels =
                catalogTopicLabelResolver.resolve(detectedTopics, query, language, georgian);
        List<LinkCard> links = catalogLinkBuilder.buildLinks(detectedTopics, query, georgian);
        return new Bundle(topicLabels, links);
    }

    @Override
    public List<LinkCard> buildPortalLinks(boolean georgian) {
        return catalogLinkBuilder.buildPortalLinks(georgian);
    }
}
