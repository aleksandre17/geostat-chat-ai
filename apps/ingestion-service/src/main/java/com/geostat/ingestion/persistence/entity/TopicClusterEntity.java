package com.geostat.ingestion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "ingestion", name = "topic_cluster")
public class TopicClusterEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corpus_id", nullable = false)
    private CorpusEntity corpus;

    @Column(name = "label_ka", nullable = false)
    private String labelKa;

    @Column(name = "label_en", nullable = false)
    private String labelEn;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] keywords = new String[0];

    @Column(name = "document_count", nullable = false)
    private int documentCount;

    @Column(name = "centroid_summary")
    private String centroidSummary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "centroid_embedding", columnDefinition = "real[]")
    private float[] centroidEmbedding;

    @Column(nullable = false)
    private boolean approved;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

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

    public String getLabelKa() {
        return labelKa;
    }

    public void setLabelKa(String labelKa) {
        this.labelKa = labelKa;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public void setLabelEn(String labelEn) {
        this.labelEn = labelEn;
    }

    public List<String> getKeywords() {
        return keywords == null || keywords.length == 0 ? List.of() : List.of(keywords);
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null || keywords.isEmpty() ? new String[0] : keywords.toArray(String[]::new);
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public String getCentroidSummary() {
        return centroidSummary;
    }

    public void setCentroidSummary(String centroidSummary) {
        this.centroidSummary = centroidSummary;
    }

    public float[] getCentroidEmbedding() {
        return centroidEmbedding;
    }

    public void setCentroidEmbedding(float[] centroidEmbedding) {
        this.centroidEmbedding = centroidEmbedding;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}
