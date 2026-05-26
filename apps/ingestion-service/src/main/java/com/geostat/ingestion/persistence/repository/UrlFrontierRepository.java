package com.geostat.ingestion.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.geostat.ingestion.persistence.entity.UrlFrontierEntity;
import com.geostat.ingestion.persistence.model.FrontierStatus;

public interface UrlFrontierRepository extends JpaRepository<UrlFrontierEntity, UUID> {

    List<UrlFrontierEntity> findTop50ByCrawlRun_IdAndStatusOrderByDiscoveredAtAsc(
            UUID crawlRunId, FrontierStatus status);

    List<UrlFrontierEntity> findTop500ByCrawlRun_IdAndStatusOrderByDiscoveredAtAsc(
            UUID crawlRunId, FrontierStatus status);

    long countByCrawlRun_IdAndStatus(UUID crawlRunId, FrontierStatus status);

    boolean existsByCrawlRun_IdAndUrlHash(UUID crawlRunId, String urlHash);

    @Query("""
            SELECT f.url, f.parentUrl
            FROM UrlFrontierEntity f
            JOIN f.crawlRun r
            WHERE r.corpus.id = :corpusId
              AND f.parentUrl IS NOT NULL
              AND f.parentUrl <> ''
            """)
    List<Object[]> findParentChildUrlsByCorpusId(@Param("corpusId") UUID corpusId);
}
