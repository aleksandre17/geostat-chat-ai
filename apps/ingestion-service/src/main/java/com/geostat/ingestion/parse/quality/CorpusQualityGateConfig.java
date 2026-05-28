package com.geostat.ingestion.parse.quality;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numeric acceptance contract loaded from {@code ops/eval/corpus-quality-gate.yaml}.
 *
 * <p>Thresholds and blocking phases are declarative in YAML; metric computation is
 * performed by {@link com.geostat.platform.quality.QualityMetric} beans keyed by gate id.
 */
public record CorpusQualityGateConfig(
        String corpus, List<GateDefinition> gates, ThresholdsConfig thresholds) {

    public CorpusQualityGateConfig {
        corpus = corpus == null ? "" : corpus;
        gates = gates == null ? List.of() : List.copyOf(gates);
    }

    public record ThresholdsConfig(Integer minContentLength, Double maxBoilerplateRatio) {}

    public record GateDefinition(
            String id,
            String description,
            GateTarget target,
            List<String> blocks,
            Double currentBaseline) {

        public GateDefinition {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    public record GateTarget(String operator, double threshold) {

        private static final Pattern SPEC = Pattern.compile("\\s*(<=|>=|<|>|=)\\s*([0-9.eE+-]+)\\s*");

        public static GateTarget parse(String spec) {
            if (spec == null || spec.isBlank()) {
                throw new IllegalArgumentException("gate target must not be blank");
            }
            Matcher matcher = SPEC.matcher(spec.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("invalid gate target: " + spec);
            }
            return new GateTarget(matcher.group(1), Double.parseDouble(matcher.group(2)));
        }

        public boolean evaluate(double value) {
            return switch (operator) {
                case "<=" -> value <= threshold;
                case ">=" -> value >= threshold;
                case "<" -> value < threshold;
                case ">" -> value > threshold;
                case "=" -> Math.abs(value - threshold) < 1.0e-9;
                default -> false;
            };
        }

        public String asText() {
            return operator + " " + threshold;
        }
    }
}
