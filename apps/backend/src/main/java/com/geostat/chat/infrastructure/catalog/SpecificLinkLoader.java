package com.geostat.chat.infrastructure.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.geostat.chat.domain.catalog.LinkInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Keyword-triggered specific links loaded from {@code catalog/specific-links.yaml} (B-24). */
@Component
public class SpecificLinkLoader {

    record SpecificLink(
            String url,
            String urlEn,
            String titleKa,
            String titleEn,
            List<String> keywords,
            List<String> excludeKeywords) {

        LinkInfo toLinkInfo() {
            if (urlEn != null && !urlEn.isBlank()) {
                return new LinkInfo(url, titleKa, titleEn, urlEn);
            }
            return new LinkInfo(url, titleKa, titleEn);
        }
    }

    private record SpecificLinksRoot(List<SpecificLink> links) {}

    private List<SpecificLink> links = List.of();

    @PostConstruct
    void load() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        SpecificLinksRoot root = mapper.readValue(
                new ClassPathResource("catalog/specific-links.yaml").getInputStream(),
                SpecificLinksRoot.class);
        links = root.links() != null ? List.copyOf(root.links()) : List.of();
    }

    public List<LinkInfo> findMatches(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String lower = query.toLowerCase();

        List<SpecificLink> matches = new ArrayList<>();
        for (SpecificLink link : links) {
            List<String> keywords = link.keywords() != null ? link.keywords() : List.of();
            List<String> excludes = link.excludeKeywords() != null ? link.excludeKeywords() : List.of();
            if (countMatches(lower, keywords) > 0
                    && excludes.stream().noneMatch(lower::contains)) {
                matches.add(link);
            }
        }
        matches.sort((a, b) -> Integer.compare(
                countMatches(lower, b.keywords()),
                countMatches(lower, a.keywords())));

        return matches.stream().map(SpecificLink::toLinkInfo).toList();
    }

    private static int countMatches(String lower, List<String> keywords) {
        int count = 0;
        for (String kw : keywords) {
            boolean matched = kw.length() <= 4
                    ? containsWholeWord(lower, kw)
                    : lower.contains(kw.toLowerCase());
            if (matched) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsWholeWord(String text, String word) {
        String lw = word.toLowerCase();
        int i = text.indexOf(lw);
        while (i >= 0) {
            boolean start = (i == 0) || !Character.isLetterOrDigit(text.charAt(i - 1));
            boolean end = (i + lw.length() >= text.length())
                    || !Character.isLetterOrDigit(text.charAt(i + lw.length()));
            if (start && end) {
                return true;
            }
            i = text.indexOf(lw, i + 1);
        }
        return false;
    }
}
