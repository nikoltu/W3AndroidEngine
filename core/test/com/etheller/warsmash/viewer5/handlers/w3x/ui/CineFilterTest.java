package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CBlendMode;

class CineFilterTest {
	@Test
	void aFilterRestoredFromItsSavedStateIsTheFilterThatWasSaved() {
		final CineFilter filter = new CineFilter();
		filter.setTexture("Textures/Black_mask.blp");
		filter.setBlendMode(CBlendMode.ADDITIVE);
		filter.setTexMapFlags(3);
		filter.setStartUV(0.1f, 0.2f, 0.3f, 0.4f);
		filter.setEndUV(0.5f, 0.6f, 0.7f, 0.8f);
		filter.setStartColor(1, 2, 3, 4);
		filter.setEndColor(5, 6, 7, 8);
		filter.setDuration(2.5f);
		filter.display(true);
		filter.update(1.25f);

		final CineFilter restored = new CineFilter();
		assertFalse(restored.isDisplayed());
		restored.restore(filter.getTexturePath(), filter.capture());

		assertTrue(restored.isDisplayed());
		assertEquals(filter.getTexturePath(), restored.getTexturePath());
		assertArrayEquals(filter.capture(), restored.capture());
	}

	@Test
	void aFilterThatWasNotDisplayedStaysHidden() {
		final CineFilter restored = new CineFilter();
		restored.display(true);
		restored.restore(null, new CineFilter().capture());
		assertFalse(restored.isDisplayed());
	}
}
