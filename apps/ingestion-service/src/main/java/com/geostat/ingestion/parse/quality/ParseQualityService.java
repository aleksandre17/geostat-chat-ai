package com.geostat.ingestion.parse.quality;

import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig.GateDefinition;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates corpus quality gates declared in {@code ops/eval/corpus-quality-gate.yaml}.
 *
 * <p>SQL formulas and numeric thresholds are <strong>not</strong> duplicated in code —
 * they are loaded once from YAML via {@link CorpusQualityGateConfigLoader} so that owner
 * edits to the artifact change live behavior without a rebuild.
 */
@Service
@Profile("db")
public class ParseQualityService {

    private static final Logger log = LoggerFactory.getLogger(ParseQualityService.class);

    private static final String PARSED_DOCS_SQL =
            """
            SELECT COUNT(*) FROM ingestion.document
            WHERE corpus_id = :corpusId AND fetch_status = 'parsed'
            """;

    @PersistenceContext
    private EntityManager entityManager;

    private final CorpusRepository corpusRepository;
    private final CorpusQualityGateConfigLoader gateConfigLoader;

    public ParseQualityService(
            CorpusRepository corpusRepository, CorpusQualityGateConfigLoader gateConfigLoader) {
        this.corpusRepository = corpusRepository;
        this.gateConfigLoader = gateConfigLoader;
    }

    @Transactional(readOnly = true)
    public ParseQualityReport assess(String corpusName) {
        UUID corpusId = corpusRepository
                .findByName(corpusName)
                .orElseThrow(() -> new IllegalArgumentException("unknown corpus: " + corpusName))
                .getId();

        long parsedDocs = scalarLong(PARSED_DOCS_SQL, corpusId);
        List<GateDefinition> definitions = gateConfigLoader.load().gates();
        List<GateResult> results = new ArrayList<>(definitions.size());
        for (GateDefinition def : definitions) {
            results.add(evaluate(def, corpusId));
        }
        return new ParseQualityReport(corpusName, parsedDocs, results);
    }

    private GateResult evaluate(GateDefinition def, UUID corpusId) {
        try {
            double value = scalarDouble(def.sql(), corpusId);
            return new GateResult(def.id(), value, def.target().asText(), def.target().evaluate(value));
        } catch (RuntimeException e) {
            log.warn("Gate {} failed to evaluate: {}", def.id(), e.getMessage());
            return new GateResult(def.id(), Double.NaN, def.target().asText(), false);
        }
    }

    private long scalarLong(String sql, UUID corpusId) {
        Number result = (Number) entityManager
                .createNativeQuery(sql)
                .setParameter("corpusId", corpusId)
                .getSingleResult();
        return result == null ? 0L : result.longValue();
    }

    private double scalarDouble(String sql, UUID corpusId) {
        Object raw = entityManager
                .createNativeQuery(sql)
                .setParameter("corpusId", corpusId)
                .getSingleResult();
        if (raw == null) {
            return 0.0;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(raw.toString());
    }

    public record ParseQualityReport(String corpus, long parsedDocs, List<GateResult> gates) {}

    public record GateResult(String id, double value, String target, boolean passed) {}
}
