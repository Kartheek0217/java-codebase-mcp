package com.mcp.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mcp.properties.OllamaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

/**
 * Low-level HTTP client for the OpenAI-compatible chat completions endpoint.
 * <p>
 * Uses Spring 6+ {@link RestClient} — no external SDK required.
 * Connection and read timeouts, base URL, API key, and model name are all
 * driven by {@link OllamaProperties} and can be changed in
 * {@code application.properties} without recompilation.
 * </p>
 *
 * <p>
 * Endpoint called: {@code POST {baseUrl}/chat/completions}
 * </p>
 */
@Service
public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);

    private final OllamaProperties props;
    private RestClient restClient;

    public OllamaClient(OllamaProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));

        restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInitializer(request -> request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
        logger.info("OllamaClient initialised — baseUrl={}, model={}, timeoutSeconds={}", props.getBaseUrl(),
                props.getModel(), props.getTimeoutSeconds());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a list of chat messages to the model and returns the assistant reply.
     *
     * @param messages ordered list of {@link Message} objects (system + user turns)
     * @return the assistant's text response
     * @throws OllamaException if the HTTP call fails or the model returns no
     *                         content
     */
    public String chat(List<Message> messages) {
        ChatRequest request = new ChatRequest(props.getModel(), messages, props.getMaxTokens(), false);

        logger.debug("Sending chat request to Ollama — model={}, messages={}", props.getModel(), messages.size());

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (Exception ex) {
            throw new OllamaException("HTTP call to Ollama failed: " + ex.getMessage(), ex);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new OllamaException("Ollama returned an empty response for model: " + props.getModel());
        }

        String content = response.choices().getFirst().message().content();
        logger.debug("Received Ollama response — length={}", content == null ? 0 : content.length());
        return content != null ? content : "";
    }

    /**
     * Pings {@code GET /models} to check whether Ollama is reachable.
     *
     * @return {@code true} if the endpoint responds with HTTP 2xx
     */
    public boolean isReachable() {
        try {
            restClient.get()
                    .uri("/models")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            logger.warn("Ollama health check failed: {}", ex.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Request / Response records (mirror the OpenAI wire format)
    // -------------------------------------------------------------------------

    /**
     * A single chat message.
     *
     * @param role    one of {@code "system"}, {@code "user"}, {@code "assistant"}
     * @param content the message text
     */
    public record Message(String role, String content) {
    }

    private record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("max_tokens") int maxTokens,
            boolean stream) {
    }

    private record ChatResponse(List<Choice> choices) {
    }

    private record Choice(Message message) {
    }

    // -------------------------------------------------------------------------
    // Exception
    // -------------------------------------------------------------------------

    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) {
            super(message);
        }

        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
