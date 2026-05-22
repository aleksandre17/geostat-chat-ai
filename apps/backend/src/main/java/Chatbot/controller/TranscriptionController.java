package Chatbot.controller;


import Chatbot.service.SpeechToTextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TranscriptionController {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptionController.class);
    private final SpeechToTextService speechToTextService;

    public TranscriptionController(SpeechToTextService speechToTextService) {
        this.speechToTextService = speechToTextService;
    }

    @PostMapping("/transcribe")
    public ResponseEntity<?> transcribeAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", defaultValue = "ka-GE") String language
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No audio file provided"));
        }

        try {
            byte[] audioData = file.getBytes();
            String transcript = speechToTextService.transcribeAudio(audioData, language);

            if (transcript.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "transcript", "",
                        "message", "No speech detected"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "transcript", transcript,
                    "language", language
            ));

        } catch (IOException e) {
            logger.error("Failed to read audio file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process audio file"));
        } catch (Exception e) {
            logger.error("Transcription error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Transcription failed: " + e.getMessage()));
        }
    }
}
