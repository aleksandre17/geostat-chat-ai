package com.geostat.platform.text;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisplayDescriptionHeuristicsTest {

    @Test
    void detectsGeostatLegalOgDescription() {
        String meta =
                "საჯარო სამართლის იურიდიული პირი - საქსტატი წარმოადგენს სტატისტიკის წარმოებისა და სტატისტიკური ინფორმაციის გავრცელების მიზნით შექმნილ დაწესებულებას.";
        assertTrue(DisplayDescriptionHeuristics.isBoilerplate(meta));
    }

    @Test
    void detectsAccessibilityBanner() {
        assertTrue(DisplayDescriptionHeuristics.isBoilerplate(
                "× ვებგვერდის ადაპტირებული ვერსია UNDP"));
    }

    @Test
    void acceptsTopicSpecificDescription() {
        assertFalse(DisplayDescriptionHeuristics.isBoilerplate(
                "ოფიციალური ინფლაციის სტატისტიკა საქართველოში."));
    }
}
