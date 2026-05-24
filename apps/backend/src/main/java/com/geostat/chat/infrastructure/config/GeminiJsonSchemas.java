package com.geostat.chat.infrastructure.config;

/** Gemini responseSchema JSON strings for structured output. */
final class GeminiJsonSchemas {

    static final String MAIN_RESPONSE = """
            {
              "type": "object",
              "properties": {
                "intro": {"type": "string"},
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "url": {"type": "string"},
                      "explanation": {"type": "string"}
                    },
                    "required": ["url", "explanation"]
                  }
                }
              },
              "required": ["intro", "items"]
            }
            """;

    static final String CLARIFICATION_RESPONSE = """
            {
              "type": "object",
              "properties": {
                "intro": {"type": "string"},
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "url": {"type": "string"},
                      "title": {"type": "string"},
                      "explanation": {"type": "string"}
                    },
                    "required": ["url"]
                  }
                }
              },
              "required": ["intro", "items"]
            }
            """;

    private GeminiJsonSchemas() {}
}
