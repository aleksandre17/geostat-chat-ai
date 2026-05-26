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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "section_path", nullable = false, columnDefinition = "jsonb")
    private List<String> sectionPath = new ArrayList<>();

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "lead_text")
    private String leadText;

    @Column(name = "meta_description")
    private String metaDescription;

    @Column(name = "display_description")
    private String displayDescription;

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

    @Column(name = "http_etag")
    private String httpEtag;

    @Column(name = "last_modified")
    private Instant lastModified;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_document_id")
    private DocumentEntity supersedesDocument;

    @Column(name = "summary_ka")
    private String summaryKa;

    @Column(name = "summary_en")
    private String summaryEn;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] keywords = new String[0];

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> entities = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locale_pair_doc_id")
    private DocumentEntity localePairDocument;

    @Column(name = "authority_score", nullable = false)
    private double authorityScore;

    @Column(name = "page_kind", nullable = false)
    private String pageKind = "unknown";

    @Column(name = "topic_cluster_id")
    private UUID topicClusterId;

    @Column(name = "score_boost", nullable = false)
    private double scoreBoost = 1.0;

    @Column(name = "enrichment_version", nullable = false)
    private int enrichmentVersion;

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

    public List<String> getSectionPath() {
        return sectionPath;
    }

    public void setSectionPath(List<String> sectionPath) {
        this.sectionPath = sectionPath != null ? sectionPath : new ArrayList<>();
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

    public String getLeadText() {
        return leadText;
    }

    public void setLeadText(String leadText) {
        this.leadText = leadText;
    }

    public String getMetaDescription() {
        return metaDescription;
    }

    public void setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
    }

    public String getDisplayDescription() {
        return displayDescription;
    }

    public void setDisplayDescription(String displayDescription) {
        this.displayDescription = displayDescription;
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

    public String getHttpEtag() {
        return httpEtag;
    }

    public void setHttpEtag(String httpEtag) {
        this.httpEtag = httpEtag;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
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

    public String getSummaryKa() {
        return summaryKa;
    }

    public void setSummaryKa(String summaryKa) {
        this.summaryKa = summaryKa;
    }

    public String getSummaryEn() {
        return summaryEn;
    }

    public void setSummaryEn(String summaryEn) {
        this.summaryEn = summaryEn;
    }

    public String[] getKeywords() {
        return keywords;
    }

    public void setKeywords(String[] keywords) {
        this.keywords = keywords != null ? keywords : new String[0];
    }

    public List<Map<String, Object>> getEntities() {
        return entities;
    }

    public void setEntities(List<Map<String, Object>> entities) {
        this.entities = entities != null ? entities : new ArrayList<>();
    }

    public DocumentEntity getLocalePairDocument() {
        return localePairDocument;
    }

    public void setLocalePairDocument(DocumentEntity localePairDocument) {
        this.localePairDocument = localePairDocument;
    }

    public double getAuthorityScore() {
        return authorityScore;
    }

    public void setAuthorityScore(double authorityScore) {
        this.authorityScore = authorityScore;
    }

    public String getPageKind() {
        return pageKind;
    }

    public void setPageKind(String pageKind) {
        this.pageKind = pageKind;
    }

    public UUID getTopicClusterId() {
        return topicClusterId;
    }

    public void setTopicClusterId(UUID topicClusterId) {
        this.topicClusterId = topicClusterId;
    }

    public double getScoreBoost() {
        return scoreBoost;
    }

    public void setScoreBoost(double scoreBoost) {
        this.scoreBoost = scoreBoost;
    }

    public int getEnrichmentVersion() {
        return enrichmentVersion;
    }

    public void setEnrichmentVersion(int enrichmentVersion) {
        this.enrichmentVersion = enrichmentVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
