package dev.learninggame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
class InfraiTelemetry {
    private final InfraiProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    InfraiTelemetry(InfraiProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newHttpClient());
    }

    InfraiTelemetry(InfraiProperties properties, ObjectMapper json, HttpClient http) {
        this.properties = properties;
        this.json = json;
        this.http = http;
    }

    void recordFailure(String runKey, long tokens, RuntimeException exception) {
        post("/v1/metrics/report", Map.of(
            "type", "counter",
            "name", "game.agent.wasted_tokens",
            "value", tokens
        ), runKey + ":tokens");

        post("/v1/errors/capture", Map.of(
            "message", exception.getMessage(),
            "level", "error",
            "fingerprint", new String[] {"game-agent", exception.getClass().getSimpleName()},
            "exception", Map.of(
                "type", exception.getClass().getSimpleName(),
                "message", exception.getMessage()
            ),
            "context", Map.of("run_key", runKey, "tokens", tokens)
        ), runKey + ":exception");
    }

    private JsonNode post(String path, Map<String, Object> payload, String idempotencyKey) {
        try {
            String body = json.writeValueAsString(payload);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl()).resolve(path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                JsonNode envelope = json.readTree(response.body());
                if (!envelope.path("ok").asBoolean()) {
                    if (response.statusCode() == 429 && attempt < 3) {
                        sleep(retryDelay(response, attempt));
                        continue;
                    }
                    JsonNode error = envelope.path("error");
                    throw new InfraiRequestException(
                        error.path("code").asText("REQUEST_REJECTED"),
                        error.path("message").asText("Infrai rejected the request"),
                        response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new InfraiRequestException("TRANSPORT_FAILURE", "Telemetry transport failed", response.statusCode());
                }
                return envelope.path("data");
            }
            throw new IllegalStateException("Retry loop ended unexpectedly");
        } catch (IOException e) {
            throw new InfraiRequestException("TRANSPORT_FAILURE", e.getMessage(), 503);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiRequestException("INTERRUPTED", "Telemetry request interrupted", 503);
        }
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
            .map(value -> Duration.ofSeconds(Long.parseLong(value)))
            .orElse(Duration.ofMillis(250L * (1L << attempt)));
    }

    private static void sleep(Duration delay) throws InterruptedException {
        Thread.sleep(delay);
    }
}

class InfraiRequestException extends RuntimeException {
    private final String code;
    private final int status;

    InfraiRequestException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    String code() { return code; }
    int status() { return status; }
}
