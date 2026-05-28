package com.geostat.chat.application.chat;

import static org.junit.jupiter.api.Assertions.*;

import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplanationGroundingVerifierTest {

    private final ExplanationGroundingVerifier verifier = new ExplanationGroundingVerifier(20, 180);

    @Test
    void isGrounded_whenExplanationCitesPassage() {
        String passage = "სამომხმარებლო ფასების ინდექსი იზომება ყოველთვიურად.";
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk("d1", "https://www.geostat.ge/ka/cpi", passage, 0.9));
        boolean grounded = verifier.isGrounded(
                List.of(),
                chunks,
                "სამომხმარებლო ფასების ინდექსი იზომება ყოველთვიურად.");
        assertTrue(grounded);
    }

    @Test
    void isGrounded_falseWhenNoOverlap() {
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk("d1", "https://www.geostat.ge/ka/cpi", "unrelated corpus text here", 0.9));
        assertFalse(verifier.isGrounded(List.of(), chunks, "completely different answer text"));
    }

    @Test
    void explanationCitesChunk_matchesByUrl() {
        String passage = "დეპარტამენტი უზრუნველყოფს სტატისტიკურ მონაცემებს.";
        List<RetrievedChunk> chunks = List.of(
                new RetrievedChunk("d1", "https://www.geostat.ge/ka/team", passage, 0.8));
        assertTrue(verifier.explanationCitesChunk(
                "დეპარტამენტი უზრუნველყოფს სტატისტიკურ", "https://www.geostat.ge/ka/team", chunks));
    }
}
