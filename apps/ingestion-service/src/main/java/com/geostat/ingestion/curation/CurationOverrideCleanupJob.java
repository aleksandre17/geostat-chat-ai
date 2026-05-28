package com.geostat.ingestion.curation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly cleanup of expired curation overrides.
 * 7-day grace period preserves audit trail.
 * Disabled by default — enable after verifying no active integrations depend on expired rows.
 */
@Component
@Profile("db")
@ConditionalOnProperty(
        prefix = "geostat.ingestion.curation",
        name = "cleanup-enabled",
        havingValue = "true")
public class CurationOverrideCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CurationOverrideCleanupJob.class);

    private final JdbcTemplate jdbcTemplate;

    public CurationOverrideCleanupJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 3 * * SUN") // Sunday 3 AM
    public void cleanupExpiredOverrides() {
        int deleted = jdbcTemplate.update(
                """
                DELETE FROM ingestion.curation_override
                WHERE expires_at IS NOT NULL
                  AND expires_at < now() - interval '7 days'
                """);
        log.info("[curation-cleanup] Deleted {} expired curation overrides", deleted);
    }
}
