package com.geostat.ingestion.api;

import com.geostat.ingestion.curation.CurationOverlayService;
import com.geostat.ingestion.curation.CurationOverlayService.CreateCurationOverrideCommand;
import com.geostat.ingestion.curation.CurationOverlayService.CurationOverrideView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("db")
@RequestMapping("/api/v1/ingestion/curation")
public class CurationController {

    private final CurationOverlayService curationOverlayService;

    public CurationController(CurationOverlayService curationOverlayService) {
        this.curationOverlayService = curationOverlayService;
    }

    /** RAG-U05 — list active curation overrides (budget ≤50, TTL-aware). */
    @GetMapping("/overrides")
    public List<CurationOverrideView> listOverrides() {
        return curationOverlayService.listActive();
    }

    /** RAG-U05 — create or upsert override; reason mandatory; default TTL 90d. */
    @PostMapping("/overrides")
    public CurationOverrideView createOverride(@RequestBody CreateCurationOverrideRequest request) {
        try {
            return curationOverlayService.create(request.toCommand());
        } catch (IllegalArgumentException e) {
            throw toHttpException(e);
        }
    }

    @DeleteMapping("/overrides/{id}")
    public void deleteOverride(@PathVariable UUID id) {
        try {
            curationOverlayService.delete(id);
        } catch (IllegalArgumentException e) {
            throw toHttpException(e);
        }
    }

    private static ResponseStatusException toHttpException(IllegalArgumentException e) {
        String message = e.getMessage() != null ? e.getMessage() : "invalid request";
        if (message.startsWith("unknown")) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
        if (message.contains("budget exceeded") || message.contains("mandatory") || message.contains("required")) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record CreateCurationOverrideRequest(
            String url,
            String action,
            String target,
            Map<String, Object> payload,
            String reason,
            Instant expiresAt,
            String createdBy) {
        CreateCurationOverrideCommand toCommand() {
            return new CreateCurationOverrideCommand(url, action, target, payload, reason, expiresAt, createdBy);
        }
    }
}
