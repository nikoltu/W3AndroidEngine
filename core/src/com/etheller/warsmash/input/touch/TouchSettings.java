package com.etheller.warsmash.input.touch;

import com.etheller.warsmash.units.Element;

/**
 * Gesture thresholds from the [Touch] section of warsmash.ini. Distances are kept in pixels, converted once from dp
 * with the screen density, so the recognizer itself never deals with units.
 */
public final class TouchSettings {
	public final float tapSlopPx;
	public final float penSlopPx;
	public final long tapMaxMs;
	public final long longPressMs;
	public final long twoFingerWindowMs;
	public final long twoFingerTapMs;
	public final float pinchStartRatio;
	public final float pinchStepRatio;
	public final float buildAimOffsetPx;
	public final float ghostTapRadiusPx;
	public final float palmTouchMajorPx;
	public final PalmFilter.PenFingerPolicy penFingerPolicy;

	public TouchSettings(final float tapSlopPx, final float penSlopPx, final long tapMaxMs, final long longPressMs,
			final long twoFingerWindowMs, final long twoFingerTapMs, final float pinchStartRatio,
			final float pinchStepRatio, final float buildAimOffsetPx, final float ghostTapRadiusPx,
			final float palmTouchMajorPx, final PalmFilter.PenFingerPolicy penFingerPolicy) {
		this.tapSlopPx = tapSlopPx;
		this.penSlopPx = penSlopPx;
		this.tapMaxMs = tapMaxMs;
		this.longPressMs = longPressMs;
		this.twoFingerWindowMs = twoFingerWindowMs;
		this.twoFingerTapMs = twoFingerTapMs;
		this.pinchStartRatio = pinchStartRatio;
		this.pinchStepRatio = pinchStepRatio;
		this.buildAimOffsetPx = buildAimOffsetPx;
		this.ghostTapRadiusPx = ghostTapRadiusPx;
		this.palmTouchMajorPx = palmTouchMajorPx;
		this.penFingerPolicy = penFingerPolicy;
	}

	public static TouchSettings defaults(final float density) {
		return new TouchSettings(10 * density, 4 * density, 300, 300, 150, 250, 1.3f, 1.4f, 56 * density,
				48 * density, 40 * density, PalmFilter.PenFingerPolicy.IGNORE);
	}

	public static TouchSettings fromIni(final Element touch, final float density) {
		final TouchSettings defaults = defaults(density);
		if (touch == null) {
			return defaults;
		}
		return new TouchSettings(dp(touch, "TapSlopDp", defaults.tapSlopPx, density),
				dp(touch, "PenSlopDp", defaults.penSlopPx, density), ms(touch, "TapMaxMs", defaults.tapMaxMs),
				ms(touch, "LongPressMs", defaults.longPressMs),
				ms(touch, "TwoFingerWindowMs", defaults.twoFingerWindowMs),
				ms(touch, "TwoFingerTapMs", defaults.twoFingerTapMs),
				ratio(touch, "PinchStartRatio", defaults.pinchStartRatio),
				ratio(touch, "PinchStepRatio", defaults.pinchStepRatio),
				dp(touch, "BuildAimOffsetDp", defaults.buildAimOffsetPx, density),
				dp(touch, "GhostTapRadiusDp", defaults.ghostTapRadiusPx, density),
				dp(touch, "PalmTouchMajorDp", defaults.palmTouchMajorPx, density),
				"Allow".equalsIgnoreCase(touch.getField("PenFingerPolicy")) ? PalmFilter.PenFingerPolicy.ALLOW
						: PalmFilter.PenFingerPolicy.IGNORE);
	}

	private static float dp(final Element touch, final String key, final float fallbackPx, final float density) {
		final String value = touch.getField(key);
		return value.isEmpty() ? fallbackPx : Float.parseFloat(value) * density;
	}

	private static long ms(final Element touch, final String key, final long fallback) {
		final String value = touch.getField(key);
		return value.isEmpty() ? fallback : Long.parseLong(value);
	}

	private static float ratio(final Element touch, final String key, final float fallback) {
		final String value = touch.getField(key);
		return value.isEmpty() ? fallback : Float.parseFloat(value);
	}
}
