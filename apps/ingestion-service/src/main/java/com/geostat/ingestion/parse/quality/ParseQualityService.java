package com.geostat.ingestion.parse.quality;

import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig.GateDefinition;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import com.geostat.platform.quality.QualityMetric;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates corpus quality gates declared in {@code ops/eval/corpus-quality-gate.yaml}.
 *
 * <p>Thresholds and blocking phases are loaded from YAML; metric values are computed by
 * {@link QualityMetric} beans keyed by gate id.
 */
@Service
@Profile("db")
public class ParseQualityService {

    private static final Logger log = LoggerFactory.getLogger(ParseQualityService.class);

    private static final String PARSED_DOCS_SQL =
            """
            SELECT COUNT(*) FROM ingestion.document
            WHERE corpus_id = ? AND fetch_status = 'parsed'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CorpusRepository corpusRepository;
    private final CorpusQualityGateConfigLoader gateConfigLoader;
    private final Map<String, QualityMetric> metricById;

    public ParseQualityService(
            JdbcTemplate jdbcTemplate,
            CorpusRepository corpusRepository,
            CorpusQualityGateConfigLoader gateConfigLoader,
            List<QualityMetric> metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.corpusRepository = corpusRepository;
        this.gateConfigLoader = gateConfigLoader;
        this.metricById = metrics.stream().collect(Collectors.toMap(QualityMetric::id, Function.identity()));
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
        QualityMetric metric = metricById.get(def.id());
        if (metric == null) {
            log.warn("No QualityMetric bean found for gate id '{}' — skipping", def.id());
            return new GateResult(def.id(), Double.NaN, def.target().asText(), false);
        }
        try {
            double value = metric.compute(corpusId);
            return new GateResult(def.id(), value, def.target().asText(), def.target().evaluate(value));
        } catch (RuntimeException e) {
            log.warn("Gate {} failed to evaluate: {}", def.id(), e.getMessage());
            return new GateResult(def.id(), Double.NaN, def.target().asText(), false);
        }
    }

    private long scalarLong(String sql, UUID corpusId) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, corpusId);
        return result == null ? 0L : result;
    }

    public record ParseQualityReport(String corpus, long parsedDocs, List<GateResult> gates) {}

    public record GateResult(String id, double value, String target, boolean passed) {}
}
