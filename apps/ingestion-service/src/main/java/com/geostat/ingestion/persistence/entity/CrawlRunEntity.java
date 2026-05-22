package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.geostat.ingestion.persistence.model.CrawlRunStatus;
@Entity
@Table(schema = "ingestion", name = "crawl_run")
public class CrawlRunEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corpus_id", nullable = false)
    private CorpusEntity corpus;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CrawlRunStatus status = CrawlRunStatus.pending;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> stats = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configSnapshot = new HashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CorpusEntity getCorpus() {
        return corpus;
    }

    public void setCorpus(CorpusEntity corpus) {
        this.corpus = corpus;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public CrawlRunStatus getStatus() {
        return status;
    }

    public void setStatus(CrawlRunStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }

    public Map<String, Object> getConfigSnapshot() {
        return configSnapshot;
    }

    public void setConfigSnapshot(Map<String, Object> configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
