package com.geostat.chat.infrastructure.prompt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PromptCatalogLoaderTest {

    @Test
    void loadsMainPromptWithConceptType() throws Exception {
        PromptCatalogLoader loader = new PromptCatalogLoader();
        loader.load();
        assertTrue(loader.version() >= 1);
        assertNotNull(loader.contentHash());
        assertEquals(64, loader.contentHash().length());
        assertTrue(loader.main(true).contains("ტიპი 1: CONCEPT"));
        assertTrue(loader.main(false).contains("Type 1: CONCEPT"));
        assertTrue(loader.main(false).contains("Unobserved economy"));
        assertFalse(loader.main(false).contains("correct term is \"დაუკვირვებადი"));
    }
}
