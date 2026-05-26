package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "expected_intent")
    private String expectedIntent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_entities", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> expectedEntities = new ArrayList<>();

    @Column(name = "expected_topic")
    private UUID expectedTopic;

    @Column(nullable = false)
    private String difficulty = "medium";

    @Column(nullable = false)
    private String source = "curated";

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

    public String getExpectedIntent() {
        return expectedIntent;
    }

    public void setExpectedIntent(String expectedIntent) {
        this.expectedIntent = expectedIntent;
    }

    public List<Map<String, Object>> getExpectedEntities() {
        return expectedEntities;
    }

    public void setExpectedEntities(List<Map<String, Object>> expectedEntities) {
        this.expectedEntities = expectedEntities != null ? expectedEntities : new ArrayList<>();
    }

    public UUID getExpectedTopic() {
        return expectedTopic;
    }

    public void setExpectedTopic(UUID expectedTopic) {
        this.expectedTopic = expectedTopic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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
