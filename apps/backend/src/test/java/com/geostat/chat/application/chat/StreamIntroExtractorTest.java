package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StreamIntroExtractorTest {

    @Test
    void extractIntro_fromPartialJson() {
        String partial = "{\"intro\":\"მოგესალმებით";
        assertEquals("მოგესალმებით", StreamIntroExtractor.extractIntro(partial));
    }

    @Test
    void extractIntro_completeJson() {
        String json = "{\"intro\":\"Hello world\",\"items\":[]}";
        assertEquals("Hello world", StreamIntroExtractor.extractIntro(json));
    }

    @Test
    void extractIntro_noIntroYet_returnsEmpty() {
        assertEquals("", StreamIntroExtractor.extractIntro("{\"items\":["));
    }
}
