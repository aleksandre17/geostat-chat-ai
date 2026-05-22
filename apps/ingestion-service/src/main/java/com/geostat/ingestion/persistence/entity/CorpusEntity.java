package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.geostat.ingestion.persistence.model.CorpusStatus;
@Entity
@Table(schema = "ingestion", name = "corpus")
public class CorpusEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seed_urls", nullable = false, columnDefinition = "jsonb")
    private List<String> seedUrls = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> policy = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorpusStatus status = CorpusStatus.active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSeedUrls() {
        return seedUrls;
    }

    public void setSeedUrls(List<String> seedUrls) {
        this.seedUrls = seedUrls;
    }

    public Map<String, Object> getPolicy() {
        return policy;
    }

    public void setPolicy(Map<String, Object> policy) {
        this.policy = policy;
    }

    public CorpusStatus getStatus() {
        return status;
    }

    public void setStatus(CorpusStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
