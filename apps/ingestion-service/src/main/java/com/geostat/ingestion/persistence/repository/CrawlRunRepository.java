package com.geostat.ingestion.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geostat.ingestion.persistence.entity.CrawlRunEntity;
public interface CrawlRunRepository extends JpaRepository<CrawlRunEntity, UUID> {
}
