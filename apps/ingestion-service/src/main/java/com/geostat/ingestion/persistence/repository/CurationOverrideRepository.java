package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.CurationOverrideEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurationOverrideRepository extends JpaRepository<CurationOverrideEntity, UUID> {

    @Query("""
            SELECT co FROM CurationOverrideEntity co
            WHERE co.expiresAt IS NULL OR co.expiresAt > :now
            ORDER BY co.createdAt DESC
            """)
    List<CurationOverrideEntity> findActive(@Param("now") Instant now);

    @Query("""
            SELECT COUNT(co) FROM CurationOverrideEntity co
            WHERE co.expiresAt IS NULL OR co.expiresAt > :now
            """)
    long countActive(@Param("now") Instant now);

    Optional<CurationOverrideEntity> findByUrlHashAndAction(String urlHash, String action);

    @Query("""
            SELECT CASE WHEN COUNT(co) > 0 THEN true ELSE false END
            FROM CurationOverrideEntity co
            WHERE co.urlHash = :urlHash
              AND co.action = 'exclude'
              AND (co.expiresAt IS NULL OR co.expiresAt > :now)
            """)
    boolean isUrlExcluded(@Param("urlHash") String urlHash, @Param("now") Instant now);
}
