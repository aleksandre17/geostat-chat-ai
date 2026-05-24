package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CorpusUrlWhitelistTest {

    @Test
    void fromChunks_normalizesUrls() {
        Set<String> allowed = CorpusUrlWhitelist.fromChunks(List.of(
                new RetrievedChunk("c1", "https://www.geostat.ge/ka/", "text", 0.9)));
        assertTrue(CorpusUrlWhitelist.contains(allowed, "https://www.geostat.ge/ka"));
    }
}
