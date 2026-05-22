package com.geostat.ingestion.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByCorpusIdAndUrlHash(UUID corpusId, String urlHash);
}
