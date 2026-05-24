package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "ingestion", name = "evaluation_query")
public class EvaluationQueryEntity {

    @Id
    private UUID id;

    @Column(name = "corpus_name", nullable = false)
    private String corpusName;

    @Column(nullable = false)
    private String locale;

    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "expect_url")
    private String expectUrl;

    @Column(name = "min_chunks", nullable = false)
    private int minChunks = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCorpusName() {
        return corpusName;
    }

    public void setCorpusName(String corpusName) {
        this.corpusName = corpusName;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getExpectUrl() {
        return expectUrl;
    }

    public void setExpectUrl(String expectUrl) {
        this.expectUrl = expectUrl;
    }

    public int getMinChunks() {
        return minChunks;
    }

    public void setMinChunks(int minChunks) {
        this.minChunks = minChunks;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
