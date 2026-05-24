package com.geostat.ingestion.quality;

import com.geostat.ingestion.events.DocumentIndexTrigger;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.DocumentFetchStatus;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("db")
public class CorpusReindexService {

    private final CorpusRepository corpusRepository;
    private final DocumentRepository documentRepository;
    private final DocumentIndexTrigger documentIndexTrigger;

    public CorpusReindexService(
            CorpusRepository corpusRepository,
            DocumentRepository documentRepository,
            DocumentIndexTrigger documentIndexTrigger) {
        this.corpusRepository = corpusRepository;
        this.documentRepository = documentRepository;
        this.documentIndexTrigger = documentIndexTrigger;
    }

    public CorpusReindexReport reindexParsedDocuments(String corpusName) {
        CorpusEntity corpus =
                corpusRepository
                        .findByName(corpusName)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Corpus not found: " + corpusName));

        List<DocumentEntity> documents =
                documentRepository.findByCorpus_IdAndFetchStatus(corpus.getId(), DocumentFetchStatus.parsed);
        for (DocumentEntity document : documents) {
            documentIndexTrigger.requestIndex(document.getId(), corpus.getId());
        }

        String mode = documents.isEmpty() ? "none" : "async-or-sync";
        return new CorpusReindexReport(corpusName, Instant.now(), documents.size(), mode);
    }
}
