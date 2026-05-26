package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YamlQueryTypoCorrectorTest {

    private final YamlQueryTypoCorrector corrector = new YamlQueryTypoCorrector();

    @Test
    void fixesCommonExecutiveDirectorTypo() {
        corrector.load();
        String fixed = corrector.fix("არმასრულებელი დირექტორის გვერდი დალინკე", "ka");
        assertThat(fixed).contains("აღმასრულებელი").doesNotContain("არმასრულებელი");
    }
}
