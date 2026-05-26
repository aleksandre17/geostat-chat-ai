package com.geostat.chat.infrastructure.query;

import com.geostat.chat.domain.query.SpellFixer;

/** RAG-U07a pass-through when spell-fix flag is off. */
public class IdentitySpellFixer implements SpellFixer {

    @Override
    public String fix(String text, String locale) {
        return text == null ? "" : text;
    }
}
