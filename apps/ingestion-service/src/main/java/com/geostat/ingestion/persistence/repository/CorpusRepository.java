package com.geostat.ingestion.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
public interface CorpusRepository extends JpaRepository<CorpusEntity, UUID> {

    Optional<CorpusEntity> findByName(String name);
}
