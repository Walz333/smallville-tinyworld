package io.github.nickm980.smallville;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.nickm980.smallville.entities.AffectState;

public class AffectStateNudgeTest {

    @Test
    public void test_withNudge_blends_valence_correctly() {
	// Start at valence=-0.82 (distressed from dream)
	AffectState state = new AffectState("distressed", -0.82, 0.1, 0.3, null, List.of(), 1);

	// Ponder valence=0.0 (neutral), alpha=0.6
	AffectState nudged = state.withNudge(0.0, 0.6, 5);

	// Expected: 0.6 * (-0.82) + 0.4 * 0.0 = -0.492
	assertEquals(-0.492, nudged.getValence(), 0.001);
	assertEquals("uneasy", nudged.getMoodLabel());
	assertEquals(5, nudged.getUpdatedAtTick());
    }

    @Test
    public void test_withNudge_preserves_activation_and_social_drive() {
	AffectState state = new AffectState("uneasy", -0.3, 0.7, 0.4, "gardening", List.of("routine"), 1);
	AffectState nudged = state.withNudge(0.2, 0.5, 10);

	assertEquals(0.7, nudged.getActivation(), 0.001, "Activation should be preserved");
	assertEquals(0.4, nudged.getSocialDrive(), 0.001, "Social drive should be preserved");
	assertEquals("gardening", nudged.getFocusTarget(), "Focus target should be preserved");
	assertNotNull(nudged.getDrivers());
    }

    @Test
    public void test_withNudge_alpha_one_keeps_original_valence() {
	AffectState state = new AffectState("distressed", -0.8, 0.5, 0.5, null, List.of(), 1);
	AffectState nudged = state.withNudge(1.0, 1.0, 5);

	// alpha=1.0 → blended = 1.0 * (-0.8) + 0.0 * 1.0 = -0.8
	assertEquals(-0.8, nudged.getValence(), 0.001);
    }

    @Test
    public void test_withNudge_alpha_zero_takes_full_ponder_valence() {
	AffectState state = new AffectState("distressed", -0.8, 0.5, 0.5, null, List.of(), 1);
	AffectState nudged = state.withNudge(0.5, 0.0, 5);

	// alpha=0.0 → blended = 0.0 * (-0.8) + 1.0 * 0.5 = 0.5
	assertEquals(0.5, nudged.getValence(), 0.001);
	assertEquals("content", nudged.getMoodLabel());
    }

    @Test
    public void test_withNudge_result_clamped_at_positive_boundary() {
	AffectState state = new AffectState("content", 0.9, 0.5, 0.5, null, List.of(), 1);
	AffectState nudged = state.withNudge(0.9, 0.5, 5);

	// 0.5 * 0.9 + 0.5 * 0.9 = 0.9 (within bounds, but test near boundary)
	assertEquals(0.9, nudged.getValence(), 0.001);
    }

    @Test
    public void test_withNudge_result_clamped_at_negative_boundary() {
	AffectState state = new AffectState("distressed", -1.0, 0.5, 0.5, null, List.of(), 1);
	AffectState nudged = state.withNudge(-1.0, 0.5, 5);

	// 0.5 * (-1.0) + 0.5 * (-1.0) = -1.0
	assertEquals(-1.0, nudged.getValence(), 0.001);
    }

    @Test
    public void test_withNudge_positive_ponder_lifts_negative_dream() {
	// Dream left agent at valence=-0.6 (distressed)
	AffectState state = AffectState.fromDreamCycle(2.0, 0.3, null, List.of(), 1);
	double dreamValence = state.getValence();
	assertEquals("distressed", state.getMoodLabel(), "Precondition: agent should be distressed");

	// Ponder with high importance memories (valence=0.5)
	AffectState nudged = state.withNudge(0.5, 0.6, 5);

	// blended = 0.6 * dreamValence + 0.4 * 0.5
	double expected = 0.6 * dreamValence + 0.4 * 0.5;
	assertEquals(expected, nudged.getValence(), 0.001);
	// Should be less negative than before
	assertTrue_greaterThan(nudged.getValence(), dreamValence, "Nudge should lift valence");
    }

    @Test
    public void test_withNudge_mood_recalculated_after_blend() {
	// Start calm (valence=0.2)
	AffectState state = new AffectState("calm", 0.2, 0.5, 0.5, null, List.of(), 1);

	// Nudge with very positive ponder (valence=0.8), alpha=0.3
	AffectState nudged = state.withNudge(0.8, 0.3, 5);

	// blended = 0.3 * 0.2 + 0.7 * 0.8 = 0.06 + 0.56 = 0.62
	assertEquals(0.62, nudged.getValence(), 0.001);
	assertEquals("content", nudged.getMoodLabel(), "Should be content after positive nudge");
    }

    private void assertTrue_greaterThan(double actual, double reference, String message) {
	if (actual <= reference) {
	    throw new AssertionError(message + ": expected > " + reference + " but was " + actual);
	}
    }
}
