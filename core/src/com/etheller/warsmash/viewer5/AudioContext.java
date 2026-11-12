package com.etheller.warsmash.viewer5;

import java.util.ArrayList;
import java.util.List;

public class AudioContext {
	private boolean running = false;
	public Listener listener;
	public AudioDestination destination;
	/**
	 * Whether sounds of this context are placed in the game world, where they fade with distance from the camera,
	 * rather than in an interface.
	 */
	public boolean world;
	/** Which places of the world the player hears; null hears everywhere. */
	public SoundVisibility visibility;

	private static final int MAX_LOOPS = 64;
	private final List<PositionalLoop> loops = new ArrayList<>();

	void addLoop(final PositionalLoop loop) {
		if (this.loops.size() >= MAX_LOOPS) {
			this.loops.remove(0);
		}
		this.loops.add(loop);
	}

	/**
	 * Sets the loudness and side of the world loops from where the camera is now, and silences those out of range or
	 * in fog. Stopped loops are not reported here; setting the level of one does nothing, so they age out of the list.
	 */
	public void updateLoops() {
		for (int i = 0, n = this.loops.size(); i < n; i++) {
			this.loops.get(i).update();
		}
	}

	public static interface SoundVisibility {
		boolean isHeardAt(float x, float y);
	}

	public AudioContext(final Listener listener, final AudioDestination destination) {
		this.listener = listener;
		this.destination = destination;
	}

	public void suspend() {
		this.running = false;
	}

	public boolean isRunning() {
		return this.running;
	}

	public void resume() {
		this.running = true;
	}

	public static interface Listener {

		float getX();

		float getY();

		float getZ();

		public void setPosition(final float x, final float y, final float z);

		public void setOrientation(final float forwardX, final float forwardY, final float forwardZ, final float upX,
				final float upY, final float upZ);

		boolean is3DSupported();

		/**
		 * A listener for a backend without 3D sound: it keeps where the camera is and where it faces, so that a sound's
		 * loudness and side can be worked out before it plays.
		 */
		public static final class Placed implements Listener {
			private float x;
			private float y;
			private float z;
			private float forwardX;
			private float forwardY;
			private float forwardZ;
			private float upX;
			private float upY;
			private float upZ = 1;

			@Override
			public float getX() {
				return this.x;
			}

			@Override
			public float getY() {
				return this.y;
			}

			@Override
			public float getZ() {
				return this.z;
			}

			@Override
			public void setPosition(final float x, final float y, final float z) {
				this.x = x;
				this.y = y;
				this.z = z;
			}

			@Override
			public void setOrientation(final float forwardX, final float forwardY, final float forwardZ,
					final float upX, final float upY, final float upZ) {
				this.forwardX = forwardX;
				this.forwardY = forwardY;
				this.forwardZ = forwardZ;
				this.upX = upX;
				this.upY = upY;
				this.upZ = upZ;
			}

			/**
			 * @return -1 for a sound fully to the left of the camera, 1 fully to the right
			 */
			public float pan(final float soundX, final float soundY, final float soundZ) {
				final float rightX = (this.forwardY * this.upZ) - (this.forwardZ * this.upY);
				final float rightY = (this.forwardZ * this.upX) - (this.forwardX * this.upZ);
				final float rightZ = (this.forwardX * this.upY) - (this.forwardY * this.upX);
				final float rightLength = (float) Math
						.sqrt((rightX * rightX) + (rightY * rightY) + (rightZ * rightZ));
				final float dx = soundX - this.x;
				final float dy = soundY - this.y;
				final float dz = soundZ - this.z;
				final float length = (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
				if ((rightLength == 0) || (length == 0)) {
					return 0;
				}
				final float pan = ((dx * rightX) + (dy * rightY) + (dz * rightZ)) / (rightLength * length);
				return Math.max(-1f, Math.min(1f, pan));
			}

			@Override
			public boolean is3DSupported() {
				return false;
			}
		}

		Listener DO_NOTHING = new Listener() {
			private float x;
			private float y;
			private float z;

			@Override
			public void setPosition(final float x, final float y, final float z) {
				this.x = x;
				this.y = y;
				this.z = z;
			}

			@Override
			public float getX() {
				return x;
			}

			@Override
			public float getY() {
				return y;
			}

			@Override
			public float getZ() {
				return z;
			}

			@Override
			public void setOrientation(final float forwardX, final float forwardY, final float forwardZ,
					final float upX, final float upY, final float upZ) {

			}

			@Override
			public boolean is3DSupported() {
				return false;
			}
		};
	}

	public AudioPanner createPanner() {
		return createPanner(true);
	}

	public AudioPanner createPanner(final boolean stopWhenOutOfRange) {
		final AudioPanner panner = createPannerFor(stopWhenOutOfRange);
		panner.placed = this.world;
		panner.context = this;
		return panner;
	}

	private AudioPanner createPannerFor(final boolean stopWhenOutOfRange) {
		if (!stopWhenOutOfRange) {
			return new AudioPanner(this.listener) {
				@Override
				public void connect(final AudioDestination destination) {
				}

				@Override
				public boolean isWithinListenerDistance() {
					return true;
				}
			};
		}
		else {
			return new AudioPanner(this.listener) {
				@Override
				public void connect(final AudioDestination destination) {
				}
			};
		}
	}

	public AudioBufferSource createBufferSource() {
		return new AudioBufferSource();
	}

}
