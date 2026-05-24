package com.geostat.ingestion.chunk;

import com.geostat.ingestion.chunk.strategy.FixedSizeChunker;
import com.geostat.ingestion.chunk.strategy.TextChunk;
import com.geostat.ingestion.crawl.frontier.UrlHasher;
import com.geostat.ingestion.persistence.entity.ChunkEntity;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.repository.ChunkRepository;
import com.geostat.ingestion.parse.SectionPathExtractor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("db")
public class DocumentChunkWriter {

    private final ChunkRepository chunkRepository;
    private final FixedSizeChunker chunker;

    public DocumentChunkWriter(ChunkRepository chunkRepository, FixedSizeChunker chunker) {
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
    }

    @Transactional
    public int replaceChunks(
            DocumentEntity document,
            CorpusEntity corpus,
            String cleanedText,
            List<String> sectionPath,
            String language) {
        chunkRepository.deleteByDocument_Id(document.getId());
        List<TextChunk> chunks = chunker.chunk(cleanedText);
        String sectionJoined = SectionPathExtractor.joinPath(sectionPath);
        for (TextChunk chunk : chunks) {
            ChunkEntity entity = new ChunkEntity();
            entity.setDocument(document);
            entity.setCorpus(corpus);
            entity.setSequenceNo(chunk.sequenceNo());
            entity.setText(chunk.text());
            entity.setTextHash(UrlHasher.hash(chunk.text()));
            entity.setTokenCount(estimateTokens(chunk.text()));
            entity.setChunkStrategy(FixedSizeChunker.STRATEGY_ID);
            Map<String, Object> meta = new HashMap<>();
            if (language != null) {
                meta.put("language", language);
            }
            if (!sectionJoined.isBlank()) {
                meta.put("sectionPath", sectionJoined);
            }
            if (document.getTitle() != null) {
                meta.put("pageTitle", document.getTitle());
            }
            if (document.getDisplayDescription() != null && !document.getDisplayDescription().isBlank()) {
                meta.put("pageDescription", document.getDisplayDescription());
            }
            entity.setMetadata(meta);
            chunkRepository.save(entity);
        }
        return chunks.size();
    }

    /** @deprecated use {@link #replaceChunks(DocumentEntity, CorpusEntity, String, List, String)} */
    @Transactional
    public int replaceChunks(DocumentEntity document, CorpusEntity corpus, String cleanedText) {
        return replaceChunks(document, corpus, cleanedText, List.of(), document.getLanguage());
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
