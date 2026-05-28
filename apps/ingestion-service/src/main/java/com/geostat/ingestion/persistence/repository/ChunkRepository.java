package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.ChunkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChunkRepository extends JpaRepository<ChunkEntity, UUID> {

    /** Bulk-deletes all chunks for the given document in a single SQL DELETE statement. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ChunkEntity c WHERE c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    List<ChunkEntity> findByDocument_IdOrderBySequenceNoAsc(UUID documentId);
}
