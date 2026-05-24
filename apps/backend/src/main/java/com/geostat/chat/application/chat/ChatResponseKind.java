package com.geostat.chat.application.chat;

/** Application-layer response classification (mapped to API {@code responseType}). */
public enum ChatResponseKind {
    answer,
    clarification,
    smalltalk,
    portal_list,
    error
}
