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

    static final String INTENT_CLASSIFICATION = """
            {
              "type": "object",
              "properties": {
                "intent": {
                  "type": "string",
                  "enum": ["factual", "lookup", "compare", "definition", "latest", "navigation", "smalltalk"]
                }
              },
              "required": ["intent"]
            }
            """;

    static final String ENTITY_EXTRACTION = """
            {
              "type": "object",
              "properties": {
                "entities": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "type": {"type": "string"},
                      "value": {"type": "string"},
                      "normalizedForm": {"type": "string"},
                      "confidence": {"type": "number"}
                    },
                    "required": ["type", "value"]
                  }
                }
              },
              "required": ["entities"]
            }
            """;

    static final String QUERY_EXPANSION = """
            {
              "type": "object",
              "properties": {
                "paraphrases": {
                  "type": "array",
                  "items": {"type": "string"}
                }
              },
              "required": ["paraphrases"]
            }
            """;

    private GeminiJsonSchemas() {}
}
