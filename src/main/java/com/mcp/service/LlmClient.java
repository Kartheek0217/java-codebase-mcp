package com.mcp.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.properties.LlmProperties;

import jakarta.annotation.PostConstruct;

/**
 * Low-level HTTP client for OpenAI-compatible chat completions endpoint
 * (specifically OpenAdapter).
 */
@Service
public class LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(LlmClient.class);

    private final LlmProperties props;
    private RestClient restClient;

    public LlmClient(LlmProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(props.getTimeoutSeconds()));
        factory.setReadTimeout(java.time.Duration.ofSeconds(props.getTimeoutSeconds()));

        restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInitializer(request -> request.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
        logger.info("LlmClient initialised — baseUrl={}, defaultModel={}, timeoutSeconds={}", props.getBaseUrl(),
                props.getDefaultModel(), props.getTimeoutSeconds());
    }

    /**
     * Resolves the model name to use based on the task type.
     */
    private String resolveModelForTask(String taskType) {
        if (taskType == null) {
            return props.getDefaultModel();
        }
        return switch (taskType.toLowerCase()) {
            case "code-review" -> props.getModelCodeReview();
            case "code-analyse", "code-analysis", "explain-symbol", "explain-file" -> props.getModelCodeAnalysis();
            case "code-refactor", "code-optimise" -> props.getModelCodeRefactor();
            case "web-search", "r&d" -> props.getModelWebSearch();
            case "junit-test-cases", "junit-test" -> props.getModelJunitTest();
            case "java-doc" -> props.getModelJavaDoc();
            case "code-commit" -> props.getModelCodeCommit();
            default -> props.getDefaultModel();
        };
    }

    /**
     * Sends a list of chat messages using the default model.
     */
    public String chat(List<Message> messages) {
        return chat(messages, null);
    }

    /**
     * Sends a list of chat messages using the specified task-specific model.
     */
    public String chat(List<Message> messages, String taskType) {
        String model = resolveModelForTask(taskType);
        ChatRequest request = new ChatRequest(model, messages, props.getMaxTokens(), false);

        logger.info("Sending chat request to LLM — model={}, taskType={}, messages={}", model, taskType,
                messages.size());

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (Exception ex) {
            throw new LlmException("HTTP call to LLM failed: " + ex.getMessage(), ex);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmException("LLM returned an empty response for model: " + model);
        }

        String content = response.choices().getFirst().message().content();
        logger.debug("Received LLM response — length={}", content == null ? 0 : content.length());
        return content != null ? content : "";
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Sends a list of chat messages using the specified task-specific model and
     * streams the response chunks.
     */
    public void streamChat(List<Message> messages, String taskType, java.util.function.Consumer<String> chunkConsumer) {
        String model = resolveModelForTask(taskType);
        ChatRequest request = new ChatRequest(model, messages, props.getMaxTokens(), true);

        logger.info("Sending streaming chat request to LLM — model={}, taskType={}, messages={}", model, taskType,
                messages.size());

        try {
            restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .exchange((req, res) -> {
                        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(res.getBody(),
                                java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6).trim();
                                    if ("[DONE]".equals(data)) {
                                        break;
                                    }
                                    if (!data.isEmpty()) {
                                        String content = parseDeltaContent(data);
                                        if (content != null && !content.isEmpty()) {
                                            chunkConsumer.accept(content);
                                        }
                                    }
                                }
                            }
                        }
                        return null;
                    });
        } catch (Exception ex) {
            throw new LlmException("Streaming HTTP call to LLM failed: " + ex.getMessage(), ex);
        }
    }

    private String parseDeltaContent(String json) {
        try {
            var node = OBJECT_MAPPER.readTree(json);
            var choices = node.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                var delta = choices.get(0).get("delta");
                if (delta != null) {
                    var content = delta.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Pings the /models endpoint to verify the API is reachable.
     */
    public boolean isReachable() {
        try {
            restClient.get()
                    .uri("/models")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            logger.warn("LLM health check failed: {}", ex.getMessage());
            return false;
        }
    }

    // OpenAI schema structures
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

    public static class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}