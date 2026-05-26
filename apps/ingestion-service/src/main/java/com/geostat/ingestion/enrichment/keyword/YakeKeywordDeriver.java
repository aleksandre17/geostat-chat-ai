package com.geostat.ingestion.enrichment.keyword;

import com.geostat.ingestion.enrichment.keyword.yake.YakeKeywordExtractor;
import com.geostat.platform.enrichment.DocumentContext;
import com.geostat.platform.enrichment.KeywordDeriver;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("db")
@ConditionalOnProperty(prefix = "geostat.ingestion.enrichment", name = "enabled", havingValue = "true")
public class YakeKeywordDeriver implements KeywordDeriver {

    private static final int MAX_NGRAM = 3;

    private final YakeKeywordExtractor extractor = new YakeKeywordExtractor(MAX_NGRAM);

    @Override
    public List<String> deriveKeywords(DocumentContext document, int topN) {
        return extractor.extract(document.contentText(), topN);
    }
}
