package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

class TextTagAlphaTest {
	private static TextTag tag(final int alpha, final boolean permanent, final float fadepoint, final float age) {
		final TextTag tag = new TextTag(new Vector3(), new Vector2(), "text", Color.WHITE, 1000, 0, 0.024f, 0);
		tag.setColor(255, 255, 255, alpha);
		tag.setPermanent(permanent);
		tag.setFadeStart(fadepoint);
		tag.setLifetime(age);
		return tag;
	}

	@Test
	void permanentTagIsOpaqueWhateverItsAlpha() {
		assertEquals(1f, tag(128, true, 900, 0).getDrawAlpha(), 1e-6f);
		assertEquals(1f, tag(0, true, 900, 0).getDrawAlpha(), 1e-6f);
		assertEquals(1f, tag(255, true, 0, 500).getDrawAlpha(), 1e-6f);
	}

	@Test
	void tagBeforeItsFadeTakesTheAlphaOfItsColor() {
		assertEquals(128 / 255f, tag(128, false, 900, 0).getDrawAlpha(), 1e-6f);
		assertEquals(0f, tag(0, false, 900, 0).getDrawAlpha(), 1e-6f);
	}

	@Test
	void fadingTagTakesTheShareOfItsFadeLeftInsteadOfItsAlpha() {
		assertEquals(0.5f, tag(255, false, 0, 500).getDrawAlpha(), 1e-6f);
		assertEquals(0.5f, tag(128, false, 0, 500).getDrawAlpha(), 1e-6f);
		assertEquals(0.8f, tag(64, false, 0, 200).getDrawAlpha(), 1e-6f);
	}

	@Test
	void permanentFlagIsReadWhenDrawing() {
		final TextTag tag = tag(128, true, 0, 500);
		tag.setPermanent(false);
		assertEquals(0.5f, tag.getDrawAlpha(), 1e-6f);
	}
}
