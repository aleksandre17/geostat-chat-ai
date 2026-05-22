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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import com.geostat.ingestion.persistence.model.FrontierStatus;
@Entity
@Table(schema = "ingestion", name = "url_frontier")
public class UrlFrontierEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crawl_run_id", nullable = false)
    private CrawlRunEntity crawlRun;

    @Column(nullable = false)
    private String url;

    @Column(name = "url_hash", nullable = false)
    private String urlHash;

    @Column(nullable = false)
    private int depth;

    @Column(name = "parent_url")
    private String parentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrontierStatus status = FrontierStatus.queued;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        discoveredAt = now;
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

    public CrawlRunEntity getCrawlRun() {
        return crawlRun;
    }

    public void setCrawlRun(CrawlRunEntity crawlRun) {
        this.crawlRun = crawlRun;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public void setUrlHash(String urlHash) {
        this.urlHash = urlHash;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getParentUrl() {
        return parentUrl;
    }

    public void setParentUrl(String parentUrl) {
        this.parentUrl = parentUrl;
    }

    public FrontierStatus getStatus() {
        return status;
    }

    public void setStatus(FrontierStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
