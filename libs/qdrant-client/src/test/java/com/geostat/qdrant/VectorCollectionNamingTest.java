package com.geostat.qdrant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VectorCollectionNamingTest {

    @Test
    void sanitizesCorpusName() {
        assertEquals("geostat-portal", VectorCollectionNaming.collectionForName("Geostat Portal"));
        assertEquals("geostat-default", VectorCollectionNaming.collectionForName(""));
    }
}
