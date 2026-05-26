package com.geostat.chat.application.chat;

import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class DerivedCatalogTopicLabelResolver implements CatalogTopicLabelResolver {

    private final DerivedCatalogReader derivedCatalogReader;
    private final CatalogProperties catalogProperties;

    public DerivedCatalogTopicLabelResolver(
            DerivedCatalogReader derivedCatalogReader, CatalogProperties catalogProperties) {
        this.derivedCatalogReader = derivedCatalogReader;
        this.catalogProperties = catalogProperties;
    }

    @Override
    public Labels resolve(List<Topic> detectedTopics, String query, String language, boolean georgian) {
        String resolvedLanguage = language != null && !language.isBlank() ? language : (georgian ? "ka" : "en");
        return DerivedCatalogResponseAssembler.labelsFrom(
                derivedCatalogReader.matchClusters(
                        query, resolvedLanguage, catalogProperties.maxClusters()),
                detectedTopics,
                georgian);
    }
}
