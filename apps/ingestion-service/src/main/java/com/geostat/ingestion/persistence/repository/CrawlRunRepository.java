package com.geostat.ingestion.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
import com.geostat.ingestion.persistence.model.CrawlRunStatus;

public interface CrawlRunRepository extends JpaRepository<CrawlRunEntity, UUID> {

    boolean existsByCorpus_IdAndStatusIn(UUID corpusId, Collection<CrawlRunStatus> statuses);

    List<CrawlRunEntity> findByStatusIn(Collection<CrawlRunStatus> statuses);

    Optional<CrawlRunEntity> findFirstByCorpus_IdOrderByCreatedAtDesc(UUID corpusId);
}
