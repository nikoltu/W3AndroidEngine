package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;

class MapTypeUsageTest {
	private static final War3ID A = War3ID.fromString("nogm");
	private static final War3ID B = War3ID.fromString("nkol");
	private static final War3ID C = War3ID.fromString("ngst");

	@Test
	void typesComeEarliestMadeFirstAndOnlyTheFirstUnitOfATypeCounts() {
		final MapTypeUsage usage = new MapTypeUsage("function main takes nothing returns nothing");
		usage.created(A, 1500);
		usage.created(B, 500);
		usage.created(A, 700);
		assertEquals(Arrays.asList(B, A), usage.typesByTick());
	}

	@Test
	void theOpeningStepsAndTheLateGameAreNotKept() {
		final MapTypeUsage usage = new MapTypeUsage("another script");
		usage.created(A, 0);
		usage.created(B, 1);
		usage.created(C, MapTypeUsage.MAX_TICK + 1);
		assertEquals(0, usage.typesByTick().size());
		usage.created(C, MapTypeUsage.MAX_TICK);
		assertEquals(Arrays.asList(C), usage.typesByTick());
	}
}
