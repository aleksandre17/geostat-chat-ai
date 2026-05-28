package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.domain.catalog.LinkedExplanation;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseGroundingEnforcerTest {

    private final ResponseGroundingEnforcer enforcer =
            new ResponseGroundingEnforcer(new ExplanationGroundingVerifier(20, 180));

    @Test
    void stripsUngroundedRagExplanation() {
        LinkCard rag = LinkCard.fromRag("https://www.geostat.ge/ka/x", "t", "s", 0.9, "i", "#fff");
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk("d", "https://www.geostat.ge/ka/x", "indexed passage about inflation trends", 0.9));
        List<LinkedExplanation> result = enforcer.enforce(
                List.of(new LinkedExplanation("invented text with no relation", rag)), chunks);
        assertNull(result.get(0).explanation());
        assertEquals("rag", result.get(0).link().sourceType());
    }
}
