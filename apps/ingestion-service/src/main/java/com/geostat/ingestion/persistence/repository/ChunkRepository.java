package com.geostat.ingestion.persistence.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.geostat.ingestion.persistence.entity.ChunkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ChunkRepository extends JpaRepository<ChunkEntity, UUID> {

    @Modifying
    void deleteByDocument_Id(UUID documentId);

    List<ChunkEntity> findByDocument_IdOrderBySequenceNoAsc(UUID documentId);
}
