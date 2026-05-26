package com.geostat.chat.infrastructure.query;

import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.domain.query.QueryExpander;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Terminology overlay + optional LLM paraphrases (RAG-U07e). */
@Component
@Primary
public class RoutingQueryExpander implements QueryExpander {

    private final QueryUnderstandingProperties properties;
    private final YamlTerminologyQueryExpander terminologyQueryExpander;
    private final GeminiQueryExpander geminiQueryExpander;

    public RoutingQueryExpander(
            QueryUnderstandingProperties properties,
            YamlTerminologyQueryExpander terminologyQueryExpander,
            GeminiQueryExpander geminiQueryExpander) {
        this.properties = properties;
        this.terminologyQueryExpander = terminologyQueryExpander;
        this.geminiQueryExpander = geminiQueryExpander;
    }

    @Override
    public List<String> expand(String normalized, String locale) {
        Set<String> merged = new LinkedHashSet<>(terminologyQueryExpander.expand(normalized, locale));
        if (properties.isEnabled() && properties.isLlmExpandEnabled()) {
            merged.addAll(geminiQueryExpander.expand(normalized, locale));
        }
        return List.copyOf(new ArrayList<>(merged));
    }
}
