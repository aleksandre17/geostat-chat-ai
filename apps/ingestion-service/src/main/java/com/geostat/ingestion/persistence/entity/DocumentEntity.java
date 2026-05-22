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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
@Entity
@Table(schema = "ingestion", name = "document")
public class DocumentEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corpus_id", nullable = false)
    private CorpusEntity corpus;

    @Column(name = "canonical_url", nullable = false)
    private String canonicalUrl;

    @Column(name = "url_hash", nullable = false)
    private String urlHash;

    private String title;

    private String language;

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "raw_storage_key")
    private String rawStorageKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_status", nullable = false)
    private DocumentFetchStatus fetchStatus = DocumentFetchStatus.pending;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_document_id")
    private DocumentEntity supersedesDocument;

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

    public CorpusEntity getCorpus() {
        return corpus;
    }

    public void setCorpus(CorpusEntity corpus) {
        this.corpus = corpus;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public void setCanonicalUrl(String canonicalUrl) {
        this.canonicalUrl = canonicalUrl;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public void setUrlHash(String urlHash) {
        this.urlHash = urlHash;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getRawStorageKey() {
        return rawStorageKey;
    }

    public void setRawStorageKey(String rawStorageKey) {
        this.rawStorageKey = rawStorageKey;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public DocumentFetchStatus getFetchStatus() {
        return fetchStatus;
    }

    public void setFetchStatus(DocumentFetchStatus fetchStatus) {
        this.fetchStatus = fetchStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public DocumentEntity getSupersedesDocument() {
        return supersedesDocument;
    }

    public void setSupersedesDocument(DocumentEntity supersedesDocument) {
        this.supersedesDocument = supersedesDocument;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
