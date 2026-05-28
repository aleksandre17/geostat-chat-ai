package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "ingestion", name = "document_link")
public class DocumentLinkEntity {

    @Id
    private UUID id;

    @Column(name = "corpus_id", nullable = false)
    private UUID corpusId;

    @Column(name = "source_doc_id", nullable = false)
    private UUID sourceDocId;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Column(name = "target_doc_id")
    private UUID targetDocId;

    @Column(name = "crawl_run_id")
    private UUID crawlRunId;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (discoveredAt == null) {
            discoveredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCorpusId() {
        return corpusId;
    }

    public void setCorpusId(UUID corpusId) {
        this.corpusId = corpusId;
    }

    public UUID getSourceDocId() {
        return sourceDocId;
    }

    public void setSourceDocId(UUID sourceDocId) {
        this.sourceDocId = sourceDocId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public UUID getTargetDocId() {
        return targetDocId;
    }

    public void setTargetDocId(UUID targetDocId) {
        this.targetDocId = targetDocId;
    }

    public UUID getCrawlRunId() {
        return crawlRunId;
    }

    public void setCrawlRunId(UUID crawlRunId) {
        this.crawlRunId = crawlRunId;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }

    public void setDiscoveredAt(Instant discoveredAt) {
        this.discoveredAt = discoveredAt;
    }
}
