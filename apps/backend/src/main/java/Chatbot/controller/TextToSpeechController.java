package Chatbot.controller;

import Chatbot.service.TextToSpeechService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/tts")
public class TextToSpeechController {

    private static final Logger logger = LoggerFactory.getLogger(TextToSpeechController.class);
    private final TextToSpeechService textToSpeechService;

    public TextToSpeechController(TextToSpeechService textToSpeechService) {
        this.textToSpeechService = textToSpeechService;
    }

    @PostMapping("/synthesize")
    public ResponseEntity<byte[]> synthesizeSpeech(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            String language = request.getOrDefault("language", "en-US");

            if (text == null || text.isEmpty()) {
                logger.error("Text parameter is missing or empty");
                return ResponseEntity.badRequest().build();
            }

            logger.info("Synthesizing speech for {} characters in language: {}",
                    text.length(), language);

            byte[] audioData = textToSpeechService.synthesizeSpeech(text, language);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.set("Content-Disposition", "inline; filename=speech.mp3");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(audioData);

        } catch (IllegalArgumentException e) {
            logger.error("Text too long: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("TTS synthesis failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
