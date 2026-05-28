package com.geostat.chat.infrastructure.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geostat.chat.domain.prompt.UiStringKey;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads {@code prompts/chat-prompts.yaml} (B-27). */
@Component
public class PromptCatalogLoader {

    record LocalizedString(String ka, String en) {}

    record LocalizedPrompts(String ka, String en) {}

    record PromptsRoot(
            int version,
            LocalizedPrompts main,
            LocalizedPrompts clarification,
            String topicClassifier,
            java.util.Map<String, LocalizedString> uiStrings) {}

    private PromptsRoot root;
    private String contentHash;

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/chat-prompts.yaml");
        byte[] bytes = resource.getInputStream().readAllBytes();
        contentHash = sha256(bytes);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        root = mapper.readValue(bytes, PromptsRoot.class);
    }

    String main(boolean isGeorgian) {
        return isGeorgian ? root.main().ka() : root.main().en();
    }

    String clarification(boolean isGeorgian) {
        return isGeorgian ? root.clarification().ka() : root.clarification().en();
    }

    String topicClassifier() {
        return root.topicClassifier();
    }

    int version() {
        return root.version();
    }

    String contentHash() {
        return contentHash;
    }

    String uiString(UiStringKey key, boolean isGeorgian) {
        if (root.uiStrings() == null) return key.name();
        LocalizedString entry = root.uiStrings().get(key.yamlKey());
        if (entry == null) return key.name();
        String value = isGeorgian ? entry.ka() : entry.en();
        return value != null ? value : key.name();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("prompt hash failed", e);
        }
    }
}
