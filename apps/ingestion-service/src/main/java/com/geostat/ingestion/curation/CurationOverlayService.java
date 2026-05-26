package com.geostat.ingestion.curation;

import com.geostat.ingestion.catalog.refresh.CatalogRefreshAfterBatch;
import com.geostat.ingestion.crawl.frontier.UrlHasher;
import com.geostat.ingestion.index.lifecycle.DocumentQdrantLifecycleSync;
import com.geostat.ingestion.persistence.entity.CurationOverrideEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.entity.TopicClusterEntity;
import com.geostat.ingestion.persistence.repository.CurationOverrideRepository;
import com.geostat.ingestion.persistence.repository.DocumentRepository;
import com.geostat.ingestion.persistence.repository.TopicClusterRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("db")
public class CurationOverlayService {

    static final int MAX_ACTIVE_OVERRIDES = 50;
    static final int DEFAULT_TTL_DAYS = 90;
    static final double DEFAULT_BOOST_FACTOR = 1.2;
    static final double DEFAULT_DEMOTE_FACTOR = 0.8;
    static final double MIN_SCORE_BOOST = 0.5;
    static final double MAX_SCORE_BOOST = 2.0;

    private final CurationOverrideRepository curationOverrideRepository;
    private final DocumentRepository documentRepository;
    private final TopicClusterRepository topicClusterRepository;
    private final CatalogRefreshAfterBatch catalogRefreshAfterBatch;
    private final DocumentQdrantLifecycleSync documentQdrantLifecycleSync;

    public CurationOverlayService(
            CurationOverrideRepository curationOverrideRepository,
            DocumentRepository documentRepository,
            TopicClusterRepository topicClusterRepository,
            CatalogRefreshAfterBatch catalogRefreshAfterBatch,
            DocumentQdrantLifecycleSync documentQdrantLifecycleSync) {
        this.curationOverrideRepository = curationOverrideRepository;
        this.documentRepository = documentRepository;
        this.topicClusterRepository = topicClusterRepository;
        this.catalogRefreshAfterBatch = catalogRefreshAfterBatch;
        this.documentQdrantLifecycleSync = documentQdrantLifecycleSync;
    }

    public List<CurationOverrideView> listActive() {
        Instant now = Instant.now();
        return curationOverrideRepository.findActive(now).stream()
                .map(entity -> CurationOverrideView.from(entity, resolveUrl(entity)))
                .toList();
    }

    @Transactional
    public CurationOverrideView create(CreateCurationOverrideCommand command) {
        Instant now = Instant.now();
        CurationAction action = CurationAction.parse(command.action());
        validateCreate(command, action);

        String urlHash = resolveUrlHash(command, action);
        CurationOverrideEntity entity = curationOverrideRepository
                .findByUrlHashAndAction(urlHash, action.value())
                .orElseGet(CurationOverrideEntity::new);

        boolean isNew = entity.getId() == null;
        if (isNew && curationOverrideRepository.countActive(now) >= MAX_ACTIVE_OVERRIDES) {
            throw new IllegalArgumentException(
                    "curation budget exceeded: max " + MAX_ACTIVE_OVERRIDES + " active overrides");
        }

        if (!isNew && entity.isActive(now)) {
            revertSideEffects(entity);
        }

        entity.setUrlHash(urlHash);
        entity.setAction(action.value());
        entity.setTarget(normalizeTarget(command.target(), action));
        entity.setPayload(buildPayload(command, action, urlHash));
        entity.setReason(command.reason().strip());
        entity.setCreatedBy(requireActor(command.createdBy()));
        entity.setExpiresAt(command.expiresAt() != null ? command.expiresAt() : defaultExpiry(now));

        CurationOverrideEntity saved = curationOverrideRepository.save(entity);
        applySideEffects(saved, action);
        refreshCatalogIfNeeded(action, "curation-create");
        syncQdrantLifecycle(saved, action);
        return CurationOverrideView.from(saved, command.url());
    }

    @Transactional
    public void delete(UUID id) {
        CurationOverrideEntity entity = curationOverrideRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown curation override: " + id));
        CurationAction action = CurationAction.parse(entity.getAction());
        revertSideEffects(entity);
        curationOverrideRepository.delete(entity);
        refreshCatalogIfNeeded(action, "curation-delete");
        syncQdrantLifecycle(entity, action);
    }

    private void validateCreate(CreateCurationOverrideCommand command, CurationAction action) {
        if (command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("reason is mandatory");
        }
        switch (action) {
            case BOOST, DEMOTE, EXCLUDE, PIN_AS_PORTAL -> requireUrl(command.url());
            case RENAME_TOPIC -> validateRenameTopic(command);
            default -> throw new IllegalArgumentException("unsupported action");
        }
        if (action == CurationAction.PIN_AS_PORTAL) {
            requireTopicCluster(parseUuid(command.target(), "target topic_cluster id"));
        }
    }

