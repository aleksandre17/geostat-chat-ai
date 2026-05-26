package com.geostat.chat.domain.query;

public interface IntentClassifier {

    QueryIntentKind classify(String message, String normalized, String locale);
}
