package com.geostat.ingestion.enrichment.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geostat.ingestion.enrichment")
public class EnrichmentProperties {

    /** Master switch for Layer 2 derivers (RAG-U01). Default off until ops enables. */
    private boolean enabled = false;

    private String modelVersion = "gemini-2.5-flash-lite@2026-05-25";

    private String chatModel = "gemini-2.5-flash-lite";

    private int maxRetries = 2;

    private String keywordModelVersion = "yake-v1";

    private int keywordTopN = 15;

    private String entityModelVersion = "gemini-2.5-flash-lite-entities@2026-05-25";

    private String localePairModelVersion = "url-embed-v1@2026-05-25";

    private double localePairSimilarityThreshold = 0.92;

    private String pageKindModelVersion = "gemini-2.5-flash-lite-pagekind@2026-05-25";

    private boolean namedVectorsEnabled = false;

    private String titleVectorModelVersion = "hash-v1-title-v1@2026-05-25";

    private String summaryVectorModelVersion = "hash-v1-summary-v1@2026-05-25";

    private boolean authoritySchedulerEnabled = false;

    private String authorityCron = "0 0 3 * * *";

    private double pagerankDamping = 0.85;

    private int topicMinDocuments = 50;

    private boolean topicSchedulerEnabled = false;

    private String topicCron = "0 0 4 * * *";

    private String topicAssignModelVersion = "smile-kmeans-v1@2026-05-25";

    private String topicMiningModelVersion = "smile-kmeans-v1@2026-05-25";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String modelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String chatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String keywordModelVersion() {
        return keywordModelVersion;
    }

    public void setKeywordModelVersion(String keywordModelVersion) {
        this.keywordModelVersion = keywordModelVersion;
    }

    public int keywordTopN() {
        return keywordTopN;
    }

    public void setKeywordTopN(int keywordTopN) {
        this.keywordTopN = keywordTopN;
    }

    public String entityModelVersion() {
        return entityModelVersion;
    }

    public void setEntityModelVersion(String entityModelVersion) {
        this.entityModelVersion = entityModelVersion;
    }

    public String localePairModelVersion() {
        return localePairModelVersion;
    }

    public void setLocalePairModelVersion(String localePairModelVersion) {
        this.localePairModelVersion = localePairModelVersion;
    }

    public double localePairSimilarityThreshold() {
        return localePairSimilarityThreshold;
    }

    public void setLocalePairSimilarityThreshold(double localePairSimilarityThreshold) {
        this.localePairSimilarityThreshold = localePairSimilarityThreshold;
    }

    public String pageKindModelVersion() {
        return pageKindModelVersion;
    }

    public void setPageKindModelVersion(String pageKindModelVersion) {
        this.pageKindModelVersion = pageKindModelVersion;
    }

    public boolean isNamedVectorsEnabled() {
        return namedVectorsEnabled;
    }

    public void setNamedVectorsEnabled(boolean namedVectorsEnabled) {
        this.namedVectorsEnabled = namedVectorsEnabled;
    }

    public String titleVectorModelVersion() {
        return titleVectorModelVersion;
    }

    public void setTitleVectorModelVersion(String titleVectorModelVersion) {
        this.titleVectorModelVersion = titleVectorModelVersion;
    }

    public String summaryVectorModelVersion() {
        return summaryVectorModelVersion;
    }

    public void setSummaryVectorModelVersion(String summaryVectorModelVersion) {
        this.summaryVectorModelVersion = summaryVectorModelVersion;
    }

    public boolean isAuthoritySchedulerEnabled() {
        return authoritySchedulerEnabled;
    }

    public void setAuthoritySchedulerEnabled(boolean authoritySchedulerEnabled) {
        this.authoritySchedulerEnabled = authoritySchedulerEnabled;
    }

    public String authorityCron() {
        return authorityCron;
    }

    public void setAuthorityCron(String authorityCron) {
        this.authorityCron = authorityCron;
    }

    public double pagerankDamping() {
        return pagerankDamping;
    }

    public void setPagerankDamping(double pagerankDamping) {
        this.pagerankDamping = pagerankDamping;
    }

    public int topicMinDocuments() {
        return topicMinDocuments;
    }

    public void setTopicMinDocuments(int topicMinDocuments) {
        this.topicMinDocuments = topicMinDocuments;
    }

    public boolean isTopicSchedulerEnabled() {
        return topicSchedulerEnabled;
    }

    public void setTopicSchedulerEnabled(boolean topicSchedulerEnabled) {
        this.topicSchedulerEnabled = topicSchedulerEnabled;
    }

    public String topicCron() {
        return topicCron;
    }

    public void setTopicCron(String topicCron) {
        this.topicCron = topicCron;
    }

    public String topicAssignModelVersion() {
        return topicAssignModelVersion;
    }

    public void setTopicAssignModelVersion(String topicAssignModelVersion) {
        this.topicAssignModelVersion = topicAssignModelVersion;
    }

    public String topicMiningModelVersion() {
        return topicMiningModelVersion;
    }

    public void setTopicMiningModelVersion(String topicMiningModelVersion) {
        this.topicMiningModelVersion = topicMiningModelVersion;
    }
}
