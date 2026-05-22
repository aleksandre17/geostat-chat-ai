package com.geostat.ingestion.api;

import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.repository.CorpusRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("db")
@RequestMapping("/api/v1/ingestion/corpora")
public class CorpusController {

    private final CorpusRepository corpusRepository;

    public CorpusController(CorpusRepository corpusRepository) {
        this.corpusRepository = corpusRepository;
    }

    @GetMapping
    public List<CorpusSummary> list() {
        return corpusRepository.findAll().stream()
                .map(CorpusSummary::from)
                .toList();
    }

    public record CorpusSummary(String name, List<String> seedUrls, String status) {
        static CorpusSummary from(CorpusEntity entity) {
            return new CorpusSummary(
                    entity.getName(),
                    entity.getSeedUrls(),
                    entity.getStatus().name());
        }
    }
}
