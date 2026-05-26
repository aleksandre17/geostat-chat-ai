package com.geostat.ingestion.parse.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.ingestion.parse.profile.ParseProperties;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig.GateDefinition;
import com.geostat.ingestion.parse.quality.CorpusQualityGateConfig.GateTarget;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loads {@link CorpusQualityGateConfig} from {@code ops/eval/corpus-quality-gate.yaml}
 * (consumer artifact, HANDOFF-P0-L1 §3 Artifact 4).
 *
 * <p>Result is cached after first successful load; missing or malformed YAML falls back to
 * an empty config so the service degrades gracefully rather than crashing.
 */
@Component
public class CorpusQualityGateConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(CorpusQualityGateConfigLoader.class);

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ParseProperties properties;
    private final AtomicReference<CorpusQualityGateConfig> cache = new AtomicReference<>();

    public CorpusQualityGateConfigLoader(ParseProperties properties) {
        this.properties = properties;
    }

    public CorpusQualityGateConfig load() {
        CorpusQualityGateConfig cached = cache.get();
        if (cached != null) {
            return cached;
        }
        CorpusQualityGateConfig fresh = readFromYaml();
        cache.compareAndSet(null, fresh);
        return cache.get();
    }

    private CorpusQualityGateConfig readFromYaml() {
        Path file = resolveGateFile(properties.evalGatePath());
        if (file == null || !Files.isRegularFile(file)) {
            log.warn("Corpus quality gate YAML not found at {} — falling back to empty config", properties.evalGatePath());
            return new CorpusQualityGateConfig("", List.of());
        }
        try (InputStream in = Files.newInputStream(file)) {
            GateConfigYaml yaml = yamlMapper.readValue(in, GateConfigYaml.class);
            CorpusQualityGateConfig config = yaml.toModel();
            log.info("Loaded {} gate definitions from {}", config.gates().size(), file.toAbsolutePath());
            return config;
        } catch (IOException e) {
            log.warn("Failed to read corpus quality gate YAML at {} — falling back: {}", file, e.getMessage());
            return new CorpusQualityGateConfig("", List.of());
        }
    }

    static Path resolveGateFile(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path direct = Path.of(configured);
        if (direct.isAbsolute()) {
            return direct;
        }
        Path userDir = Path.of(System.getProperty("user.dir", "."));
        for (Path base : candidateBases(userDir)) {
            Path candidate = base.resolve(direct).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return userDir.resolve(direct).normalize();
    }

    private static List<Path> candidateBases(Path userDir) {
        List<Path> bases = new ArrayList<>(4);
        bases.add(userDir);
        Path parent = userDir.getParent();
        if (parent != null) {
            bases.add(parent);
            Path grand = parent.getParent();
            if (grand != null) {
                bases.add(grand);
            }
        }
        return bases;
    }

    @SuppressWarnings("unused")
    static final class GateConfigYaml {
        public String corpus;
        public List<GateYaml> gates;

        CorpusQualityGateConfig toModel() {
            if (gates == null || gates.isEmpty()) {
                return new CorpusQualityGateConfig(corpus, List.of());
            }
            List<GateDefinition> defs = new ArrayList<>(gates.size());
            for (GateYaml gate : gates) {
                if (gate == null || gate.id == null || gate.id.isBlank()) {
                    continue;
                }
                String sql = gate.metric == null ? null : gate.metric.get("sql");
                if (sql == null || sql.isBlank()) {
                    continue;
                }
                GateTarget target;
                try {
                    target = GateTarget.parse(gate.target);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                defs.add(new GateDefinition(
                        gate.id,
                        gate.description,
                        sql.trim(),
                        target,
                        gate.blocks,
                        gate.currentBaseline));
            }
            return new CorpusQualityGateConfig(corpus, defs);
        }
    }

    @SuppressWarnings("unused")
    static final class GateYaml {
        public String id;
        public String description;
        public Map<String, String> metric;
        public String target;
        public Double currentBaseline;
        public List<String> blocks;
    }
}
