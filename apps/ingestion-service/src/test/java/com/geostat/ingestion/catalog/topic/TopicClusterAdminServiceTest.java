package com.geostat.ingestion.catalog.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopicClusterAdminServiceTest {

    @Mock
    private CorpusRepository corpusRepository;

    @Mock
    private TopicClusterRepository topicClusterRepository;

    @Mock
    private CatalogRefreshAfterBatch catalogRefreshAfterBatch;

    private TopicClusterAdminService service;

    @BeforeEach
    void setUp() {
        service = new TopicClusterAdminService(corpusRepository, topicClusterRepository, catalogRefreshAfterBatch);
    }

    @Test
    void approveSetsMetadataAndTriggersRefresh() {
        UUID corpusId = UUID.randomUUID();
        UUID clusterId = UUID.randomUUID();
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        corpus.setName("geostat-portal");

        TopicClusterEntity cluster = new TopicClusterEntity();
        cluster.setId(clusterId);
        cluster.setCorpus(corpus);
        cluster.setLabelKa("ინფლაცია");
        cluster.setLabelEn("Inflation");
        cluster.setKeywords(List.of("cpi"));

        when(topicClusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));
        when(topicClusterRepository.save(cluster)).thenAnswer(invocation -> invocation.getArgument(0));

        var approved = service.approve(clusterId, "owner");

        assertThat(approved.approved()).isTrue();
        assertThat(approved.approvedBy()).isEqualTo("owner");
        assertThat(approved.approvedAt()).isBeforeOrEqualTo(Instant.now());
        verify(catalogRefreshAfterBatch).refreshIfConfigured("topic-approval");
    }

    @Test
    void listForCorpusReturnsViews() {
        UUID corpusId = UUID.randomUUID();
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        corpus.setName("geostat-portal");

        TopicClusterEntity cluster = new TopicClusterEntity();
        cluster.setId(UUID.randomUUID());
        cluster.setCorpus(corpus);
        cluster.setLabelKa("მშპ");
        cluster.setLabelEn("GDP");
        cluster.setKeywords(List.of("gdp"));

        when(corpusRepository.findByName("geostat-portal")).thenReturn(Optional.of(corpus));
        when(topicClusterRepository.findByCorpus_IdOrderByLabelKaAsc(corpusId)).thenReturn(List.of(cluster));

        assertThat(service.listForCorpus("geostat-portal")).hasSize(1);
        verify(catalogRefreshAfterBatch, never()).refreshIfConfigured(any());
    }
}
