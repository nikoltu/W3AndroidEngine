package com.etheller.warsmash.util;

import java.util.Random;

/**
 * How the script natives GetRandomInt and GetRandomReal turn one 32-bit draw of the stream into a value.
 */
public final class JassRandom {
	private JassRandom() {
	}

	/** Equal bounds return without a draw; the size of the range is |high - low| in 32 bits, added to the first bound. */
	public static int nextInt(final Random random, final int low, final int high) {
		if (low == high) {
			return low;
		}
		return intFromDraw(random.nextInt(), low, high);
	}

	public static float nextReal(final Random random, final float low, final float high) {
		if (low == high) {
			return low;
		}
		return realFromDraw(random.nextInt(), low, high);
	}

	public static int intFromDraw(final int draw, final int low, final int high) {
		final int distance = high >= low ? high - low : low - high;
		// (MinInt, MaxInt) has distance -1, so the range wraps to 0 and the result is always the first bound.
		final long range = (distance + 1) & 0xFFFFFFFFL;
		return low + (int) (((draw & 0xFFFFFFFFL) * range) >>> 32);
	}

	public static float realFromDraw(final int draw, final float low, final float high) {
		final float unit = Float.intBitsToFloat(0x3F800000 | (draw & 0x7FFFFF)) - 1f;
		return low + (Math.abs(high - low) * unit);
	}
}
