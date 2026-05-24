package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.DocumentLocalePairEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentLocalePairRepository extends JpaRepository<DocumentLocalePairEntity, UUID> {

    Optional<DocumentLocalePairEntity> findByCorpus_IdAndPathKey(UUID corpusId, String pathKey);
}
