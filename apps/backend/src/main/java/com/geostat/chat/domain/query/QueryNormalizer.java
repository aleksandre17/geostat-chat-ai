package com.geostat.chat.domain.query;

public interface QueryNormalizer {

    String normalize(String text, String locale);
}
