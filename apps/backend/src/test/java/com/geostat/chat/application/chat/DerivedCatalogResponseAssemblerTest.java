package com.geostat.chat.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedClusterMatch;
import com.geostat.chat.domain.catalog.DerivedTopicCluster;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DerivedCatalogResponseAssemblerTest {

    @Mock
    private DerivedCatalogReader derivedCatalogReader;

    @Mock
    private DerivedCatalogLinkBuilder derivedCatalogLinkBuilder;

    private CatalogProperties catalogProperties;
    private DerivedCatalogResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        catalogProperties = new CatalogProperties();
        catalogProperties.setMaxClusters(2);
        assembler = new DerivedCatalogResponseAssembler(
                derivedCatalogReader, derivedCatalogLinkBuilder, catalogProperties);
    }

    @Test
    void assembleUsesSingleClusterMatchForLabelsAndLinks() {
        UUID clusterId = UUID.randomUUID();
        DerivedClusterMatch match =
                new DerivedClusterMatch(List.of(new DerivedTopicCluster(clusterId, "ინფლაცია", "Inflation")));
        List<LinkCard> links = List.of(LinkCard.fromCatalog("https://www.geostat.ge/ka/topics/cpi", "CPI", "CPI", "portal", "i", "#fff", null));

        when(derivedCatalogReader.matchClusters("ინფლაცია", "ka", 2)).thenReturn(match);
        when(derivedCatalogLinkBuilder.buildLinksForClusters(match, true)).thenReturn(links);

        var bundle = assembler.assemble(List.of(Topic.PRICES), "ინფლაცია", "ka", true);

        assertThat(bundle.topicLabels().primary()).isEqualTo("ინფლაცია");
        assertThat(bundle.links()).isEqualTo(links);
        verify(derivedCatalogReader).matchClusters("ინფლაცია", "ka", 2);
        verify(derivedCatalogLinkBuilder).buildLinksForClusters(match, true);
    }
}
