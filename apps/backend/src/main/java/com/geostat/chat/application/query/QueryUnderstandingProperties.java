package com.geostat.chat.application.query;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.chat.query-understanding")
public class QueryUnderstandingProperties {

    /** Master switch — default off until eval baseline (RAG-U12 gate). */
    private boolean enabled = false;

    /** RAG-U07c — Gemini intent classification (requires enabled=true). */
    private boolean geminiIntentEnabled = false;

    /** RAG-U07a — SymSpell typo correction (requires enabled=true). */
    private boolean spellFixEnabled = false;

    /** RAG-U07d — Gemini entity extraction (requires enabled=true). Heuristic always runs. */
    private boolean geminiEntityEnabled = false;

    /** RAG-U07e — LLM query paraphrase expansion (requires enabled=true). Terminology overlay always runs. */
    private boolean llmExpandEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGeminiIntentEnabled() {
        return geminiIntentEnabled;
    }

    public void setGeminiIntentEnabled(boolean geminiIntentEnabled) {
        this.geminiIntentEnabled = geminiIntentEnabled;
    }

    public boolean isSpellFixEnabled() {
        return spellFixEnabled;
    }

    public void setSpellFixEnabled(boolean spellFixEnabled) {
        this.spellFixEnabled = spellFixEnabled;
    }

    public boolean isGeminiEntityEnabled() {
        return geminiEntityEnabled;
    }

    public void setGeminiEntityEnabled(boolean geminiEntityEnabled) {
        this.geminiEntityEnabled = geminiEntityEnabled;
    }

    public boolean isLlmExpandEnabled() {
        return llmExpandEnabled;
    }

    public void setLlmExpandEnabled(boolean llmExpandEnabled) {
        this.llmExpandEnabled = llmExpandEnabled;
    }
}
