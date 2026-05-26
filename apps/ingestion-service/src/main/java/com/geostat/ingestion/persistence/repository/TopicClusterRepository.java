package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicClusterRepository extends JpaRepository<TopicClusterEntity, UUID> {

    List<TopicClusterEntity> findByCorpus_IdOrderByLabelKaAsc(UUID corpusId);

    long countByCorpus_Id(UUID corpusId);

    long countByCorpus_IdAndApprovedTrue(UUID corpusId);

    @Modifying
    @Query("DELETE FROM TopicClusterEntity t WHERE t.corpus.id = :corpusId")
    int deleteAllByCorpusId(@Param("corpusId") UUID corpusId);
}
