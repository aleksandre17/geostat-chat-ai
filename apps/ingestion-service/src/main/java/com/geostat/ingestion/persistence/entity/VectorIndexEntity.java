package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "ingestion", name = "vector_index")
public class VectorIndexEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chunk_id", nullable = false)
    private ChunkEntity chunk;

    @Column(name = "collection_name", nullable = false)
    private String collectionName;

    @Column(name = "point_id", nullable = false)
    private String pointId;

    @Column(name = "index_version", nullable = false)
    private String indexVersion = "v1";

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        indexedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ChunkEntity getChunk() {
        return chunk;
    }

    public void setChunk(ChunkEntity chunk) {
        this.chunk = chunk;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getPointId() {
        return pointId;
    }

    public void setPointId(String pointId) {
        this.pointId = pointId;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public void setIndexVersion(String indexVersion) {
        this.indexVersion = indexVersion;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }
}
