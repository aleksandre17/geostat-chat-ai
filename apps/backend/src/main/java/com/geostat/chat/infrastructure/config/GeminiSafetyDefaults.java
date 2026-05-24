package com.geostat.chat.infrastructure.config;

import java.util.List;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockThreshold;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmCategory;

/** Default Gemini safety thresholds for a public statistics assistant. */
public final class GeminiSafetyDefaults {

    private GeminiSafetyDefaults() {}

    public static List<GoogleGenAiSafetySetting> publicAssistant() {
        HarmBlockThreshold threshold = HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE;
        return List.of(
                setting(HarmCategory.HARM_CATEGORY_HATE_SPEECH, threshold),
                setting(HarmCategory.HARM_CATEGORY_HARASSMENT, threshold),
                setting(HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT, threshold),
                setting(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT, threshold));
    }

    private static GoogleGenAiSafetySetting setting(HarmCategory category, HarmBlockThreshold threshold) {
        GoogleGenAiSafetySetting setting = new GoogleGenAiSafetySetting();
        setting.setCategory(category);
        setting.setThreshold(threshold);
        return setting;
    }
}
