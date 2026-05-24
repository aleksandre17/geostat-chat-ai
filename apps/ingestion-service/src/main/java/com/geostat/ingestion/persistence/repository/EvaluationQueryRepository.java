package com.geostat.ingestion.persistence.repository;

import com.geostat.ingestion.persistence.entity.EvaluationQueryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationQueryRepository extends JpaRepository<EvaluationQueryEntity, java.util.UUID> {

    List<EvaluationQueryEntity> findByCorpusNameAndActiveTrueOrderByLocaleAscQueryTextAsc(String corpusName);
}
