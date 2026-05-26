package com.geostat.ingestion.parse.profile;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.ingestion.parse")
public record ParseProperties(Profile profile, String configDir, String evalGatePath) {

    public ParseProperties {
        if (profile == null) {
            profile = Profile.defaults();
        }
        if (configDir == null || configDir.isBlank()) {
            configDir = "ops/config/corpus";
        }
        if (evalGatePath == null || evalGatePath.isBlank()) {
            evalGatePath = "ops/eval/corpus-quality-gate.yaml";
        }
    }

    public record Profile(boolean enabled) {
        public static Profile defaults() {
            return new Profile(false);
        }
    }
}
