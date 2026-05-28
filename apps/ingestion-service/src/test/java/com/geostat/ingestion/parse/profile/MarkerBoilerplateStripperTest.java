package com.geostat.ingestion.parse.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.platform.parse.BoilerplateMarkers;
import com.geostat.platform.parse.ParseProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkerBoilerplateStripperTest {

    private final MarkerBoilerplateStripper stripper = new MarkerBoilerplateStripper();

    private static ParseProfile profile() {
        return DefaultParseProfile.GEOSTAT_PORTAL;
    }

    @Test
    void dropsGeorgianAccessibilityParagraph() {
        String boilerplate = "ვებგვერდის ადაპტირებული ვერსია საქსტატის პორტალზე.";
        assertThat(stripper.isBoilerplateParagraph(boilerplate, profile())).isTrue();
        String input = boilerplate + "\n\nინფლაციის მაჩვენებელი 2024.";
        assertThat(stripper.stripFromBody(input, profile())).isEqualTo("ინფლაციის მაჩვენებელი 2024.");
    }

    @Test
    void dropsEnglishAccessibilityParagraph() {
        String boilerplate = "The adapted version of the website is accessible.";
        assertThat(stripper.isBoilerplateParagraph(boilerplate, profile())).isTrue();
        String input = boilerplate + "\n\nConsumer price index rose in 2024.";
        assertThat(stripper.stripFromBody(input, profile()))
                .isEqualTo("Consumer price index rose in 2024.");
    }

    @Test
    void stripsLeadingAndTrailingBoilerplateRuns() {
        ParseProfile p = new ParseProfile(
                "test",
                List.of("main"),
                List.of(),
                List.of(),
                new BoilerplateMarkers(List.of("უკან დაბრუნება"), List.of("skip to content"), List.of()),
                true,
                true,
                true,
                true,
                new ParseProfile.LanguageConfig(List.of(), null),
                null,
                null,
                List.of(),
                null,
                null);
        String input = "უკან დაბრუნება\n\nMain body text\n\nskip to content";
        assertThat(stripper.stripFromBody(input, p)).isEqualTo("Main body text");
    }
}
