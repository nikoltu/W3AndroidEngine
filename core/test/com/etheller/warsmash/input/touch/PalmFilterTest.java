package com.etheller.warsmash.input.touch;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PalmFilterTest {
	private static PointerEvent down(final PointerEvent.Tool tool, final float touchMajor, final long time) {
		return new PointerEvent(0, tool, PointerEvent.Action.DOWN, 10, 10, 0, 1, touchMajor, 0, time);
	}

	private static PointerEvent up(final PointerEvent.Tool tool, final long time) {
		return new PointerEvent(0, tool, PointerEvent.Action.UP, 10, 10, 0, 1, 0, 0, time);
	}

	@Test
	void autoIgnoresSizeWithoutPen() {
		final PalmFilter filter = new PalmFilter(100, PalmFilter.PenFingerPolicy.IGNORE, PalmFilter.SizeRejection.AUTO);
		assertTrue(filter.accept(down(PointerEvent.Tool.FINGER, 2000, 0)));
	}

	@Test
	void autoDropsLargeContactOnPenDevice() {
		final PalmFilter filter = new PalmFilter(100, PalmFilter.PenFingerPolicy.IGNORE, PalmFilter.SizeRejection.AUTO);
		filter.setPenCapable(true);
		assertFalse(filter.accept(down(PointerEvent.Tool.FINGER, 101, 0)));
		assertFalse(filter.accept(up(PointerEvent.Tool.FINGER, 10)));
		assertTrue(filter.accept(down(PointerEvent.Tool.FINGER, 99, 20)));
	}

	@Test
	void autoActivatesOnceAPenWasSeen() {
		final PalmFilter filter = new PalmFilter(100, PalmFilter.PenFingerPolicy.ALLOW, PalmFilter.SizeRejection.AUTO);
		assertTrue(filter.accept(down(PointerEvent.Tool.FINGER, 500, 0)));
		filter.accept(up(PointerEvent.Tool.FINGER, 1));
		assertTrue(filter.accept(down(PointerEvent.Tool.STYLUS, 5, 2)));
		filter.accept(up(PointerEvent.Tool.STYLUS, 3));
		assertFalse(filter.accept(down(PointerEvent.Tool.FINGER, 500, 4)));
	}

	@Test
	void offAndZeroNeverDrop() {
		final PalmFilter off = new PalmFilter(100, PalmFilter.PenFingerPolicy.ALLOW, PalmFilter.SizeRejection.OFF);
		off.setPenCapable(true);
		assertTrue(off.accept(down(PointerEvent.Tool.FINGER, 5000, 0)));
		final PalmFilter zero = new PalmFilter(0, PalmFilter.PenFingerPolicy.ALLOW, PalmFilter.SizeRejection.ON);
		assertTrue(zero.accept(down(PointerEvent.Tool.FINGER, 5000, 0)));
	}

	@Test
	void onAlwaysDrops() {
		final PalmFilter filter = new PalmFilter(100, PalmFilter.PenFingerPolicy.ALLOW, PalmFilter.SizeRejection.ON);
		assertFalse(filter.accept(down(PointerEvent.Tool.FINGER, 101, 0)));
	}

	@Test
	void iniModeParsing() {
		assertEquals(PalmFilter.SizeRejection.OFF,
				TouchSettings.sizeRejection("off", PalmFilter.SizeRejection.AUTO));
		assertEquals(PalmFilter.SizeRejection.AUTO,
				TouchSettings.sizeRejection("", PalmFilter.SizeRejection.AUTO));
	}
}
