package com.geostat.ingestion.enrichment.entity;

import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.Entity;
import com.geostat.platform.enrichment.EntityDeriver;
import com.geostat.platform.enrichment.EntityJsonParser;
import com.geostat.ingestion.enrichment.prompt.EntityPromptTemplate;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class GeminiFewShotEntityDeriver implements EntityDeriver {

    private final ChatClient chatClient;
    private final EntityPromptTemplate promptTemplate;

    public GeminiFewShotEntityDeriver(ChatClient enrichmentChatClient, EntityPromptTemplate promptTemplate) {
        this.chatClient = enrichmentChatClient;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public List<Entity> deriveEntities(DocumentContext document) {
        String user = promptTemplate.user(
                document.title(), document.sectionPath(), document.language(), document.contentText());
        String raw = chatClient
                .prompt()
                .system(promptTemplate.system())
                .user(user)
                .call()
                .content();
        return EntityJsonParser.parseEntityResponse(raw);
    }

    static List<Entity> parseEntityResponse(String raw) {
        return EntityJsonParser.parseEntityResponse(raw);
    }

    static List<Entity> deduplicate(List<Entity> entities) {
        return EntityJsonParser.deduplicate(entities);
    }
}
