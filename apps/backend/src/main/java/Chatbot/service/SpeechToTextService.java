package Chatbot.service;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class SpeechToTextService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechToTextService.class);

    private static final String GEORGIAN_LANGUAGE_CODE = "ka-GE";
    private static final String ENGLISH_LANGUAGE_CODE = "en-US";
    private static final int WEBM_OPUS_SAMPLE_RATE = 48000;

    private final SpeechClient speechClient;

    public SpeechToTextService(SpeechClient speechClient) {
        this.speechClient = speechClient;
    }

    public String transcribeAudio(byte[] audioData, String languageCode) {
        if (audioData == null || audioData.length == 0) {
            logger.warn("Empty audio data received");
            return "";
        }

        try {
            ByteString audioBytes = ByteString.copyFrom(audioData);

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                    .setSampleRateHertz(WEBM_OPUS_SAMPLE_RATE)
                    .setLanguageCode(languageCode != null ? languageCode : GEORGIAN_LANGUAGE_CODE)
                    .setEnableAutomaticPunctuation(true)
                    .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(audioBytes)
                    .build();

            RecognizeResponse response = speechClient.recognize(config, audio);
            List<SpeechRecognitionResult> results = response.getResultsList();

            if (results.isEmpty()) {
                logger.info("No speech detected in audio");
                return "";
            }

            SpeechRecognitionAlternative alternative = results.get(0)
                    .getAlternativesList()
                    .get(0);

            String transcript = alternative.getTranscript();
            logger.info("Transcription successful: {} characters", transcript.length());

            return transcript;

        } catch (Exception e) {
            logger.error("Transcription failed", e);
            throw new RuntimeException("Failed to transcribe audio", e);
        }
    }

    public String transcribeGeorgianAudio(byte[] audioData) {
        return transcribeAudio(audioData, GEORGIAN_LANGUAGE_CODE);
    }

    public String transcribeEnglishAudio(byte[] audioData) {
        return transcribeAudio(audioData, ENGLISH_LANGUAGE_CODE);
    }
}
