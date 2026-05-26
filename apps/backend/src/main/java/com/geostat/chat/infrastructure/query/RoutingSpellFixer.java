package com.geostat.chat.infrastructure.query;

import com.geostat.chat.application.query.QueryUnderstandingProperties;
import com.geostat.chat.domain.query.SpellFixer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RoutingSpellFixer implements SpellFixer {

    private final QueryUnderstandingProperties properties;
    private final YamlQueryTypoCorrector yamlQueryTypoCorrector;
    private final SymSpellFixer symSpellFixer;

    public RoutingSpellFixer(
            QueryUnderstandingProperties properties,
            YamlQueryTypoCorrector yamlQueryTypoCorrector,
            SymSpellFixer symSpellFixer) {
        this.properties = properties;
        this.yamlQueryTypoCorrector = yamlQueryTypoCorrector;
        this.symSpellFixer = symSpellFixer;
    }

    @Override
    public String fix(String text, String locale) {
        String corrected = yamlQueryTypoCorrector.fix(text, locale);
        if (properties.isEnabled() && properties.isSpellFixEnabled()) {
            return symSpellFixer.fix(corrected, locale);
        }
        return corrected;
    }
}
