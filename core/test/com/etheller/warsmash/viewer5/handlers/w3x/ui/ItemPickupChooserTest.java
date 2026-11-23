package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CPrimaryAttribute;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.ItemPickupChooser.Candidate;

class ItemPickupChooserTest {
	private static final int[] STR = { 2, 0, 0 };
	private static final int[] AGI = { 0, 2, 0 };
	private static final int[] INT = { 0, 0, 2 };
	private static final int[] ALL = { 2, 2, 2 };
	private static final int[] NONE = { 0, 0, 0 };

	private static Candidate hero(final CPrimaryAttribute attribute, final double distance) {
		return new Candidate(true, attribute, true, distance * distance);
	}

	@Test
	void heroWithMatchingPrimaryAttributeTakesItEvenWhenFarthest() {
		final java.util.List<Candidate> group = Arrays.asList(hero(CPrimaryAttribute.STRENGTH, 500),
				hero(CPrimaryAttribute.AGILITY, 400), hero(CPrimaryAttribute.INTELLIGENCE, 300));
		assertEquals(0, ItemPickupChooser.choose(STR, group));
		assertEquals(1, ItemPickupChooser.choose(AGI, group));
		assertEquals(2, ItemPickupChooser.choose(INT, group));
	}

	@Test
	void allStatsAndNoStatsFallBackToTheNearestHero() {
		final java.util.List<Candidate> group = Arrays.asList(hero(CPrimaryAttribute.STRENGTH, 500),
				hero(CPrimaryAttribute.AGILITY, 400), hero(CPrimaryAttribute.INTELLIGENCE, 300));
		assertEquals(2, ItemPickupChooser.choose(NONE, group));
		assertEquals(0, ItemPickupChooser.choose(ALL, Arrays.asList(hero(CPrimaryAttribute.STRENGTH, 100),
				hero(CPrimaryAttribute.AGILITY, 400))));
		assertEquals(2, ItemPickupChooser.choose(ALL, group));
	}

	@Test
	void nearestOfTwoMatchingHeroesWinsAndAMismatchFallsBackToNearest() {
		assertEquals(1, ItemPickupChooser.choose(STR, Arrays.asList(hero(CPrimaryAttribute.STRENGTH, 300),
				hero(CPrimaryAttribute.STRENGTH, 200))));
		assertEquals(1, ItemPickupChooser.choose(STR, Arrays.asList(hero(CPrimaryAttribute.AGILITY, 300),
				hero(CPrimaryAttribute.INTELLIGENCE, 200))));
	}

	@Test
	void unitsWithoutRoomNeverTakeItAndNonHeroesComeLast() {
		final Candidate fullStrength = new Candidate(true, CPrimaryAttribute.STRENGTH, false, 10);
		final Candidate footman = new Candidate(false, null, true, 1);
		assertEquals(1, ItemPickupChooser.choose(STR, Arrays.asList(fullStrength, footman)));
		assertEquals(1, ItemPickupChooser.choose(STR,
				Arrays.asList(footman, hero(CPrimaryAttribute.AGILITY, 900))));
		assertEquals(-1, ItemPickupChooser.choose(STR, Collections.<Candidate>emptyList()));
	}

	@Test
	void whenNobodyHasRoomTheNearestUnitGoesAndShowsTheOneError() {
		final Candidate farHero = new Candidate(true, CPrimaryAttribute.STRENGTH, false, 90);
		final Candidate nearHero = new Candidate(true, CPrimaryAttribute.AGILITY, false, 10);
		assertEquals(0, ItemPickupChooser.choose(STR, Arrays.asList(farHero)));
		assertEquals(1, ItemPickupChooser.choose(STR, Arrays.asList(farHero, nearHero)));
	}
}
