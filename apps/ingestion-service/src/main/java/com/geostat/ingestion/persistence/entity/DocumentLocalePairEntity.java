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
@Table(schema = "ingestion", name = "document_locale_pair")
public class DocumentLocalePairEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corpus_id", nullable = false)
    private CorpusEntity corpus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ka_document_id")
    private DocumentEntity kaDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "en_document_id")
    private DocumentEntity enDocument;

    @Column(name = "ka_url", nullable = false)
    private String kaUrl;

    @Column(name = "en_url", nullable = false)
    private String enUrl;

    @Column(name = "path_key", nullable = false)
    private String pathKey;

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

    public CorpusEntity getCorpus() {
        return corpus;
    }

    public void setCorpus(CorpusEntity corpus) {
        this.corpus = corpus;
    }

    public DocumentEntity getKaDocument() {
        return kaDocument;
    }

    public void setKaDocument(DocumentEntity kaDocument) {
        this.kaDocument = kaDocument;
    }

    public DocumentEntity getEnDocument() {
        return enDocument;
    }

    public void setEnDocument(DocumentEntity enDocument) {
        this.enDocument = enDocument;
    }

    public String getKaUrl() {
        return kaUrl;
    }

    public void setKaUrl(String kaUrl) {
        this.kaUrl = kaUrl;
    }

    public String getEnUrl() {
        return enUrl;
    }

    public void setEnUrl(String enUrl) {
        this.enUrl = enUrl;
    }

    public String getPathKey() {
        return pathKey;
    }

    public void setPathKey(String pathKey) {
        this.pathKey = pathKey;
    }
}
