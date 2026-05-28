package com.geostat.ingestion.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Returns the subset of urlHashes that already exist in the frontier
     * for the given crawl run. Single batch query instead of N individual
     * existsByCrawlRun_IdAndUrlHash() calls.
     */
    @Query("SELECT f.urlHash FROM UrlFrontierEntity f "
            + "WHERE f.crawlRun.id = :crawlRunId AND f.urlHash IN :hashes")
    List<String> findExistingHashesByCrawlRunAndHashIn(
            @Param("crawlRunId") UUID crawlRunId, @Param("hashes") Set<String> hashes);

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
