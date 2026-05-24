package com.geostat.chat.application.chat;

/** Encodes a completed chat turn for SSE wire format. */
public interface ChatCompleteEncoder {

    String encodeComplete(ChatResult result);
}
