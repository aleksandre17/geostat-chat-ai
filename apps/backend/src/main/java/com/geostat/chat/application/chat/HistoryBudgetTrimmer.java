package com.geostat.chat.application.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.ai.chat.messages.Message;

/** Trims conversation history deque to configured max size. */
public final class HistoryBudgetTrimmer {

    private HistoryBudgetTrimmer() {}

    public static Deque<Message> trim(Deque<Message> history, int maxMessages) {
        if (history == null || history.size() <= maxMessages) {
            return history;
        }
        Deque<Message> trimmed = new ArrayDeque<>(history);
        while (trimmed.size() > maxMessages) {
            trimmed.pollFirst();
        }
        return trimmed;
    }
}
