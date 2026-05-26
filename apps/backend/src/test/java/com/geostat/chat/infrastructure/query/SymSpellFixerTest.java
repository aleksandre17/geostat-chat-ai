package com.geostat.chat.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.geostat.chat.application.query.QueryUnderstandingProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SymSpellFixerTest {

    private SymSpellFixer symSpellFixer;

    @BeforeEach
    void setUp() {
        SpellDictionaryLoader loader = new SpellDictionaryLoader() {
            @Override
            public Map<String, Long> unigrams() {
                return Map.of(
                        "inflation", 1000L,
                        "statistics", 900L,
                        "employment", 800L);
            }
        };
        symSpellFixer = new SymSpellFixer(loader);
        symSpellFixer.init();
    }

    @Test
    void fixesLatinTypo() {
        assertThat(symSpellFixer.fix("inflatin rate", "en")).isEqualTo("inflation rate");
    }

    @Test
    void preservesGeorgianTokens() {
        assertThat(symSpellFixer.fix("ინფლაცია 2024", "ka")).isEqualTo("ინფლაცია 2024");
    }

    @Test
    void routingUsesTypoCorrectorWhenSpellFixDisabled() {
        QueryUnderstandingProperties props = new QueryUnderstandingProperties();
        props.setEnabled(true);
        props.setSpellFixEnabled(false);
        YamlQueryTypoCorrector typoCorrector = new YamlQueryTypoCorrector();
        typoCorrector.load();
        RoutingSpellFixer routing = new RoutingSpellFixer(props, typoCorrector, symSpellFixer);
        assertThat(routing.fix("inflatin", "en")).isEqualTo("inflatin");
    }
}
