package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MeleeUISelectionVoiceTest {
	@Test
	void ownUnitsAndNeutralPassiveUnitsAnswerTheirSelection() {
		assertTrue(MeleeUIInput.isSelectionVoiced(3, 3, 28));
		// A shop Neutral Passive owns answers whatever race the map gave it.
		assertTrue(MeleeUIInput.isSelectionVoiced(27, 0, 28));
	}

	@Test
	void unitsOfOtherPlayersStaySilent() {
		assertFalse(MeleeUIInput.isSelectionVoiced(1, 0, 28));
		assertFalse(MeleeUIInput.isSelectionVoiced(24, 0, 28));
		assertFalse(MeleeUIInput.isSelectionVoiced(15, 0, 28));
		assertFalse(MeleeUIInput.isSelectionVoiced(15, 0, 28, false));
	}

	@Test
	void unitsOfAPlayerSharingControlAnswerTheirSelection() {
		assertTrue(MeleeUIInput.isSelectionVoiced(15, 0, 28, true));
	}
}
