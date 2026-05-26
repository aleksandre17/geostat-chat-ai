package com.geostat.ingestion.enrichment.vectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.ingestion.persistence.entity.DocumentEntity;
import org.junit.jupiter.api.Test;

class DocumentSummaryTextTest {

    @Test
    void prefersGeorgianSummaryForKaDocument() {
        DocumentEntity document = new DocumentEntity();
        document.setLanguage("ka");
        document.setSummaryKa("ქართული");
        document.setSummaryEn("English");

        assertThat(DocumentSummaryText.pickForEmbedding(document)).isEqualTo("ქართული");
    }

    @Test
    void prefersEnglishSummaryForEnDocument() {
        DocumentEntity document = new DocumentEntity();
        document.setLanguage("en");
        document.setSummaryKa("ქართული");
        document.setSummaryEn("English");

        assertThat(DocumentSummaryText.pickForEmbedding(document)).isEqualTo("English");
    }

    @Test
    void fallsBackWhenPrimaryLocaleSummaryMissing() {
        DocumentEntity document = new DocumentEntity();
        document.setLanguage("ka");
        document.setSummaryEn("English only");

        assertThat(DocumentSummaryText.pickForEmbedding(document)).isEqualTo("English only");
    }
}
