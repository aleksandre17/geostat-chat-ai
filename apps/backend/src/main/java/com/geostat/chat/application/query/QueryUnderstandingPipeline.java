package com.geostat.chat.application.query;

import com.geostat.chat.domain.query.AnalyzedQuery;
import com.geostat.chat.domain.query.IntentClassifier;
import com.geostat.chat.domain.query.QueryEntityExtractor;
import com.geostat.chat.domain.query.QueryExpander;
import com.geostat.chat.domain.query.QueryIntentKind;
import com.geostat.chat.domain.query.QueryNormalizer;
import com.geostat.chat.domain.query.SpellFixer;
import com.geostat.platform.enrichment.Entity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** RAG-U07 orchestrator — stages 1..5 (SpellFix → Normalize → Intent → Entity → Expand). */
@Component
public class QueryUnderstandingPipeline {

    private final SpellFixer spellFixer;
    private final QueryNormalizer queryNormalizer;
    private final IntentClassifier intentClassifier;
    private final QueryEntityExtractor queryEntityExtractor;
    private final QueryExpander queryExpander;

    public QueryUnderstandingPipeline(
            SpellFixer spellFixer,
            QueryNormalizer queryNormalizer,
            IntentClassifier intentClassifier,
            QueryEntityExtractor queryEntityExtractor,
            QueryExpander queryExpander) {
        this.spellFixer = spellFixer;
        this.queryNormalizer = queryNormalizer;
        this.intentClassifier = intentClassifier;
        this.queryEntityExtractor = queryEntityExtractor;
        this.queryExpander = queryExpander;
    }

    public AnalyzedQuery analyze(String message, String locale) {
        String resolvedLocale = resolveLocale(locale);
        String original = message == null ? "" : message.strip();
        String spellFixed = spellFixer.fix(original, resolvedLocale);
        String normalized = queryNormalizer.normalize(spellFixed, resolvedLocale);
        QueryIntentKind intent = intentClassifier.classify(spellFixed, normalized, resolvedLocale);
        List<Entity> entities = queryEntityExtractor.extract(original, normalized, resolvedLocale);
        List<String> expansions = queryExpander.expand(normalized, resolvedLocale);
        String retrievalText = composeRetrievalText(normalized, entities, expansions);
        return new AnalyzedQuery(
                original, spellFixed, normalized, retrievalText, intent, resolvedLocale, entities, expansions);
    }

    private static String composeRetrievalText(String normalized, List<Entity> entities, List<String> expansions) {
        if (normalized.isBlank()) {
            return "";
        }
        Set<String> parts = new LinkedHashSet<>();
        parts.add(normalized);
        for (Entity entity : entities) {
            if (entity.normalizedForm() != null && !entity.normalizedForm().isBlank()) {
                parts.add(entity.normalizedForm().strip());
            }
        }
        parts.addAll(expansions);
        return String.join(" ", new ArrayList<>(parts));
    }

    private static String resolveLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "ka";
        }
        return locale.split("-")[0].toLowerCase();
    }
}
