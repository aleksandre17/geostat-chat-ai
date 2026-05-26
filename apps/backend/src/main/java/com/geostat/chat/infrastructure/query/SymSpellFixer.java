package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.SpellFixer;
import io.gitlab.rxp90.jsymspell.SymSpell;
import io.gitlab.rxp90.jsymspell.SymSpellBuilder;
import io.gitlab.rxp90.jsymspell.Verbosity;
import io.gitlab.rxp90.jsymspell.api.SuggestItem;
import io.gitlab.rxp90.jsymspell.exceptions.NotInitializedException;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** RAG-U07a — SymSpell token correction using manifest-driven geostat lexicon. */
@Component
public class SymSpellFixer implements SpellFixer {

    private static final Logger log = LoggerFactory.getLogger(SymSpellFixer.class);
    private static final Pattern LATIN_TOKEN = Pattern.compile("[A-Za-z][A-Za-z'-]*");
    private static final int MAX_EDIT_DISTANCE = 2;

    private final SpellDictionaryLoader dictionaryLoader;
    private SymSpell symSpell;

    public SymSpellFixer(SpellDictionaryLoader dictionaryLoader) {
        this.dictionaryLoader = dictionaryLoader;
    }

    @PostConstruct
    void init() {
        if (dictionaryLoader.unigrams().isEmpty()) {
            log.warn("SymSpell lexicon empty — spell fix will pass through");
            return;
        }
        symSpell = new SymSpellBuilder()
                .setUnigramLexicon(dictionaryLoader.unigrams())
                .setMaxDictionaryEditDistance(MAX_EDIT_DISTANCE)
                .createSymSpell();
    }

    @Override
    public String fix(String text, String locale) {
        if (text == null || text.isBlank() || symSpell == null) {
            return text == null ? "" : text;
        }
        String[] parts = text.split("\\s+");
        StringBuilder fixed = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                fixed.append(' ');
            }
            fixed.append(fixToken(parts[i]));
        }
        return fixed.toString();
    }

    private String fixToken(String token) {
        if (!LATIN_TOKEN.matcher(token).matches()) {
            return token;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if (dictionaryLoader.unigrams().containsKey(lower)) {
            return preserveCase(token, lower);
        }
        try {
            List<SuggestItem> suggestions = symSpell.lookup(lower, Verbosity.TOP, false);
            if (suggestions.isEmpty() || suggestions.get(0).getEditDistance() == 0) {
                return token;
            }
            return preserveCase(token, suggestions.get(0).getSuggestion());
        } catch (NotInitializedException e) {
            return token;
        }
    }

    private static String preserveCase(String original, String suggestion) {
        if (original.isEmpty() || suggestion.isEmpty()) {
            return suggestion;
        }
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(suggestion.charAt(0)) + suggestion.substring(1);
        }
        return suggestion;
    }
}
