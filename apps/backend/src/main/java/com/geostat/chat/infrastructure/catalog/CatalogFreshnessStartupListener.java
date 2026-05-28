package com.geostat.chat.infrastructure.catalog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "geostat.chat.catalog", name = "source", havingValue = "derived")
public class CatalogFreshnessStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private final CatalogMvFreshnessChecker freshnessChecker;

    public CatalogFreshnessStartupListener(CatalogMvFreshnessChecker freshnessChecker) {
        this.freshnessChecker = freshnessChecker;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        freshnessChecker.checkFreshness();
    }
}
