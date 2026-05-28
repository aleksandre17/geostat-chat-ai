package com.geostat.ingestion.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geostat.ingestion.persistence.entity.VectorIndexEntity;

public interface VectorIndexRepository extends JpaRepository<VectorIndexEntity, UUID> {

    /** Bulk-deletes all vector index rows for a document in a single SQL DELETE statement. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM VectorIndexEntity v WHERE v.chunk.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);
}
