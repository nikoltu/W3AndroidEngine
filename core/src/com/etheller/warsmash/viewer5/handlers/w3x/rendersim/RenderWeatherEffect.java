package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.ParticleEmitter2Object;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentWeather;

/**
 * A weather effect over a rect. The emitter only covers the part of the rect around the camera, so a weather over
 * the whole map costs no more than one over the screen.
 */
public final class RenderWeatherEffect implements SimulationRenderComponentWeather {
	// Calibration: the table's emission rate counts per square of this side, fitted to the original's rain density.
	private static final float RATE_AREA_SIDE = 300;
	// Calibration: how far around the camera target weather is emitted.
	private static final float VIEW_HALF_SIZE = 1536;
	private static final Vector3 rayHeap = new Vector3();
	private static final Quaternion tiltHeap = new Quaternion();
	private static final Quaternion tiltHeap2 = new Quaternion();

	private final War3ID weatherId;
	private final WeatherType type;
	private final float minX;
	private final float minY;
	private final float maxX;
	private final float maxY;
	private final MdxComplexInstance instance;
	private final ParticleEmitter2Object emitter;
	private UnitSound ambientSound;
	private long ambientSoundId = -1;
	private boolean ambientSoundTried;
	private boolean enabled;
	private boolean removed;
	private Object savedForm;

	public RenderWeatherEffect(final War3MapViewer viewer, final War3ID weatherId, final WeatherType type,
			final MdxModel model, final float minX, final float minY, final float maxX, final float maxY) {
		this.weatherId = weatherId;
		this.type = type;
		this.minX = Math.min(minX, maxX);
		this.minY = Math.min(minY, maxY);
		this.maxX = Math.max(minX, maxX);
		this.maxY = Math.max(minY, maxY);
		this.emitter = model.particleEmitters2.get(0);
		this.emitter.emissionRate = 0;
		this.instance = (MdxComplexInstance) model.addInstance();
		tiltHeap.setFromAxis(1, 0, 0, type.getTiltX());
		tiltHeap2.setFromAxis(0, 1, 0, type.getTiltY());
		this.instance.setLocalRotation(tiltHeap.mul(tiltHeap2));
		this.instance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		this.instance.setSequence(0);
		this.instance.setScene(viewer.worldScene);
	}

	public void update(final War3MapViewer viewer) {
		if (this.removed) {
			return;
		}
		final Camera camera = viewer.worldScene.camera;
		float targetX = camera.location.x;
		float targetY = camera.location.y;
		final Vector3 forward = rayHeap.set(camera.directionZ).scl(-1);
		if (forward.z < -0.01f) {
			final float ground = viewer.terrain.getGroundHeight(targetX, targetY);
			final float distance = (ground - camera.location.z) / forward.z;
			targetX += forward.x * distance;
			targetY += forward.y * distance;
		}
		final float left = Math.max(this.minX, targetX - VIEW_HALF_SIZE);
		final float right = Math.min(this.maxX, targetX + VIEW_HALF_SIZE);
		final float bottom = Math.max(this.minY, targetY - VIEW_HALF_SIZE);
		final float top = Math.min(this.maxY, targetY + VIEW_HALF_SIZE);
		if (!this.enabled || (left >= right) || (bottom >= top)) {
			this.emitter.emissionRate = 0;
			stopAmbientSound();
			return;
		}
		if ((this.ambientSoundId == -1) && !this.ambientSoundTried && (this.type.getAmbientSound() != null)) {
			// Weather ambience is heard over the whole view rather than from a point, and one try per time in view is
			// enough: a sound that cannot play now has no better chance on the next frame.
			this.ambientSoundTried = true;
			this.ambientSound = viewer.getUiSounds().getSound(this.type.getAmbientSound());
			if (this.ambientSound != null) {
				final AudioContext context = viewer.getUiAudioContext() != null ? viewer.getUiAudioContext()
						: viewer.worldScene.audioContext;
				this.ambientSoundId = this.ambientSound.play(context, targetX, targetY,
						viewer.terrain.getGroundHeight(targetX, targetY), true);
			}
		}
		final float width = right - left;
		final float length = top - bottom;
		this.emitter.width = width;
		this.emitter.length = length;
		final float areaRate = (this.type.getEmissionRate() * width * length) / (RATE_AREA_SIDE * RATE_AREA_SIDE);
		this.emitter.emissionRate = Math.min(areaRate, this.type.getMaxEmissionRate());
		final float centerX = (left + right) / 2;
		final float centerY = (bottom + top) / 2;
		this.instance.setLocation(centerX, centerY,
				viewer.terrain.getGroundHeight(centerX, centerY) + this.type.getHeight());
	}

	private void stopAmbientSound() {
		if (this.ambientSoundId != -1) {
			this.ambientSound.stop(this.ambientSoundId);
			this.ambientSoundId = -1;
		}
		this.ambientSoundTried = false;
	}

	@Override
	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public void remove() {
		if (!this.removed) {
			this.removed = true;
			this.emitter.emissionRate = 0;
			stopAmbientSound();
			this.instance.detach();
		}
	}

	@Override
	public boolean isRemoved() {
		return this.removed;
	}

	public War3ID getWeatherId() {
		return this.weatherId;
	}

	public float[] getRect() {
		return new float[] { this.minX, this.minY, this.maxX, this.maxY };
	}

	public Object getSavedForm() {
		return this.savedForm;
	}

	public void setSavedForm(final Object savedForm) {
		this.savedForm = savedForm;
	}
}
