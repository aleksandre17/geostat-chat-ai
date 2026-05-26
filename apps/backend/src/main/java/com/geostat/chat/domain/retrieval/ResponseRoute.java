package com.geostat.chat.domain.retrieval;

/**
 * Response routing strategy based on retrieval confidence.
 */
public enum ResponseRoute {
    /** HIGH confidence — answer with citations */
    ANSWER_WITH_CITATIONS,
    /** MEDIUM confidence — answer with "see also" suggestions */
    ANSWER_WITH_SUGGESTIONS,
    /** LOW confidence — clarification needed */
    CLARIFY,
    /** NONE — refuse and suggest topics */
    REFUSE_SUGGEST_TOPICS
}
