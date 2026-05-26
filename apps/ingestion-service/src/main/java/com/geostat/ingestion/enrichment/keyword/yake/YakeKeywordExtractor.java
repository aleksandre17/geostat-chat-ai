package com.geostat.ingestion.enrichment.keyword.yake;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Java-native YAKE port (model version {@code yake-v1}). Statistical single-document keyword
 * extraction with n-grams 1–3 and sequence-matcher deduplication — no external corpus or NLP models.
 */
public final class YakeKeywordExtractor {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[.!?\\n]+");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:[-'][\\p{L}\\p{N}]+)*");
    private static final double DEDUP_LIMIT = 0.9;

    private final int maxNgram;

    public YakeKeywordExtractor(int maxNgram) {
        this.maxNgram = Math.max(1, maxNgram);
    }

    public List<String> extract(String text, int topN) {
        if (text == null || text.isBlank() || topN <= 0) {
            return List.of();
        }

        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) {
            return List.of();
        }

        List<List<String>> tokenized = new ArrayList<>(sentences.size());
        int totalTokens = 0;
        for (String sentence : sentences) {
            List<String> tokens = tokenize(sentence);
            tokenized.add(tokens);
            totalTokens += tokens.size();
        }
        if (totalTokens == 0) {
            return List.of();
        }

        Map<String, TermStats> stats = new LinkedHashMap<>();
        for (int sentenceIndex = 0; sentenceIndex < tokenized.size(); sentenceIndex++) {
            List<String> tokens = tokenized.get(sentenceIndex);
            for (int start = 0; start < tokens.size(); start++) {
                StringBuilder phrase = new StringBuilder();
                for (int size = 1; size <= maxNgram && start + size <= tokens.size(); size++) {
                    if (size > 1) {
                        phrase.append(' ');
                    }
                    phrase.append(tokens.get(start + size - 1));
                    String term = phrase.toString();
                    TermStats termStats = stats.computeIfAbsent(term, ignored -> new TermStats(term));
                    termStats.registerOccurrence(sentenceIndex, tokens.size());
                }
            }
        }

        List<ScoredTerm> ranked = new ArrayList<>(stats.size());
        int maxFrequency = stats.values().stream().mapToInt(TermStats::frequency).max().orElse(1);
        for (TermStats termStats : stats.values()) {
            if (termStats.frequency() < 1) {
                continue;
            }
            ranked.add(new ScoredTerm(termStats.term(), score(termStats, maxFrequency, totalTokens)));
        }
        ranked.sort(Comparator.comparingDouble(ScoredTerm::score));

        return deduplicate(ranked, topN);
    }

    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String part : SENTENCE_SPLIT.split(text)) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                sentences.add(trimmed);
            }
        }
        return sentences.isEmpty() ? List.of(text.trim()) : sentences;
    }

    private static List<String> tokenize(String sentence) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(sentence);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2 || Character.isDigit(token.charAt(0))) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static double score(TermStats termStats, int maxFrequency, int totalTokens) {
        double casing = casingFeature(termStats.term());
        double position = 1.0 / (1.0 + termStats.firstSentenceIndex());
        double frequency = termStats.frequency() / (double) maxFrequency;
        double relatednessToSentence = termStats.meanRelatednessToSentence();
        double termFrequency = termStats.frequency() / (double) totalTokens;
        double dispersion = termStats.sentenceDispersion();

        return casing
                * position
                * (1.0 + frequency)
                * (1.0 + relatednessToSentence)
                * (1.0 + termFrequency)
                * (1.0 + dispersion);
    }

    private static double casingFeature(String term) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < term.length(); i++) {
            char ch = term.charAt(i);
            if (Character.isLetter(ch)) {
                letters++;
                if (Character.isUpperCase(ch)) {
                    upper++;
                }
            }
        }
        if (letters == 0) {
            return 1.0;
        }
        return upper == 0 ? 1.0 : 1.0 + (upper / (double) letters);
    }

    private static List<String> deduplicate(List<ScoredTerm> ranked, int topN) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (ScoredTerm candidate : ranked) {
            if (selected.size() >= topN) {
                break;
            }
            if (isDuplicate(candidate.term(), selected)) {
                continue;
            }
            selected.add(candidate.term());
        }
        return new ArrayList<>(selected);
    }

    private static boolean isDuplicate(String candidate, Iterable<String> selected) {
        String normalizedCandidate = normalizeForDedup(candidate);
        for (String existing : selected) {
            if (sequenceMatchRatio(normalizedCandidate, normalizeForDedup(existing)) >= DEDUP_LIMIT) {
                return true;
            }
            if (normalizedCandidate.contains(normalizeForDedup(existing))
                    || normalizeForDedup(existing).contains(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeForDedup(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static double sequenceMatchRatio(String left, String right) {
        if (left.equals(right)) {
            return 1.0;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int lcs = longestCommonSubsequence(left, right);
        return (2.0 * lcs) / (left.length() + right.length());
    }

    private static int longestCommonSubsequence(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                if (left.charAt(i - 1) == right.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[left.length()][right.length()];
    }

    private static final class TermStats {
        private final String term;
        private int frequency;
        private int firstSentenceIndex = Integer.MAX_VALUE;
        private final LinkedHashSet<Integer> sentenceIndices = new LinkedHashSet<>();
        private double relatednessSum;

        private TermStats(String term) {
            this.term = term;
        }

        private void registerOccurrence(int sentenceIndex, int sentenceTokenCount) {
            frequency++;
            firstSentenceIndex = Math.min(firstSentenceIndex, sentenceIndex);
            sentenceIndices.add(sentenceIndex);
            relatednessSum += 1.0 / Math.max(1, sentenceTokenCount);
        }

        private String term() {
            return term;
        }

        private int frequency() {
            return frequency;
        }

        private int firstSentenceIndex() {
            return firstSentenceIndex == Integer.MAX_VALUE ? 0 : firstSentenceIndex;
        }

        private double meanRelatednessToSentence() {
            return relatednessSum / frequency;
        }

        private double sentenceDispersion() {
            return sentenceIndices.size() / (double) Math.max(1, frequency);
        }
    }

    private record ScoredTerm(String term, double score) {}
}
