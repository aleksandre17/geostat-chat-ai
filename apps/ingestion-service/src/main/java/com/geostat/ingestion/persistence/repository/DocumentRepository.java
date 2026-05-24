package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByCorpusIdAndUrlHash(UUID corpusId, String urlHash);

    List<DocumentEntity> findByCorpus_IdAndFetchStatus(UUID corpusId, DocumentFetchStatus fetchStatus);

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
}
