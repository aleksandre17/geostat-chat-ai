package com.geostat.chat.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.chat.domain.catalog.CatalogLinkBuilder;
import com.geostat.chat.domain.catalog.CatalogResponseAssembler;
import com.geostat.chat.domain.catalog.CatalogTopicLabelResolver;
import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.Topic;
import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.chat.domain.query.QueryIntentKind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YamlCatalogResponseAssemblerTest {

    @Mock
    private CatalogLinkBuilder catalogLinkBuilder;

    @Mock
    private CatalogTopicLabelResolver catalogTopicLabelResolver;

    private YamlCatalogResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new YamlCatalogResponseAssembler(catalogLinkBuilder, catalogTopicLabelResolver);
    }

    @Test
    void assembleUsesRetrievalTextWhenPresent() {
        AnalyzedQuery analyzed = new AnalyzedQuery(
                "gdp-ა",
                "gdp",
                "gdp",
                "gdp gross domestic product expansion",
                QueryIntentKind.FACTUAL,
                "ka",
                List.of(),
                List.of("gross domestic product"));
        CatalogTopicLabelResolver.Labels labels =
                new CatalogTopicLabelResolver.Labels("GDP", List.of("GDP"));
        List<LinkCard> links = List.of(
                LinkCard.fromCatalog("https://www.geostat.ge/ka/topics/gdp", "GDP", "GDP", "portal", "i", "#fff", null));

        when(catalogTopicLabelResolver.resolve(
                        List.of(Topic.NATIONAL_ACCOUNTS), "gdp gross domestic product expansion", "ka", true))
                .thenReturn(labels);
        when(catalogLinkBuilder.buildLinks(
                        List.of(Topic.NATIONAL_ACCOUNTS), "gdp gross domestic product expansion", true))
                .thenReturn(links);

        CatalogResponseAssembler.Bundle bundle =
                assembler.assemble(List.of(Topic.NATIONAL_ACCOUNTS), analyzed, "ka", true);

        assertThat(bundle.topicLabels()).isEqualTo(labels);
        assertThat(bundle.links()).isEqualTo(links);
        verify(catalogTopicLabelResolver)
                .resolve(List.of(Topic.NATIONAL_ACCOUNTS), "gdp gross domestic product expansion", "ka", true);
        verify(catalogLinkBuilder)
                .buildLinks(List.of(Topic.NATIONAL_ACCOUNTS), "gdp gross domestic product expansion", true);
    }

    @Test
    void assembleFallsBackToNormalizedWhenRetrievalTextBlank() {
        AnalyzedQuery analyzed = new AnalyzedQuery(
                "gdp-ა", "gdp", "gdp normalized", "", QueryIntentKind.FACTUAL, "ka", List.of(), List.of());
        CatalogTopicLabelResolver.Labels labels =
                new CatalogTopicLabelResolver.Labels("GDP", List.of("GDP"));

        when(catalogTopicLabelResolver.resolve(List.of(Topic.NATIONAL_ACCOUNTS), "gdp normalized", "ka", true))
                .thenReturn(labels);
        when(catalogLinkBuilder.buildLinks(List.of(Topic.NATIONAL_ACCOUNTS), "gdp normalized", true))
                .thenReturn(List.of());

        assembler.assemble(List.of(Topic.NATIONAL_ACCOUNTS), analyzed, "ka", true);

        verify(catalogLinkBuilder).buildLinks(List.of(Topic.NATIONAL_ACCOUNTS), "gdp normalized", true);
    }
}
