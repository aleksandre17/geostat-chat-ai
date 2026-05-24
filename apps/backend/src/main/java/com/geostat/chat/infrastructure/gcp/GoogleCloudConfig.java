package com.geostat.chat.infrastructure.gcp;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Configuration
public class GoogleCloudConfig {

    /** Canonical path when running from {@code backend/} (bootRun, IDE). */
    private static final String SECRETS_CREDENTIALS = "../../ops/config/backend/google-credentials.json";

    private static final List<String> CREDENTIAL_CANDIDATES = List.of(
            SECRETS_CREDENTIALS,
            "google-credentials.json"
    );

    @Bean
    public SpeechClient speechClient() throws IOException {
        String envCredentials = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envCredentials != null && !envCredentials.isBlank()) {
            File envFile = new File(envCredentials);
            if (envFile.isFile()) {
                return createSpeechClient(envFile);
            }
            return SpeechClient.create();
        }

        File credFile = resolveCredentialsFile();
        if (credFile != null) {
            return createSpeechClient(credFile);
        }

        throw new IOException(
                "Google application default credentials were not found. "
                        + "Set GOOGLE_APPLICATION_CREDENTIALS, or place google-credentials.json in "
                        + SECRETS_CREDENTIALS + " (run from backend/). "
                        + "See https://cloud.google.com/docs/authentication/external/set-up-adc");
    }

    private static File resolveCredentialsFile() {
        for (String relative : CREDENTIAL_CANDIDATES) {
            Path path = Path.of(relative).normalize();
            File file = path.toFile();
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private static SpeechClient createSpeechClient(File credFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(credFile)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(fis)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
            FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
            SpeechSettings settings = SpeechSettings.newBuilder()
                    .setCredentialsProvider(credentialsProvider)
                    .build();
            return SpeechClient.create(settings);
        }
    }
}
