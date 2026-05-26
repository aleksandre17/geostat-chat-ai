package com.geostat.ingestion.parse.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.platform.parse.BoilerplateMarkers;
import com.geostat.platform.parse.CorpusPolicyV2;
import com.geostat.platform.parse.ParseProfile;
import com.geostat.platform.parse.SubdomainMode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Loads corpus policy + parse profile YAML from ops/config/corpus (consumer-only). */
@Component
public class CorpusConfigurationLoader {

    private static final Logger log = LoggerFactory.getLogger(CorpusConfigurationLoader.class);

    private final Path configDir;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, ParseProfile> parseProfiles = new ConcurrentHashMap<>();
    private final Map<String, CorpusPolicyV2> corpusPolicies = new ConcurrentHashMap<>();

    public CorpusConfigurationLoader(ParseProperties properties) {
        this.configDir = resolveConfigDir(properties.configDir());
    }

    public ParseProfile parseProfileFor(String corpusName) {
        return parseProfiles.computeIfAbsent(corpusName, this::loadParseProfile);
    }

    public CorpusPolicyV2 policyFor(String corpusName) {
        return corpusPolicies.computeIfAbsent(corpusName, this::loadCorpusPolicy);
    }

    private ParseProfile loadParseProfile(String corpusName) {
        Path file = configDir.resolve(corpusName + "-parse.yaml");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                ParseProfileYaml yaml = yamlMapper.readValue(in, ParseProfileYaml.class);
                log.info("Loaded parse profile from {}", file.toAbsolutePath());
                return yaml.toModel(corpusName);
            } catch (IOException e) {
                log.warn("Failed to load parse profile {} — using default: {}", file, e.getMessage());
            }
        }
        if (DefaultParseProfile.GEOSTAT_PORTAL.corpus().equals(corpusName)) {
            return DefaultParseProfile.GEOSTAT_PORTAL;
        }
        return DefaultParseProfile.GEOSTAT_PORTAL.forCorpus(corpusName);
    }

    private CorpusPolicyV2 loadCorpusPolicy(String corpusName) {
        Path file = configDir.resolve(corpusName + "-policy.yaml");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                CorpusPolicyYaml yaml = yamlMapper.readValue(in, CorpusPolicyYaml.class);
                log.info("Loaded corpus policy from {}", file.toAbsolutePath());
                return yaml.toModel(corpusName);
            } catch (IOException e) {
                log.warn("Failed to load corpus policy {} — using permissive fallback: {}", file, e.getMessage());
            }
        }
        return permissivePolicy(corpusName);
    }

    private static CorpusPolicyV2 permissivePolicy(String corpusName) {
        return new CorpusPolicyV2(
                corpusName,
                List.of(),
                List.of(),
                List.of("www.geostat.ge", "geostat.ge"),
                SubdomainMode.all,
                List.of(),
                List.of(),
                List.of());
    }

    private static Path resolveConfigDir(String configured) {
        Path path = Path.of(configured);
        if (path.isAbsolute() && Files.isDirectory(path)) {
            return path;
        }
        Path fromUserDir = Path.of(System.getProperty("user.dir", ".")).resolve(path);
        if (Files.isDirectory(fromUserDir)) {
            return fromUserDir.normalize();
        }
        Path parent = fromUserDir.getParent();
        if (parent != null) {
            Path fromParent = parent.resolve(path);
            if (Files.isDirectory(fromParent)) {
                return fromParent.normalize();
            }
        }
        return fromUserDir.normalize();
    }

    @SuppressWarnings("unused")
    static final class ParseProfileYaml {
        public String corpus;
        public List<String> rootSelectors;
        public List<String> removeSelectors;
        public Map<String, List<String>> boilerplateMarkers;
        public Boolean stripLeading;
        public Boolean stripTrailing;
        public Boolean extractTables;
        public Boolean preserveHeadings;
        public Map<String, List<String>> language;

        ParseProfile toModel(String fallbackCorpus) {
            BoilerplateMarkers markers = new BoilerplateMarkers(
                    listOrEmpty(boilerplateMarkers, "ka"),
                    listOrEmpty(boilerplateMarkers, "en"),
                    listOrEmpty(boilerplateMarkers, "shared"),
                    listOrEmpty(boilerplateMarkers, "containsKa"),
                    listOrEmpty(boilerplateMarkers, "containsEn"),
                    listOrEmpty(boilerplateMarkers, "containsShared"));
            List<String> inferFrom = language == null ? List.of() : language.getOrDefault("inferFrom", List.of());
            return new ParseProfile(
                    corpus == null ? fallbackCorpus : corpus,
                    rootSelectors,
                    removeSelectors,
                    markers,
                    stripLeading == null || stripLeading,
                    stripTrailing == null || stripTrailing,
                    extractTables == null || extractTables,
                    preserveHeadings == null || preserveHeadings,
                    inferFrom);
        }
    }

    @SuppressWarnings("unused")
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CorpusPolicyYaml {
        public String corpus;
        public List<String> seeds;
        public List<String> curatedUrls;
        public HostPolicyYaml hostPolicy;
        public List<String> excludePatterns;
        public List<String> includePatterns;

        CorpusPolicyV2 toModel(String fallbackCorpus) {
            List<String> allowed = hostPolicy == null ? List.of() : hostPolicy.allowedHosts;
            SubdomainMode mode = hostPolicy == null || hostPolicy.subdomains == null
                    ? SubdomainMode.all
                    : SubdomainMode.fromYaml(hostPolicy.subdomains.mode);
            List<String> allow = hostPolicy == null || hostPolicy.subdomains == null
                    ? List.of()
                    : hostPolicy.subdomains.allow;
            return new CorpusPolicyV2(
                    corpus == null ? fallbackCorpus : corpus,
                    seeds,
                    curatedUrls,
                    allowed,
                    mode,
                    allow,
                    excludePatterns,
                    includePatterns);
        }
    }

    @SuppressWarnings("unused")
    static final class HostPolicyYaml {
        public List<String> allowedHosts;
        public SubdomainsYaml subdomains;
    }

    @SuppressWarnings("unused")
    static final class SubdomainsYaml {
        public String mode;
        public List<String> allow;
    }

    private static List<String> listOrEmpty(Map<String, List<String>> map, String key) {
        if (map == null) {
            return List.of();
        }
        List<String> values = map.get(key);
        return values == null ? List.of() : values;
    }
}
