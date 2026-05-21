package com.mcp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Ollama / OpenAI-compatible LLM integration.
 * <p>
 * All values are overridable via {@code application.properties} under the
 * {@code mcp.ollama.*} prefix. The defaults point to a locally-hosted Ollama
 * instance using the {@code granite3.1:3b} model.
 * </p>
 *
 * <pre>
 * mcp.ollama.base-url=http://localhost:11434/v1
 * mcp.ollama.api-key=ollama
 * mcp.ollama.model=granite3.1:3b
 * mcp.ollama.timeout-seconds=120
 * mcp.ollama.max-tokens=2048
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "mcp.ollama")
public class OllamaProperties {

    /**
     * Base URL of the OpenAI-compatible endpoint.
     * For Ollama: {@code http://localhost:11434/v1}
     */
    private String baseUrl = "http://localhost:11434/v1";

    /**
     * API key sent as a Bearer token in the Authorization header.
     * Ollama requires the header to be present but ignores its value.
     */
    private String apiKey = "ollama";

    /**
     * Name of the model to use for completions.
     * Must be a model that has already been pulled in Ollama (e.g.
     * {@code ollama pull granite4.1:3b}).
     */
    private String model = "granite4.1:3b";

    /**
     * HTTP read/connect timeout in seconds for calls to the LLM endpoint.
     * Large models can be slow on first token — increase this if you see timeouts.
     */
    private int timeoutSeconds = 120;

    /**
     * Maximum number of tokens the model may generate in a single response.
     */
    private int maxTokens = 2048;

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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
