package com.geostat.ingestion.curation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.ingestion.index.lifecycle.DocumentQdrantLifecycleSync;
import com.geostat.ingestion.persistence.entity.CurationOverrideEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import com.geostat.ingestion.persistence.repository.CurationOverrideRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurationOverlayServiceTest {

    @Mock
    private CurationOverrideRepository curationOverrideRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TopicClusterRepository topicClusterRepository;

    @Mock
    private CatalogRefreshAfterBatch catalogRefreshAfterBatch;

    @Mock
    private DocumentQdrantLifecycleSync documentQdrantLifecycleSync;

    private CurationOverlayService service;

    @BeforeEach
    void setUp() {
        service = new CurationOverlayService(
                curationOverrideRepository,
                documentRepository,
                topicClusterRepository,
                catalogRefreshAfterBatch,
                documentQdrantLifecycleSync);
    }

    @Test
    void excludeOverrideTriggersCatalogRefresh() {
        when(curationOverrideRepository.countActive(any())).thenReturn(0L);
        when(curationOverrideRepository.findByUrlHashAndAction(any(), eq("exclude")))
                .thenReturn(Optional.empty());
        when(curationOverrideRepository.save(any())).thenAnswer(invocation -> {
            CurationOverrideEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        var created = service.create(new CurationOverlayService.CreateCurationOverrideCommand(
                "https://www.geostat.ge/ka/spam",
                "exclude",
                null,
                Map.of(),
                "noisy page",
                null,
                "owner"));

        assertThat(created.action()).isEqualTo("exclude");
        assertThat(created.reason()).isEqualTo("noisy page");
        verify(catalogRefreshAfterBatch).refreshIfConfigured("curation-create");
    }

    @Test
    void boostAppliesScoreMultiplierAndStoresPrevious() {
        DocumentEntity document = new DocumentEntity();
        document.setScoreBoost(1.0);

        when(curationOverrideRepository.countActive(any())).thenReturn(0L);
        when(curationOverrideRepository.findByUrlHashAndAction(any(), eq("boost")))
                .thenReturn(Optional.empty());
        when(documentRepository.findByUrlHash(any())).thenReturn(List.of(document));
        when(curationOverrideRepository.save(any())).thenAnswer(invocation -> {
            CurationOverrideEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        service.create(new CurationOverlayService.CreateCurationOverrideCommand(
                "https://www.geostat.ge/ka/key",
                "boost",
                null,
                Map.of("factor", 1.5),
                "important release",
                null,
                "owner"));

        assertThat(document.getScoreBoost()).isEqualTo(1.5);
        ArgumentCaptor<CurationOverrideEntity> captor = ArgumentCaptor.forClass(CurationOverrideEntity.class);
        verify(curationOverrideRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload().get("previousScoreBoost")).isEqualTo(1.0);
        verify(catalogRefreshAfterBatch, never()).refreshIfConfigured(any());
    }

    @Test
    void renameTopicRequiresLabels() {
        UUID clusterId = UUID.randomUUID();
        TopicClusterEntity cluster = new TopicClusterEntity();
        cluster.setId(clusterId);
        when(topicClusterRepository.findById(clusterId)).thenReturn(Optional.of(cluster));

        assertThatThrownBy(() -> service.create(new CurationOverlayService.CreateCurationOverrideCommand(
                        null,
                        "rename_topic",
                        clusterId.toString(),
                        Map.of(),
                        "label fix",
                        null,
                        "owner")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("labelKa");
    }

    @Test
    void rejectsWhenBudgetExceeded() {
        when(curationOverrideRepository.countActive(any())).thenReturn(50L);
        when(curationOverrideRepository.findByUrlHashAndAction(any(), eq("exclude")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CurationOverlayService.CreateCurationOverrideCommand(
                        "https://www.geostat.ge/ka/x",
                        "exclude",
                        null,
                        Map.of(),
                        "too many",
                        null,
                        "owner")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("budget exceeded");
    }

    @Test
    void deleteRevertsBoostAndRemovesRow() {
        UUID id = UUID.randomUUID();
        DocumentEntity document = new DocumentEntity();
        document.setScoreBoost(1.2);

        CurationOverrideEntity entity = new CurationOverrideEntity();
        entity.setId(id);
        entity.setUrlHash("abc");
        entity.setAction("boost");
        entity.setPayload(Map.of("previousScoreBoost", 1.0));
        entity.setReason("test");
        entity.setCreatedBy("owner");
        entity.setCreatedAt(Instant.now());

        when(curationOverrideRepository.findById(id)).thenReturn(Optional.of(entity));
        when(documentRepository.findByUrlHash("abc")).thenReturn(List.of(document));

        service.delete(id);

        assertThat(document.getScoreBoost()).isEqualTo(1.0);
        verify(curationOverrideRepository).delete(entity);
    }
}
