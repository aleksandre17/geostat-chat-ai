package com.geostat.ingestion.enrichment.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.geostat.embedding.EmbeddingPort;
import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.DocumentLocalePairEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.DocumentLocalePairRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.enrichment.DocumentContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlPlusEmbeddingLocalePairerTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentLocalePairRepository pairRepository;

    @Mock
    private EmbeddingPort embeddingPort;

    private UrlPlusEmbeddingLocalePairer pairer;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties();
        properties.setLocalePairSimilarityThreshold(0.92);
        pairer = new UrlPlusEmbeddingLocalePairer(
                documentRepository, pairRepository, embeddingPort, properties);
    }

    @Test
    void findPairByCounterpartUrl() {
        UUID corpusId = UUID.randomUUID();
        UUID kaId = UUID.randomUUID();
        UUID enId = UUID.randomUUID();
        DocumentEntity ka = document(corpusId, kaId, "ka", "https://www.geostat.ge/ka/modules/41", "Statistics ka");
        DocumentEntity en = document(corpusId, enId, "en", "https://www.geostat.ge/en/modules/41", "Statistics en");

        when(documentRepository.findById(kaId)).thenReturn(Optional.of(ka));
        when(documentRepository.findByCorpusIdAndUrlHash(
                        eq(corpusId), eq(UrlHasher.hash("https://www.geostat.ge/en/modules/41"))))
                .thenReturn(Optional.of(en));

        Optional<UUID> pair = pairer.findPair(context(ka));

        assertThat(pair).contains(enId);
    }

    @Test
    void findPairFromLocalePairTableWhenCounterpartUrlMissing() {
        UUID corpusId = UUID.randomUUID();
        UUID kaId = UUID.randomUUID();
        UUID enId = UUID.randomUUID();
        DocumentEntity ka = document(corpusId, kaId, "ka", "https://www.geostat.ge/ka/modules/41", "Statistics ka");
        DocumentEntity en = document(corpusId, enId, "en", "https://www.geostat.ge/en/modules/41", "Statistics en");
        DocumentLocalePairEntity pairEntity = new DocumentLocalePairEntity();
        pairEntity.setKaDocument(ka);
        pairEntity.setEnDocument(en);

        when(documentRepository.findById(kaId)).thenReturn(Optional.of(ka));
        when(documentRepository.findByCorpusIdAndUrlHash(any(), any())).thenReturn(Optional.empty());
        when(pairRepository.findByCorpus_IdAndPathKey(corpusId, "/modules/41")).thenReturn(Optional.of(pairEntity));

        Optional<UUID> pair = pairer.findPair(context(ka));

        assertThat(pair).contains(enId);
    }

    @Test
    void findPairByTitleEmbeddingWhenUrlPatternMissing() {
        UUID corpusId = UUID.randomUUID();
        UUID kaId = UUID.randomUUID();
        UUID enId = UUID.randomUUID();
        DocumentEntity ka = document(corpusId, kaId, "ka", "https://legacy.geostat.ge/page?id=1", "Consumer Price Index");
        DocumentEntity en = document(corpusId, enId, "en", "https://legacy.geostat.ge/page?id=2", "Consumer Price Index");

        when(documentRepository.findById(kaId)).thenReturn(Optional.of(ka));
        when(documentRepository.findByCorpus_IdAndLanguageAndFetchStatusAndIdNot(
                        corpusId, "en", DocumentFetchStatus.parsed, kaId))
                .thenReturn(List.of(en));
        when(embeddingPort.embed("Consumer Price Index")).thenReturn(new float[] {1f, 0f}, new float[] {1f, 0f});

        Optional<UUID> pair = pairer.findPair(context(ka));

        assertThat(pair).contains(enId);
    }

    private static DocumentContext context(DocumentEntity document) {
        return new DocumentContext(
                document.getId(),
                document.getCanonicalUrl(),
                document.getTitle(),
                "content",
                document.getLanguage(),
                "");
    }

    private static DocumentEntity document(UUID corpusId, UUID id, String language, String url, String title) {
        CorpusEntity corpus = new CorpusEntity();
        corpus.setId(corpusId);
        DocumentEntity document = new DocumentEntity();
        document.setId(id);
        document.setCorpus(corpus);
        document.setCanonicalUrl(url);
        document.setLanguage(language);
        document.setTitle(title);
        document.setFetchStatus(DocumentFetchStatus.parsed);
        return document;
    }
}
