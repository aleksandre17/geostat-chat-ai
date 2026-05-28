package com.geostat.chat.application.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** GeoStat site boilerplate filters for RAG-L11 citation snippets. */
@Component
public class DisplayBoilerplate {

    private List<String> markers = List.of();

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("catalog/display-boilerplate.yaml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        BoilerplateFile file = mapper.readValue(resource.getInputStream(), BoilerplateFile.class);
        this.markers = file.markers() != null ? List.copyOf(file.markers()) : List.of();
    }

    public boolean isBoilerplate(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.startsWith("×")) {
            return true;
        }
        for (String marker : markers) {
            if (normalized.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private record BoilerplateFile(List<String> markers) {}
}
