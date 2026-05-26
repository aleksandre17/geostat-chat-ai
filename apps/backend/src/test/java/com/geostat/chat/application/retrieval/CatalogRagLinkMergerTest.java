package com.geostat.chat.application.retrieval;

import com.geostat.chat.domain.catalog.LinkCard;
import com.geostat.chat.infrastructure.catalog.YamlPresentationStyleCatalog;
import com.geostat.platform.contracts.retrieval.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogRagLinkMergerTest {

    private final CatalogRagLinkMerger merger =
            new CatalogRagLinkMerger(YamlPresentationStyleCatalog.fromClasspath());

    @Test
    void merge_deduplicatesCatalogUrlAgainstRag() {
        LinkCard catalog = new LinkCard(
                "https://www.geostat.ge/ka/statistics",
                "სტატისტიკა", "Statistics", "statistics", "icon", "", "#fff");
        RetrievedChunk rag = new RetrievedChunk(
                "doc-1",
                "https://www.geostat.ge/ka/statistics/",
                "passage text",
                0.9);

        List<LinkCard> merged = merger.merge(List.of(catalog), List.of(rag), true);

        assertEquals(1, merged.size());
        assertEquals("statistics", merged.get(0).type());
    }

    @Test
    void merge_usesPageDescriptionForGeorgianLocale() {
        RetrievedChunk rag = new RetrievedChunk(
                "doc-2",
                "https://www.geostat.ge/ka/news/123",
                "79.9 28 235.4",
                0.8,
                "ka",
                "სიახლეები",
                null,
                "ოფიციალური ინფლაციის სტატისტიკა საქართველოში.",
                null);

        List<LinkCard> merged = merger.merge(List.of(), List.of(rag), true);

        assertEquals(1, merged.size());
        assertEquals("rag", merged.get(0).sourceType());
        assertTrue(merged.get(0).snippet().contains("ინფლაცი"));
    }

    @Test
    void merge_usesPageDescriptionForEnglishLocale() {
        RetrievedChunk rag = new RetrievedChunk(
                "doc-en",
                "https://www.geostat.ge/en/modules/categories/25",
                "table data",
                0.85,
                "en",
                "Price Statistics",
                null,
                "Official price statistics and consumer price indices for Georgia.",
                null);

        List<LinkCard> merged = merger.merge(List.of(), List.of(rag), false);

        assertEquals(1, merged.size());
        assertEquals("Price Statistics", merged.get(0).titleEn());
        assertNotNull(merged.get(0).snippet());
        assertTrue(merged.get(0).snippet().contains("price statistics"));
    }

    @Test
    void merge_fallsBackToProseExcerptWhenNoPageDescription() {
        RetrievedChunk rag = new RetrievedChunk(
                "doc-3",
                "https://www.geostat.ge/ka/about",
                "about page with meaningful prose about national statistics office",
                0.7,
                "ka",
                "About",
                null,
                null,
                null);

        List<LinkCard> merged = merger.merge(List.of(), List.of(rag), true);

        assertEquals(1, merged.size());
        assertTrue(merged.get(0).snippet().contains("meaningful prose"));
    }

    @Test
    void merge_omitsSnippetWhenChunkIsTableOnly() {
        RetrievedChunk rag = new RetrievedChunk(
                "doc-4",
                "https://www.geostat.ge/en/x",
                "1 2 3 4 5 6 7 8 9",
                0.7,
                "en",
                "Table",
                null,
                null,
                null);

        List<LinkCard> merged = merger.merge(List.of(), List.of(rag), false);

        assertNull(merged.get(0).snippet());
    }

    @Test
    void merge_usesSectionPathWhenBoilerplateAndContentLacksProse() {
        RetrievedChunk rag = new RetrievedChunk(
                "doc-bp",
                "https://www.geostat.ge/ka/modules/categories/26/samomkhmareblo-fasebis-indeksi-inflatsia",
                "table numbers",
                0.9,
                "ka",
                "სამომხმარებლო ფასების ინდექსი (ინფლაცია)",
                "ფასების სტატისტიკა",
                "× ვებგვერდის ადაპტირებული ვერსია UNDP",
                null);

        List<LinkCard> merged = merger.merge(List.of(), List.of(rag), true);

        assertEquals(1, merged.size());
        assertEquals("მონაკვეთი: ფასების სტატისტიკა", merged.get(0).snippet());
    }

    @Test
    void contentExcerpt_trimsLeadingPartialWord() {
        String snippet = CatalogRagLinkMerger.contentExcerpt(
                "დექსი წინა თვესთან შედარებით მონაცემების გადმოწერა XLS CSV "
                        + "სამომხმარებლო ფასების ინდექსი 2010 წლის საშუალოსთან",
                "სამომხმარებლო ფასების ინდექსი (ინფლაცია)");

        assertNotNull(snippet);
        assertTrue(snippet.startsWith("წინა თვესთან"));
    }

    @Test
    void merge_skipsSnippetWhenDescriptionDuplicatesTitle() {
        RetrievedChunk rag = new RetrievedChunk(
                "doc-dup",
                "https://www.geostat.ge/ka/x",
                "table only 1 2 3",
                0.8,
                "ka",
                "სამომხმარებლო ფასების ინდექსი",
                null,
                "სამომხმარებლო ფასების ინდექსი",
                null);

        List<LinkCard> merged = merger.merge(List.of(), List.of(rag), true);

        assertNull(merged.get(0).snippet());
    }
}
