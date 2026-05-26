package com.geostat.ingestion.api;

import com.geostat.ingestion.catalog.topic.TopicClusterAdminService;
import com.geostat.ingestion.catalog.topic.TopicClusterAdminService.TopicClusterView;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("db")
@RequestMapping("/api/v1/ingestion")
public class TopicClusterController {

    private final TopicClusterAdminService topicClusterAdminService;

    public TopicClusterController(TopicClusterAdminService topicClusterAdminService) {
        this.topicClusterAdminService = topicClusterAdminService;
    }

    /** RAG-U02 — list mined topic clusters (approved + pending) for owner review. */
    @GetMapping("/corpora/{name}/topic-clusters")
    public List<TopicClusterView> listTopicClusters(@PathVariable String name) {
        return topicClusterAdminService.listForCorpus(name);
    }

    /** RAG-U02 — approve cluster labels so MVs include them (`approved=true` gate). */
    @PostMapping("/topic-clusters/{id}:approve")
    public TopicClusterView approve(
            @PathVariable UUID id, @RequestParam(required = false) String approvedBy) {
        try {
            return topicClusterAdminService.approve(id, approvedBy);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/topic-clusters/{id}:unapprove")
    public TopicClusterView unapprove(@PathVariable UUID id) {
        try {
            return topicClusterAdminService.unapprove(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
