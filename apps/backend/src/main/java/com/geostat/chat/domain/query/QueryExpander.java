package com.geostat.chat.domain.query;

import java.util.List;

public interface QueryExpander {

    List<String> expand(String normalized, String locale);
}
