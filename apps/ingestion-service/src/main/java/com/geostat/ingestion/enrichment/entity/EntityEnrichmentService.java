package com.geostat.ingestion.enrichment.entity;

import com.geostat.ingestion.enrichment.runner.EnrichmentProperties;
import com.geostat.ingestion.enrichment.runner.EnrichmentRunExecutor;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.model.EnrichmentDeriverKind;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.Entity;
import com.geostat.platform.enrichment.EntityDeriver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
@Service
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class EntityEnrichmentService {

    private final EnrichmentRunExecutor enrichmentRunExecutor;
    private final EntityDeriver entityDeriver;
    private final DocumentRepository documentRepository;
    private final EnrichmentProperties properties;

    public EntityEnrichmentService(
            EnrichmentRunExecutor enrichmentRunExecutor,
            EntityDeriver entityDeriver,
            DocumentRepository documentRepository,
            EnrichmentProperties properties) {
        this.enrichmentRunExecutor = enrichmentRunExecutor;
        this.entityDeriver = entityDeriver;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    public void enrichDocument(UUID documentId) {
        String modelVersion = properties.entityModelVersion();
        enrichmentRunExecutor.run(
                documentId,
                EnrichmentDeriverKind.entities,
                modelVersion,
                properties.maxRetries(),
                this::shouldSkip,
                document -> entityDeriver.deriveEntities(toContext(document)),
                this::persistEntities,
                "entities");
    }

    private boolean shouldSkip(DocumentEntity document) {
        return document.getContentText() == null || document.getContentText().isBlank();
    }

    private void persistEntities(DocumentEntity document, List<Entity> entities) {
        List<Map<String, Object>> payload = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", entity.type());
            row.put("value", entity.value());
            row.put("normalizedForm", entity.normalizedForm());
            row.put("confidence", entity.confidence());
            payload.add(row);
        }
        document.setEntities(payload);
        documentRepository.updateEntities(document.getId(), payload);
    }

    private static DocumentContext toContext(DocumentEntity document) {
        String sectionPath = Optional.ofNullable(document.getSectionPath()).orElseGet(List::of).stream()
                .collect(Collectors.joining(" > "));
        return new DocumentContext(
                document.getId(),
                document.getCanonicalUrl(),
                document.getTitle(),
                document.getContentText(),
                document.getLanguage(),
                sectionPath);
    }
}
