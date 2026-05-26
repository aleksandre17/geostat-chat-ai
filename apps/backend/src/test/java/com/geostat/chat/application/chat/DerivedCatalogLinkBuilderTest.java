package com.geostat.chat.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geostat.chat.domain.catalog.DerivedCatalogLink;
import com.geostat.chat.domain.catalog.DerivedCatalogReader;
import com.geostat.chat.domain.catalog.DerivedClusterMatch;
import com.geostat.chat.domain.catalog.DerivedTopicCluster;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.infrastructure.catalog.YamlPresentationStyleCatalog;
import com.geostat.chat.infrastructure.config.CatalogProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DerivedCatalogLinkBuilderTest {

    @Mock
    private DerivedCatalogReader derivedCatalogReader;

    private CatalogProperties catalogProperties;
    private YamlPresentationStyleCatalog presentationStyles;
    private DerivedCatalogLinkBuilder builder;

    @BeforeEach
    void setUp() {
        catalogProperties = new CatalogProperties();
        catalogProperties.setMaxClusters(2);
        catalogProperties.setMaxSpecificRank(2);
        catalogProperties.setMaxPortalLinks(1);
        presentationStyles = YamlPresentationStyleCatalog.fromClasspath();
        builder = new DerivedCatalogLinkBuilder(derivedCatalogReader, catalogProperties, presentationStyles);
    }

    @Test
    void buildLinksAddsPortalThenSpecificWithoutDuplicateUrls() {
        UUID clusterId = UUID.randomUUID();
        DerivedClusterMatch match =
                new DerivedClusterMatch(List.of(new DerivedTopicCluster(clusterId, "CPI", "CPI")));
        when(derivedCatalogReader.matchClusters("cpi inflation", "ka", 2)).thenReturn(match);
        when(derivedCatalogReader.findPortalLinks(List.of(clusterId), "ka"))
                .thenReturn(List.of(new DerivedCatalogLink(
                        "https://www.geostat.ge/ka/topics/cpi",
                        "CPI პორტალი",
                        "ინფლაციის მაჩვენებელი",
                        "portal",
                        0.9)));
        when(derivedCatalogReader.findSpecificLinks(List.of(clusterId), "ka", 2))
                .thenReturn(List.of(
                        new DerivedCatalogLink(
                                "https://www.geostat.ge/ka/topics/cpi",
                                "CPI report",
                                "report",
                                "report",
                                0.8),
                        new DerivedCatalogLink(
                                "https://www.geostat.ge/ka/database/cpi",
                                "CPI DB",
                                "dataset",
                                "dataset",
                                0.7)));

        List<LinkCard> links = builder.buildLinks(List.of(), "cpi inflation", true);

        assertThat(links).hasSize(2);
        assertThat(links.get(0).type()).isEqualTo("portal");
        assertThat(links.get(1).type()).isEqualTo("statistics");
    }

    @Test
    void cardTypeForPageKindComesFromPresentationCatalog() {
        assertThat(presentationStyles.cardTypeForPageKind("portal")).isEqualTo("portal");
        assertThat(presentationStyles.cardTypeForPageKind("report")).isEqualTo("statistics");
        assertThat(presentationStyles.cardTypeForPageKind("news")).isEqualTo("news");
    }
}
