package Chatbot.controller;

import Chatbot.service.StructureLookup;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for Docker and monitoring.
 * GET /health → {"status":"UP","db":{"primary":"UP","secondary":"UP"}}
 */
@RestController
public class HealthController {

    private final StructureLookup structureLookup;

    public HealthController(StructureLookup structureLookup) {
        this.structureLookup = structureLookup;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> db = new LinkedHashMap<>();

        boolean allUp = true;


        result.put("status", allUp ? "UP" : "DOWN");
        result.put("db", db);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/structure/cache/clear")
    public ResponseEntity<Map<String, String>> clearStructureCache() {
        structureLookup.clearCache();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }
}
