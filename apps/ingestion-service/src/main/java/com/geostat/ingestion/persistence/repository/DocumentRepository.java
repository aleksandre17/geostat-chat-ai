package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByCorpusIdAndUrlHash(UUID corpusId, String urlHash);

    List<DocumentEntity> findByUrlHash(String urlHash);

    List<DocumentEntity> findByCorpus_IdAndFetchStatus(UUID corpusId, DocumentFetchStatus fetchStatus);

    List<DocumentEntity> findByCorpus_IdAndLanguageAndFetchStatusAndIdNot(
            UUID corpusId, String language, DocumentFetchStatus fetchStatus, UUID excludeId);

    @Query("""
            SELECT d FROM DocumentEntity d
            WHERE d.corpus.id = :corpusId
              AND d.fetchStatus = :status
              AND d.fetchedAt < :staleBefore
              AND (d.httpEtag IS NOT NULL OR d.lastModified IS NOT NULL)
            ORDER BY d.fetchedAt ASC
            """)
    List<DocumentEntity> findStaleWithValidators(
            @Param("corpusId") UUID corpusId,
            @Param("status") DocumentFetchStatus status,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);

    @Query("""
            SELECT d FROM DocumentEntity d
            WHERE d.corpus.id = :corpusId
              AND d.fetchStatus = com.geostat.ingestion.persistence.model.DocumentFetchStatus.parsed
              AND d.pageKind <> 'navigation'
              AND (
                  (d.summaryKa IS NOT NULL AND d.summaryKa <> '')
                  OR (d.summaryEn IS NOT NULL AND d.summaryEn <> '')
              )
            """)
    List<DocumentEntity> findTopicMiningCandidates(@Param("corpusId") UUID corpusId);

    @Modifying
    @Query("UPDATE DocumentEntity d SET d.topicClusterId = NULL WHERE d.corpus.id = :corpusId")
    int clearTopicClusterAssignments(@Param("corpusId") UUID corpusId);

    /** P1 backfill — parsed docs missing completed summary or page_kind for current model versions. */
    @Query("""
            SELECT d.id FROM DocumentEntity d
            WHERE d.corpus.id = :corpusId
              AND d.fetchStatus = com.geostat.ingestion.persistence.model.DocumentFetchStatus.parsed
              AND (
                NOT EXISTS (
                  SELECT 1 FROM EnrichmentRunEntity r
                  WHERE r.document.id = d.id
                    AND r.deriverKind = com.geostat.ingestion.persistence.model.EnrichmentDeriverKind.summary
                    AND r.modelVersion = :summaryModelVersion
                    AND r.status = com.geostat.ingestion.persistence.model.EnrichmentRunStatus.completed
                )
                OR NOT EXISTS (
                  SELECT 1 FROM EnrichmentRunEntity r
                  WHERE r.document.id = d.id
                    AND r.deriverKind = com.geostat.ingestion.persistence.model.EnrichmentDeriverKind.page_kind
                    AND r.modelVersion = :pageKindModelVersion
                    AND r.status = com.geostat.ingestion.persistence.model.EnrichmentRunStatus.completed
                )
              )
            ORDER BY d.fetchedAt ASC
            """)
    List<UUID> findIdsNeedingP1EnrichmentBackfill(
            @Param("corpusId") UUID corpusId,
            @Param("summaryModelVersion") String summaryModelVersion,
            @Param("pageKindModelVersion") String pageKindModelVersion);
}
