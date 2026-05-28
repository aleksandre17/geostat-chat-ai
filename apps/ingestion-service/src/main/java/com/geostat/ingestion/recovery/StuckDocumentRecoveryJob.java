package com.geostat.ingestion.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
public class StuckDocumentRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(StuckDocumentRecoveryJob.class);

    private final JdbcTemplate jdbcTemplate;

    public StuckDocumentRecoveryJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Documents stuck in fetch_status='parsing' for &gt; 30 minutes = pipeline crashed mid-flight.
     * Reset to 'pending' for re-crawl.
     *
     * <p>Scheduled: every 30 minutes.
     */
    @Scheduled(fixedDelayString = "${ingestion.recovery.interval-ms:1800000}")
    public void recoverStuckDocuments() {
        int count = jdbcTemplate.update(
                """
                UPDATE ingestion.document
                SET fetch_status = 'pending',
                    updated_at   = now()
                WHERE fetch_status = 'parsing'
                  AND updated_at < now() - INTERVAL '30 minutes'
                """);
        if (count > 0) {
            log.warn("[stuck-recovery] Reset {} documents from 'parsing' to 'pending'", count);
        }
    }
}
