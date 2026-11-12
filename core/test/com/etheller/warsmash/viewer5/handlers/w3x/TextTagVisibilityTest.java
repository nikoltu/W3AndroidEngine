package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.TextTagConfigType;

class TextTagVisibilityTest {
	private static TextTag tag() {
		return new TextTag(new Vector3(), new Vector2(), "text", Color.WHITE, 1000, 0, 0.024f, 0);
	}

	@Test
	void tagWithoutAReceivingPlayerIsShownToEveryone() {
		final TextTag tag = tag();
		assertTrue(tag.isShownTo(0));
		assertTrue(tag.isShownTo(7));
	}

	@Test
	void tagForAPlayerIsShownToThatPlayerOnly() {
		final TextTag tag = tag();
		tag.setReceivingPlayer(2);
		assertTrue(tag.isShownTo(2));
		assertFalse(tag.isShownTo(0));
		assertFalse(tag.isShownTo(3));
	}

	@Test
	void resourceTextIsForTheReceiverAndCombatTextForAll() {
		assertTrue(TextTagConfigType.GOLD.isShownToReceiverOnly());
		assertTrue(TextTagConfigType.GOLD_BOUNTY.isShownToReceiverOnly());
		assertTrue(TextTagConfigType.LUMBER.isShownToReceiverOnly());
		assertFalse(TextTagConfigType.CRITICAL_STRIKE.isShownToReceiverOnly());
		assertFalse(TextTagConfigType.MANA_BURN.isShownToReceiverOnly());
		assertFalse(TextTagConfigType.MISS_TEXT.isShownToReceiverOnly());
	}
}
