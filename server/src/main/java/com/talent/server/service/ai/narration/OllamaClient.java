package com.talent.server.service.ai.narration;

import com.talent.server.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal client for the local Ollama REST API ({@code /api/chat},
 * {@code /api/tags}).
 * Uses structured outputs: the JSON schema is sent as {@code format}, so the
 * model must reply with
 * JSON that matches it. Low temperature keeps output stable.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final Duration HEALTH_CACHE = Duration.ofSeconds(20);

    private final AiProperties.Ollama settings;
    private final RestClient restClient;
    private final RestClient healthClient;
    private final Clock clock;
    private final AtomicReference<Health> lastHealth = new AtomicReference<>();

    /** Reachability of the server and whether the configured model is installed. */
    public record Health(boolean reachable, boolean modelInstalled, String detail, Instant checkedAt) {

        public boolean usable() {
            return reachable && modelInstalled;
        }
    }

    public OllamaClient(AiProperties properties, Clock clock) {
        this.settings = properties.ollama();
        this.clock = clock;
        this.restClient = RestClient.builder().baseUrl(settings.baseUrl())
                .requestFactory(requestFactory(settings.connectTimeout(), settings.readTimeout())).build();
        this.healthClient = RestClient.builder().baseUrl(settings.baseUrl())
                .requestFactory(requestFactory(settings.connectTimeout(), Duration.ofSeconds(3))).build();
    }

    public String model() {
        return settings.model();
    }

    public String baseUrl() {
        return settings.baseUrl();
    }

    /** Cached health check (refreshed at most every 20 seconds). */
    public Health health() {
        Health cached = lastHealth.get();
        if (cached != null && cached.checkedAt().plus(HEALTH_CACHE).isAfter(clock.instant())) {
            return cached;
        }
        Health fresh = probe();
        lastHealth.set(fresh);
        return fresh;
    }

    private Health probe() {
        try {
            JsonNode tags = healthClient.get().uri("/api/tags").retrieve().body(JsonNode.class);
            boolean installed = false;
            if (tags != null && tags.has("models")) {
                for (JsonNode model : tags.get("models")) {
                    String name = model.path("name").asString("");
                    if (name.equals(settings.model()) || name.equals(settings.model() + ":latest")) {
                        installed = true;
                        break;
                    }
                }
            }
            return new Health(true, installed,
                    installed ? "Model %s is available".formatted(settings.model())
                            : "Ollama is running but model %s is not installed (run: ollama pull %s)"
                                    .formatted(settings.model(), settings.model()),
                    clock.instant());
        } catch (RestClientException e) {
            log.debug("Ollama health probe failed: {}", e.getMessage());
            return new Health(false, false, "Ollama is not reachable at %s".formatted(settings.baseUrl()),
                    clock.instant());
        }
    }

    /**
     * Sends one chat completion with a JSON-schema constrained response.
     *
     * @return the assistant message content (a JSON document)
     * @throws LlmException when the call fails or returns no content
     */
    public String chatJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("stream", false);
        body.put("format", jsonSchema);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("keep_alive", settings.keepAlive());
        body.put("options", Map.of("temperature", settings.temperature(), "num_ctx", settings.contextWindow()));
        try {
            JsonNode response = restClient.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? null : response.path("message").path("content").asString(null);
            if (content == null || content.isBlank()) {
                throw new LlmException("LLM returned an empty response");
            }
            return content;
        } catch (RestClientException e) {
            lastHealth.set(null);
            throw new LlmException("LLM call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Blocking HttpURLConnection-based factory: one local endpoint, no connection
     * pool or selector needed.
     */
    /**
     * Loads the model into memory (an empty generate request) so the first user
     * request does not pay
     * the model load time. Failures are ignored: the agent falls back or reports
     * unavailability later.
     */
    public void warmUp() {
        try {
            restClient.post().uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", settings.model(), "keep_alive", settings.keepAlive(),
                            "options", Map.of("num_ctx", settings.contextWindow())))
                    .retrieve()
                    .toBodilessEntity();
            log.info("LLM {} is loaded and kept warm for {}", settings.model(), settings.keepAlive());
        } catch (RestClientException e) {
            log.info("LLM warm-up skipped: {}", e.getMessage());
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /**
     * Raised when the LLM cannot produce a usable answer; the agent then falls
     * back.
     */
    public static class LlmException extends RuntimeException {

        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
