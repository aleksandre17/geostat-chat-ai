package com.geostat.chat.domain.query;

public interface SpellFixer {

    String fix(String text, String locale);
}
