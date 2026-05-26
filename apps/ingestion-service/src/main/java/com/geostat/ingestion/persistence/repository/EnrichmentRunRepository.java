package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.EnrichmentRunEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.model.EnrichmentRunStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrichmentRunRepository extends JpaRepository<EnrichmentRunEntity, UUID> {

    Optional<EnrichmentRunEntity> findByDocument_IdAndDeriverKindAndModelVersion(
            UUID documentId, EnrichmentDeriverKind deriverKind, String modelVersion);

    boolean existsByDocument_IdAndDeriverKindAndModelVersionAndStatus(
            UUID documentId, EnrichmentDeriverKind deriverKind, String modelVersion, EnrichmentRunStatus status);

    List<EnrichmentRunEntity> findByDocument_Id(UUID documentId);
}
