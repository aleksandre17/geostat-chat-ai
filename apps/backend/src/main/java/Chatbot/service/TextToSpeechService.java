package Chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Service
public class TextToSpeechService {

    private static final Logger logger = LoggerFactory.getLogger(TextToSpeechService.class);

    @Value("${spring.ai.elevenlabs.api-key}")
    private String apiKey;

    private final WebClient webClient;

    public TextToSpeechService(WebClient.Builder webClientBuilder) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))  // 10MB
                .build();

        this.webClient = webClientBuilder
                .baseUrl("https://api.elevenlabs.io/v1")
                .exchangeStrategies(strategies)  // Add this!
                .build();
    }

    private String getVoiceIdForLanguage(String language) {
        return language.startsWith("ka")
                ? "Z3R5wn05IrDiVCyEkUrK"  // Arabella (Georgian)
                : "21m00Tcm4TlvDq8ikWAM"; // Rachel (English)
    }

    public byte[] synthesizeSpeech(String text, String language) {
        if (text.length() > 5000) {
            logger.error("Text too long: {} characters (max 5000)", text.length());
            throw new IllegalArgumentException("Text exceeds maximum length of 5000 characters");
        }

        try {
            String voiceId = getVoiceIdForLanguage(language);
            logger.info("Synthesizing with voice: {}, language: {}, text length: {}",
                    voiceId, language, text.length());

            byte[] result = webClient.post()
                    .uri("/text-to-speech/{voice_id}", voiceId)
                    .header("xi-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of(
                            "text", text,
                            "model_id", "eleven_v3"
                    ))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (result == null || result.length == 0) {
                throw new RuntimeException("Empty audio response from ElevenLabs");
            }

            logger.info("Successfully generated audio: {} bytes", result.length);
            return result;

        } catch (Exception e) {
            logger.error("TTS synthesis failed", e);
            throw new RuntimeException("Failed to synthesize speech: " + e.getMessage(), e);
        }
    }
}
