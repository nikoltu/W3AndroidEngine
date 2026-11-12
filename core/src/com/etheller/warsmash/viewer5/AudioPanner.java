package com.etheller.warsmash.viewer5;

import com.etheller.warsmash.viewer5.AudioContext.Listener;

public abstract class AudioPanner {
	public Listener listener;
	public float x;
	public float y;
	public float z;

	public AudioPanner(final Listener listener) {
		this.listener = listener;
	}

	public void setPosition(final float x, final float y, final float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public void setDistances(final float maxDistance, final float refDistance) {
		// Reforged sound tables leave the cutoff of unit responses blank ("_"), which reads as 0: such a sound is
		// heard at any distance rather than nowhere.
		this.maxDistance = maxDistance > 0 ? maxDistance : Float.MAX_VALUE;
		this.refDistance = refDistance > 0 ? refDistance : 0;
		this.maxDistanceSq = maxDistance > 0 ? maxDistance * maxDistance : Float.POSITIVE_INFINITY;
	}

	public float maxDistance;
	public float refDistance;
	public float maxDistanceSq;
	/** Whether the sound's context places it in the game world. */
	public boolean placed;
	/** Whether the sound wants to be heard from its place (WANT3D), rather than as an interface sound. */
	public boolean wants3D = true;

	public void setWants3D(final boolean wants3D) {
		this.wants3D = wants3D;
	}

	/**
	 * @return whether the sound is heard from where it is, fading with distance from the camera
	 */
	public boolean isPositional() {
		return this.placed && this.wants3D;
	}

	/** The context the panner was made by; its loops follow the camera. */
	public AudioContext context;
	/** A live position (such as a unit's) a loop follows, or null for a fixed place. */
	public float[] followLocation;
	/** Whether a loop stays at the listener, as a 3D sound nobody placed does. */
	public boolean followListener;

	/** Moves the panner to where a followed loop is now. */
	public void refreshPosition() {
		if (this.followLocation != null) {
			setPosition(this.followLocation[0], this.followLocation[1], this.followLocation[2]);
		}
		else if (this.followListener) {
			setPosition(this.listener.getX(), this.listener.getY(), this.listener.getZ());
		}
	}

	public float getListenerDistance() {
		final float dx = this.listener.getX() - this.x;
		final float dy = this.listener.getY() - this.y;
		final float dz = this.listener.getZ() - this.z;
		return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	/**
	 * The loudness at the listener for a backend without 3D sound, as the original's sound library makes it: full
	 * within the minimum distance, then falling inversely with distance.
	 */
	public float getDistanceGain() {
		if (this.refDistance <= 0) {
			return 1f;
		}
		final float distance = getListenerDistance();
		if (distance <= this.refDistance) {
			return 1f;
		}
		return this.refDistance / distance;
	}

	public abstract void connect(AudioDestination destination);

	public boolean isWithinListenerDistance() {
		final float dx = this.listener.getX() - this.x;
		final float dy = this.listener.getY() - this.y;
		final float dz = this.listener.getZ() - this.z;
		return ((dx * dx) + (dy * dy) + (dz * dz)) <= this.maxDistanceSq;
	}
}
