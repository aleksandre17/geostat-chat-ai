package com.geostat.ingestion.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.geostat.ingestion.persistence.entity.VectorIndexEntity;
public interface VectorIndexRepository extends JpaRepository<VectorIndexEntity, UUID> {
}
