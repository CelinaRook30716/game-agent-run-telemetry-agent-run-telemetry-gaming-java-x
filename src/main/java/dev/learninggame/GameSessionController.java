package dev.learninggame;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/game-agent")
class GameSessionController {
    private final OpenAIClient ai;
    private final InfraiTelemetry telemetry;

    GameSessionController(OpenAIClient ai, InfraiTelemetry telemetry) {
        this.ai = ai;
        this.telemetry = telemetry;
    }

    @PostMapping("/runs")
    RunResult run(@RequestBody PlayerAsset asset) {
        ChatCompletion completion = ai.chat().completions().create(
            ChatCompletionCreateParams.builder()
                .model("auto")
                .addSystemMessage("Review a player-created game asset for a classroom live event. Reply with one teacher-friendly sentence.")
                .addUserMessage(asset.title() + "\n" + asset.description())
                .build());

        long tokens = completion.usage().map(usage -> usage.totalTokens()).orElse(0L);
        String guidance = completion.choices().getFirst().message().content().orElse("");

        try {
            return ModerationQueue.decide(asset, guidance);
        } catch (AssetNeedsReview exception) {
            telemetry.recordFailure(asset.runKey(), tokens, exception);
            throw exception;
        }
    }

    @ExceptionHandler(AssetNeedsReview.class)
    ResponseEntity<Map<String, Object>> reviewRequired(AssetNeedsReview exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
            "state", "QUEUED_FOR_REVIEW",
            "reason", exception.getMessage()));
    }

    @ExceptionHandler(InfraiRequestException.class)
    ResponseEntity<Map<String, Object>> infraiRejected(InfraiRequestException exception) {
        int status = exception.status() >= 400 && exception.status() < 500 ? exception.status() : 502;
        return ResponseEntity.status(status).body(Map.of(
            "state", "REQUEST_REJECTED",
            "reason", exception.code()));
    }
}

record PlayerAsset(String runKey, String playerId, String title, String description, boolean liveEvent) {}
record RunResult(String state, String guidance) {}

final class ModerationQueue {
    private ModerationQueue() {}

    static RunResult decide(PlayerAsset asset, String guidance) {
        if (asset.liveEvent() && asset.description().toLowerCase().contains("spoiler")) {
            throw new AssetNeedsReview("Live-event spoiler requires an educator review");
        }
        return new RunResult("PUBLISHED", guidance);
    }
}

class AssetNeedsReview extends RuntimeException {
    AssetNeedsReview(String message) {
        super(message);
    }
}
