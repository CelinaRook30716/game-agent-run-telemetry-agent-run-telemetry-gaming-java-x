package dev.learninggame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModerationQueueTest {
    @Test
    void liveEventSpoilerMovesPlayerAssetToEducatorReview() {
        PlayerAsset asset = new PlayerAsset(
            "lesson-42-run-7", "player-9", "Volcano Quest",
            "A spoiler for the live-event final answer", true);

        AssetNeedsReview error = assertThrows(AssetNeedsReview.class,
            () -> ModerationQueue.decide(asset, "Suitable for a geology lesson."));

        assertEquals("Live-event spoiler requires an educator review", error.getMessage());
    }

    @Test
    void ordinaryPlayerAssetPublishesWithAgentGuidance() {
        PlayerAsset asset = new PlayerAsset(
            "lesson-42-run-8", "player-10", "Mineral Match",
            "Match each mineral to its classroom label", false);

        RunResult result = ModerationQueue.decide(asset, "Good fit for guided practice.");

        assertEquals("PUBLISHED", result.state());
        assertEquals("Good fit for guided practice.", result.guidance());
    }
}
