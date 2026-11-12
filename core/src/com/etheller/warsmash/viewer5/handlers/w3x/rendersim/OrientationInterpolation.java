package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

/**
 * We observe this table during gameplay but I haven't found it anywhere in the
 * data yet. So, I'm making my own.
 */
public enum OrientationInterpolation {
	OI0(0.07f, 0.2f, 999f, 0.2f, 1f, 0.85f),
	OI1(0.03f, 0.1f, 0.04f, 0.2f, 1f, 0.85f),
	OI2(0.015f, 1.0f, 999, 0.2f, 1f, 0.85f),
	OI3(0.005f, 0.1f, 0.0043f, 0.1f, 1f, 1f),
	OI4(0.04f, 0.15f, 0.01f, 0.2f, 1f, 0.85f),
	OI5(0.05f, 0.18f, 0.015f, 0.2f, 1f, 0.85f),
	OI6(0.1f, 0.3f, 0.1f, 0.2f, 1f, 0.85f),
	OI7(0.003f, 0.08f, 0.0027f, 0.1f, 1f, 1f),
	OI8(0.001f, 0.05f, 0.001f, 0.1f, 1f, 1f);

	public static final OrientationInterpolation[] VALUES = values();

	private float startingAcceleration;
	private float maxVelocity;
	private float endingNegativeAcceleration;
	private float endingAccelCutoff;
	private float startingAccelCutoff;
	private float settleRadius;
	private float settleExponent;
	private float brakingScale;

	private OrientationInterpolation(final float startingAcceleration, final float maxVelocity,
			final float endingNegativeAcceleration, final float settleRadius, final float settleExponent,
			final float brakingScale) {
		this.brakingScale = brakingScale;
		this.settleRadius = settleRadius;
		this.settleExponent = settleExponent;
		this.startingAcceleration = startingAcceleration;
		this.maxVelocity = maxVelocity;
		this.endingNegativeAcceleration = endingNegativeAcceleration;
		this.endingAccelCutoff = endingAccelCutoff(maxVelocity, endingNegativeAcceleration);
		this.startingAccelCutoff = endingAccelCutoff(maxVelocity, startingAcceleration);
	}

	public float getStartingAcceleration() {
		return this.startingAcceleration;
	}

	public float getMaxVelocity() {
		return this.maxVelocity;
	}

	public float getEndingNegativeAcceleration() {
		return this.endingNegativeAcceleration;
	}

	/** Radians left below which the turn creeps in more slowly than the constant deceleration alone would. */
	public float getSettleRadius() {
		return this.settleRadius;
	}

	/** What share of the table's ending deceleration the drawn facing brakes with. */
	public float getBrakingScale() {
		return this.brakingScale;
	}

	public float getSettleExponent() {
		return this.settleExponent;
	}

	public float getEndingAccelCutoff() {
		return this.endingAccelCutoff;
	}

	public float getStartingAccelCutoff() {
		return this.startingAccelCutoff;
	}

	private static float endingAccelCutoff(final float maxVelocity, final float endingAccel) {
		final float endingAccelFinishingTime = maxVelocity / endingAccel;
		final float endingDistanceRequired = (maxVelocity * endingAccelFinishingTime)
				- ((endingAccel / 2) * (endingAccelFinishingTime * endingAccelFinishingTime));
		return endingDistanceRequired;
	}
}
