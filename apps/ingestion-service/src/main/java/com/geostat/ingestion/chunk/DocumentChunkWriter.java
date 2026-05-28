package com.geostat.ingestion.chunk;



import com.geostat.ingestion.chunk.strategy.FixedSizeChunker;
import com.geostat.ingestion.chunk.strategy.TextChunk;
import com.geostat.platform.url.UrlHasher;
import com.geostat.ingestion.persistence.entity.ChunkEntity;
import com.geostat.ingestion.persistence.entity.CorpusEntity;
import com.geostat.ingestion.persistence.entity.DocumentEntity;
import com.geostat.ingestion.persistence.repository.ChunkRepository;
import com.geostat.ingestion.parse.SectionPathExtractor;
import java.util.ArrayList;
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
    private final ChunkHasher chunkHasher;

    public DocumentChunkWriter(ChunkRepository chunkRepository, FixedSizeChunker chunker, ChunkHasher chunkHasher) {
        this.chunkRepository = chunkRepository;
        this.chunker = chunker;
        this.chunkHasher = chunkHasher;
    }



    @Transactional

    public int replaceChunks(

            DocumentEntity document,

            CorpusEntity corpus,

            String cleanedText,

            List<String> sectionPath,

            String language) {

        chunkRepository.deleteByDocumentId(document.getId());

        List<TextChunk> chunks = chunker.chunk(cleanedText);

        String sectionJoined = SectionPathExtractor.joinPath(sectionPath);

        String navBreadcrumb = document.getNavBreadcrumb();

        List<ChunkEntity> entities = new ArrayList<>(chunks.size());

        for (TextChunk chunk : chunks) {

            Map<String, Object> meta = new HashMap<>();

            if (language != null) {

                meta.put("language", language);

            }

            if (!sectionJoined.isBlank()) {

                meta.put("sectionPath", sectionJoined);

            }

            if (navBreadcrumb != null) {

                meta.put("navBreadcrumb", navBreadcrumb);

            }

            if (document.getTitle() != null) {

                meta.put("pageTitle", document.getTitle());

            }

            if (document.getDisplayDescription() != null && !document.getDisplayDescription().isBlank()) {

                meta.put("pageDescription", document.getDisplayDescription());

            }

            ChunkEntity entity = new ChunkEntity();

            entity.setDocument(document);

            entity.setCorpus(corpus);

            entity.setSequenceNo(chunk.sequenceNo());

            entity.setText(chunk.text());

            entity.setTextHash(UrlHasher.hash(chunk.text()));

            entity.setContentHash(chunkHasher.hash(chunk.text()));

            entity.setTokenCount(estimateTokens(chunk.text()));

            entity.setChunkStrategy(FixedSizeChunker.STRATEGY_ID);

            entity.setNavBreadcrumb(navBreadcrumb);

            entity.setMetadata(meta);

            entities.add(entity);

        }

        chunkRepository.saveAll(entities);

        return entities.size();

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

