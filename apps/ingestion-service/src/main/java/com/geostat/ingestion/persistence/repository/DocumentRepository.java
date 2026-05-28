package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.platform.document.DocumentAuthorityPort;
import com.geostat.platform.document.DocumentCrawlPort;
import com.geostat.platform.document.DocumentEnrichmentPort;
import com.geostat.platform.document.DocumentTopicPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID>,
        DocumentCrawlPort, DocumentEnrichmentPort, DocumentAuthorityPort, DocumentTopicPort {

    Optional<DocumentEntity> findByCorpusIdAndUrlHash(UUID corpusId, String urlHash);

    /** Acquires a pessimistic write lock on the document row for safe concurrent chunk replacement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DocumentEntity d WHERE d.id = :id")
    Optional<DocumentEntity> lockById(@Param("id") UUID id);

    List<DocumentEntity> findByUrlHash(String urlHash);

    List<DocumentEntity> findByCorpus_IdAndFetchStatus(UUID corpusId, DocumentFetchStatus fetchStatus);

    /**
     * Returns document IDs only — avoids loading full entity graph into memory.
     * Scales to 500K+ documents without heap pressure.
     */
    @Query("SELECT d.id FROM DocumentEntity d WHERE d.corpus.id = :corpusId AND d.fetchStatus = :status")
    List<UUID> findIdsByCorpusIdAndFetchStatus(
            @Param("corpusId") UUID corpusId,
            @Param("status") DocumentFetchStatus status);

    List<DocumentEntity> findByCorpus_IdAndLanguageAndFetchStatusAndIdNot(
            UUID corpusId, String language, DocumentFetchStatus fetchStatus, UUID excludeId);

    @Query("""
            SELECT d FROM DocumentEntity d
            WHERE d.corpus.id = :corpusId
              AND d.fetchStatus = :status
              AND d.fetchedAt < :staleBefore
              AND (d.etagHttp IS NOT NULL OR d.lastModifiedHttp IS NOT NULL
                   OR d.lastModified IS NOT NULL)
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

    // ─── Crawl fetch result (CrawlRunStore) ─────────────────────────────────────

    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE ingestion.document
                    SET fetch_status = :fetchStatus,
                        canonical_url = :canonicalUrl,
                        content_hash = :contentHash,
                        http_status = :httpStatus,
                        fetched_at = NOW(),
                        updated_at = NOW()
                    WHERE id = :documentId
                    """,
            nativeQuery = true)
    void updateFetchResult(
            @Param("documentId") UUID documentId,
            @Param("fetchStatus") String fetchStatus,
            @Param("canonicalUrl") String canonicalUrl,
            @Param("contentHash") String contentHash,
            @Param("httpStatus") Integer httpStatus,
            @Param("errorMessage") String errorMessage);

    @Modifying
    @Transactional
    @Query("""
            UPDATE DocumentEntity d
            SET d.contentText = :contentText, d.updatedAt = CURRENT_TIMESTAMP
            WHERE d.id = :documentId
            """)
    void updateContent(
            @Param("documentId") UUID documentId,
            @Param("contentText") String contentText,
            @Param("wordCount") int wordCount);

    // ─── Topic assignment (SmileKMeansTopicMiner / TopicAssignEnrichmentService) ───

    @Modifying
    @Transactional
    @Query("UPDATE DocumentEntity d SET d.topicClusterId = :clusterId, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id = :id")
    void updateTopicCluster(@Param("id") UUID id, @Param("clusterId") UUID clusterId);

    // ─── Enrichment fields ────────────────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query("""
            UPDATE DocumentEntity d
            SET d.summaryKa = :summaryKa, d.summaryEn = :summaryEn,
                d.updatedAt = CURRENT_TIMESTAMP
            WHERE d.id = :id
            """)
    void updateSummary(
            @Param("id") UUID id, @Param("summaryKa") String summaryKa, @Param("summaryEn") String summaryEn);

    @Modifying
    @Transactional
    @Query("""
            UPDATE DocumentEntity d
            SET d.keywords = :keywords, d.updatedAt = CURRENT_TIMESTAMP
            WHERE d.id = :id
            """)
    void updateKeywords(@Param("id") UUID id, @Param("keywords") String[] keywords);

    @Modifying
    @Transactional
    @Query("""
            UPDATE DocumentEntity d
            SET d.pageKind = :pageKind, d.updatedAt = CURRENT_TIMESTAMP
            WHERE d.id = :id
            """)
    void updatePageKind(@Param("id") UUID id, @Param("pageKind") String pageKind);

    @Modifying
    @Transactional
    @Query("""
            UPDATE DocumentEntity d
            SET d.entities = :entities, d.updatedAt = CURRENT_TIMESTAMP
            WHERE d.id = :id
            """)
    void updateEntities(@Param("id") UUID id, @Param("entities") List<Map<String, Object>> entities);

    @Modifying
    @Transactional
    @Query(
            value =
                    """
                    UPDATE ingestion.document
                    SET locale_pair_doc_id = :pairDocId, updated_at = NOW()
                    WHERE id = :id
                    """,
            nativeQuery = true)
    void updateLocalePairDocId(@Param("id") UUID id, @Param("pairDocId") UUID pairDocId);

    @Modifying
    @Transactional
    @Query("UPDATE DocumentEntity d SET d.enrichmentVersion = d.enrichmentVersion + 1, d.updatedAt = CURRENT_TIMESTAMP WHERE d.id = :id")
    void incrementEnrichmentVersion(@Param("id") UUID id);

    // ─── Authority score (JGraphTPageRankAuthorityDeriver) ──────────────────────

    @Modifying
    @Transactional
    @Query("""
            UPDATE DocumentEntity d
            SET d.authorityScore = :score, d.updatedAt = CURRENT_TIMESTAMP
            WHERE d.id = :id
            """)
    void updateAuthorityScore(@Param("id") UUID id, @Param("score") double score);
}