    private void validateRenameTopic(CreateCurationOverrideCommand command) {
        UUID clusterId = parseUuid(command.target(), "target topic_cluster id");
        topicClusterRepository
                .findById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("unknown topic cluster: " + clusterId));
        Map<String, Object> payload = command.payload() != null ? command.payload() : Map.of();
        String labelKa = stringPayload(payload, "labelKa");
        String labelEn = stringPayload(payload, "labelEn");
        if (labelKa == null && labelEn == null) {
            throw new IllegalArgumentException("rename_topic requires payload.labelKa and/or payload.labelEn");
        }
    }

    private String resolveUrlHash(CreateCurationOverrideCommand command, CurationAction action) {
        if (action == CurationAction.RENAME_TOPIC) {
            return UrlHasher.hash("rename_topic:" + command.target().strip());
        }
        return UrlHasher.hash(requireUrl(command.url()));
    }

    private void syncQdrantLifecycle(CurationOverrideEntity entity, CurationAction action) {
        if (action == CurationAction.RENAME_TOPIC) {
            return;
        }
        documentQdrantLifecycleSync.syncByUrlHash(entity.getUrlHash());
    }

    private static String resolveUrl(CurationOverrideEntity entity) {
        if (CurationAction.RENAME_TOPIC.value().equals(entity.getAction())) {
            return null;
        }
        Object stored = entity.getPayload().get("url");
        return stored instanceof String url ? url : null;
    }

    private Map<String, Object> buildPayload(
            CreateCurationOverrideCommand command, CurationAction action, String urlHash) {
        Map<String, Object> payload = new HashMap<>();
        if (command.payload() != null) {
            payload.putAll(command.payload());
        }
        if (action != CurationAction.RENAME_TOPIC && command.url() != null) {
            payload.put("url", command.url().strip());
        }
        switch (action) {
            case BOOST -> payload.putIfAbsent("factor", DEFAULT_BOOST_FACTOR);
            case DEMOTE -> payload.putIfAbsent("factor", DEFAULT_DEMOTE_FACTOR);
            default -> {}
        }
        if (action == CurationAction.BOOST || action == CurationAction.DEMOTE) {
            documentRepository.findByUrlHash(urlHash).stream()
                    .findFirst()
                    .ifPresent(doc -> payload.put("previousScoreBoost", doc.getScoreBoost()));
        }
        return payload;
    }

    private void applySideEffects(CurationOverrideEntity entity, CurationAction action) {
        if (action != CurationAction.BOOST && action != CurationAction.DEMOTE) {
            return;
        }
        double factor = factorFromPayload(entity.getPayload(), action);
        for (DocumentEntity document : documentRepository.findByUrlHash(entity.getUrlHash())) {
            double updated = clampScoreBoost(document.getScoreBoost() * factor);
            document.setScoreBoost(updated);
            documentRepository.save(document);
        }
    }

    private void revertSideEffects(CurationOverrideEntity entity) {
        CurationAction action = CurationAction.parse(entity.getAction());
        if (action != CurationAction.BOOST && action != CurationAction.DEMOTE) {
            return;
        }
        Object previous = entity.getPayload().get("previousScoreBoost");
        if (!(previous instanceof Number number)) {
            return;
        }
        double restored = clampScoreBoost(number.doubleValue());
        for (DocumentEntity document : documentRepository.findByUrlHash(entity.getUrlHash())) {
            document.setScoreBoost(restored);
            documentRepository.save(document);
        }
    }

    private void refreshCatalogIfNeeded(CurationAction action, String reason) {
        if (action == CurationAction.EXCLUDE || action == CurationAction.PIN_AS_PORTAL) {
            catalogRefreshAfterBatch.refreshIfConfigured(reason);
        }
    }

    private static double factorFromPayload(Map<String, Object> payload, CurationAction action) {
        Object raw = payload.get("factor");
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return action == CurationAction.BOOST ? DEFAULT_BOOST_FACTOR : DEFAULT_DEMOTE_FACTOR;
    }

    private static double clampScoreBoost(double value) {
        return Math.max(MIN_SCORE_BOOST, Math.min(MAX_SCORE_BOOST, value));
    }

    private static Instant defaultExpiry(Instant now) {
        return now.plus(DEFAULT_TTL_DAYS, ChronoUnit.DAYS);
    }

    private static String requireUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        return url.strip();
    }

    private static String normalizeTarget(String target, CurationAction action) {
        if (action == CurationAction.PIN_AS_PORTAL || action == CurationAction.RENAME_TOPIC) {
            return parseUuid(target, "target topic_cluster id").toString();
        }
        return target != null && !target.isBlank() ? target.strip() : null;
    }

    private static UUID parseUuid(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            return UUID.fromString(raw.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + label + ": " + raw);
        }
    }

    private static String requireActor(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return "admin";
        }
        return createdBy.strip();
    }

    private static String stringPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private void requireTopicCluster(UUID clusterId) {
        TopicClusterEntity cluster = topicClusterRepository
                .findById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("unknown topic cluster: " + clusterId));
        if (!cluster.isApproved()) {
            throw new IllegalArgumentException("topic cluster must be approved before pin_as_portal: " + clusterId);
        }
    }

    public record CreateCurationOverrideCommand(
            String url, String action, String target, Map<String, Object> payload, String reason, Instant expiresAt, String createdBy) {}

    public record CurationOverrideView(
            UUID id,
            String url,
            String action,
            String target,
            Map<String, Object> payload,
            String reason,
            String createdBy,
            Instant createdAt,
            Instant expiresAt,
            boolean active) {
        static CurationOverrideView from(CurationOverrideEntity entity, String url) {
            Instant now = Instant.now();
            return new CurationOverrideView(
                    entity.getId(),
                    url,
                    entity.getAction(),
                    entity.getTarget(),
                    Map.copyOf(entity.getPayload()),
                    entity.getReason(),
                    entity.getCreatedBy(),
                    entity.getCreatedAt(),
                    entity.getExpiresAt(),
                    entity.isActive(now));
        }
    }
}
