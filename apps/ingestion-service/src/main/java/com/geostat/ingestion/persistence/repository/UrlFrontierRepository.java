package com.geostat.ingestion.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.FrontierStatus;
public interface UrlFrontierRepository extends JpaRepository<UrlFrontierEntity, UUID> {

    List<UrlFrontierEntity> findTop50ByCrawlRun_IdAndStatusOrderByDiscoveredAtAsc(
            UUID crawlRunId, FrontierStatus status);

    boolean existsByCrawlRun_IdAndUrlHash(UUID crawlRunId, String urlHash);
}
