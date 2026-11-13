package com.etheller.warsmash.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Formulas measured in the original game (1.29).
 */
class JassRandomTest {

	/** A stream that hands out fixed 32-bit draws and counts them. */
	private static final class Fixed extends Random {
		private final int[] draws;
		private int used;

		Fixed(final int... draws) {
			this.draws = draws;
		}

		@Override
		protected int next(final int bits) {
			return this.draws[this.used++] >>> (32 - bits);
		}
	}

	@Test
	void reversedBoundsAddTheRangeToTheFirstBound() {
		assertEquals(7, JassRandom.intFromDraw(0, 7, 3));
		assertEquals(10, JassRandom.intFromDraw(0xA0000000, 7, 3));
		assertEquals(11, JassRandom.intFromDraw(0xFFFFFFFF, 7, 3));
		assertEquals(3, JassRandom.intFromDraw(0xC0000000, -5, 5));
	}

	@Test
	void wholeRangesUseTheDrawDirectly() {
		for (final int draw : new int[] { 0, 1, 0x12345678, 0x80000000, 0xFFFFFFFF }) {
			assertEquals(draw >>> 1, JassRandom.intFromDraw(draw, 0, Integer.MAX_VALUE));
			assertEquals(Integer.MIN_VALUE + (draw >>> 1), JassRandom.intFromDraw(draw, Integer.MIN_VALUE, -1));
			assertEquals(Integer.MIN_VALUE, JassRandom.intFromDraw(draw, Integer.MIN_VALUE, Integer.MAX_VALUE));
		}
	}

	@Test
	void realUsesTheLow23BitsOfTheDraw() {
		assertEquals(5f, JassRandom.realFromDraw(0xFF800000, 5f, 2f));
		assertEquals(5f + (3f * 0.5f), JassRandom.realFromDraw(0x00400000, 5f, 2f));
		assertEquals(2f + (3f * 0.5f), JassRandom.realFromDraw(0xAB400000, 2f, 5f));
	}

	@Test
	void equalBoundsDoNotDraw() {
		final Fixed random = new Fixed(0x80000000, 0x40000000);
		assertEquals(4, JassRandom.nextInt(random, 4, 4));
		assertEquals(2.5f, JassRandom.nextReal(random, 2.5f, 2.5f));
		assertEquals(0, random.used);
		assertEquals(0x40000000, JassRandom.nextInt(random, 0, Integer.MAX_VALUE));
		assertEquals(1, random.used);
		JassRandom.nextReal(random, 0f, 1f);
		assertEquals(2, random.used);
	}
}
