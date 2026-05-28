package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.DocumentLinkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentLinkRepository extends JpaRepository<DocumentLinkEntity, UUID> {

    @Query("""
        SELECT dl FROM DocumentLinkEntity dl
        WHERE dl.corpusId = :corpusId
          AND dl.targetDocId IS NOT NULL
    """)
    List<DocumentLinkEntity> findResolvedEdgesByCorpus(@Param("corpusId") UUID corpusId);

    boolean existsBySourceDocIdAndTargetUrl(UUID sourceDocId, String targetUrl);
}
