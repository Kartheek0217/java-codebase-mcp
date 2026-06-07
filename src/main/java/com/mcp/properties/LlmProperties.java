package com.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the generic OpenAI-compatible / OpenAdapter LLM integration.
 */
@Component
@ConfigurationProperties(prefix = "mcp.llm")
public class LlmProperties {

    private String baseUrl = "https://api.openadapter.in/v1";
    private String apiKey = "";
    private String defaultModel = "DeepSeek-V4-Flash";
    private String modelCodeReview = "Qwen3-Coder";
    private String modelCodeAnalysis = "Kimi-K2.5";
    private String modelCodeRefactor = "MiniMax-M2.5";
    private String modelWebSearch = "gemma-4-31b-it";
    private String modelJunitTest = "GLM-5";
    private String modelJavaDoc = "0G-Qwen3.7-max";
    private String modelCodeCommit = "0G-DeepSeek-v4-Pro";
    private int timeoutSeconds = 120;
    private int maxTokens = 2048;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getModelCodeReview() {
        return modelCodeReview;
    }

    public void setModelCodeReview(String modelCodeReview) {
        this.modelCodeReview = modelCodeReview;
    }

    public String getModelCodeAnalysis() {
        return modelCodeAnalysis;
    }

    public void setModelCodeAnalysis(String modelCodeAnalysis) {
        this.modelCodeAnalysis = modelCodeAnalysis;
    }

    public String getModelCodeRefactor() {
        return modelCodeRefactor;
    }

    public void setModelCodeRefactor(String modelCodeRefactor) {
        this.modelCodeRefactor = modelCodeRefactor;
    }

    public String getModelWebSearch() {
        return modelWebSearch;
    }

    public void setModelWebSearch(String modelWebSearch) {
        this.modelWebSearch = modelWebSearch;
    }

    public String getModelJunitTest() {
        return modelJunitTest;
    }

    public void setModelJunitTest(String modelJunitTest) {
        this.modelJunitTest = modelJunitTest;
    }

    public String getModelJavaDoc() {
        return modelJavaDoc;
    }

    public void setModelJavaDoc(String modelJavaDoc) {
        this.modelJavaDoc = modelJavaDoc;
    }

    public String getModelCodeCommit() {
        return modelCodeCommit;
    }

    public void setModelCodeCommit(String modelCodeCommit) {
        this.modelCodeCommit = modelCodeCommit;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}
