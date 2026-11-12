package com.etheller.warsmash.viewer5.handlers.w3x.camera;

import java.util.List;
import java.util.function.IntSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions;

public final class GameCameraManager extends CameraManager {
	private Vector2 quickPosition;
	private static final float TWO_PI = (float) Math.PI * 2;
	// Reforged map cameras may set near Z to 0, which makes the projection matrix non-invertible.
	private static final float MIN_NEAR_Z = 16f;
	// Measured on 1.29: the game camera keeps far Z at 100 or more, the field of view between 20 and 120 degrees and
	// the target distance at 100 or more, whatever a script sets; camera setups keep the values as set.
	private static final float MIN_FAR_Z = 100f;
	private static final float MIN_FOV = 20f;
	private static final float MAX_FOV = 120f;
	private static final float MIN_DISTANCE = 100f;
	private static final float PAN_SPEED = 3000f;
	private static final CameraRates INFINITE_CAMERA_RATES = new CameraRates(Float.POSITIVE_INFINITY,
			Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
			Float.POSITIVE_INFINITY);
	private final CameraPreset[] presets;
	private final CameraSetup[] presetCameras;
	private final CameraSetup[] presetCamerasInsert;
	private final CameraSetup[] presetCamerasDelete;
	private final CameraRates cameraRates;
	public final CameraPanControls cameraPanControls;
	private Rectangle cameraBounds;
	private int currentPreset = 0;
	private float fov;
	private float projectionHeightScale = 1;
	private boolean started;
	private float targetZOffset = 0.0f;
	private RenderUnit targetControllerUnit;
	private float targetControllerXOffset;
	private float targetControllerYOffset;
	private boolean targetControllerInheritOrientation;
	private CustomCameraSetup customSetup;
	private CameraRates customCameraRates;
	private Vector2 panDestination;
	private Vector2 panRate;
	private Float zOffsetDestination;
	private float zOffsetRate;
	private final CameraNoise sourceNoise = new CameraNoise(1.7f);
	private final CameraNoise targetNoise = new CameraNoise(0.9f);
	private final Vector3 noisyEye = new Vector3();
	private final Vector3 noisyTarget = new Vector3();
	private final MovingState renderState = new MovingState();
	private IntSupplier stepClock;
	private GroundHeight groundHeight;
	private int steppedTick;
	private float renderLead;
	private float roll;
	private float localPitch;
	private float localYaw;
	private float localRoll;
	private final Vector3 viewForward = new Vector3();
	private final Vector3 viewUp = new Vector3();
	private final Vector3 viewRight = new Vector3();
	private final Vector3 viewTemp = new Vector3();
	private final Vector3 viewLookAt = new Vector3();
	private com.etheller.warsmash.viewer5.handlers.mdx.Camera cinematicCamera;
	private MdxModel cinematicModel;
	private float cinematicTime;
	private float cinematicYaw;
	private boolean cinematicOnTarget;
	private float cinematicRoll;
	private final float[] cinematicTemp = new float[3];

	public GameCameraManager(final CameraPreset[] presets, final CameraRates cameraRates) {
		this.presets = presets;
		this.cameraRates = new CameraRates(cameraRates.aoa, cameraRates.fov, cameraRates.rotation * 3,
				cameraRates.distance, cameraRates.forward, cameraRates.strafe);
		this.cameraPanControls = new CameraPanControls();

		this.presetCameras = new CameraSetup[presets.length];
		this.presetCamerasInsert = new CameraSetup[presets.length];
		this.presetCamerasDelete = new CameraSetup[presets.length];
		for (int i = 0; i < presets.length; i++) {
			this.presetCameras[i] = getSetup(presets[i], false, false);
			this.presetCamerasInsert[i] = getSetup(presets[i], true, false);
			this.presetCamerasDelete[i] = getSetup(presets[i], false, true);
		}
	}

	private CameraSetup getSetup(final CameraPreset preset, final boolean insert, final boolean delete) {
		return new CameraSetup(preset.getAoa(), preset.getFov(), preset.getRotation(insert, delete), 0,
				Math.max(1200, preset.getDistance()), preset.getFarZ(), preset.getNearZ(), preset.getHeight());
	}

	public void setCameraBounds(final Rectangle cameraBounds) {
		this.cameraBounds = cameraBounds;
	}

	@Override
	public void updateCamera() {
		if (this.stepClock == null) {
			advance(Gdx.graphics.getDeltaTime());
			placeCamera(this.noisyEye, this.noisyTarget);
			return;
		}
		syncToGameStep();
		// The game moves the camera a step at a time; the frame shows it part of the way into the next step, so it
		// moves smoothly between steps.
		this.renderState.save(this);
		advance(Math.min(WarsmashConstants.SIMULATION_STEP_TIME, this.renderLead));
		placeCamera(this.noisyEye, this.noisyTarget);
		this.renderState.restore(this);
	}

	/**
	 * Moves the camera with the game: once per game step, by the step's time, whatever the frame rate. What a script
	 * reads from the camera natives is then the same on every machine and in a replay.
	 */
	public void setStepClock(final IntSupplier stepClock, final GroundHeight groundHeight) {
		this.stepClock = stepClock;
		this.groundHeight = groundHeight;
		this.steppedTick = stepClock.getAsInt();
		updateView();
	}

	/**
	 * Brings the camera up to the current game step. The camera natives call it before they read.
	 */
	public void syncToGameStep() {
		if (this.stepClock == null) {
			return;
		}
		final int tick = this.stepClock.getAsInt();
		while (this.steppedTick < tick) {
			advance(WarsmashConstants.SIMULATION_STEP_TIME);
			this.steppedTick++;
			this.renderLead = Math.max(0, this.renderLead - WarsmashConstants.SIMULATION_STEP_TIME);
		}
		this.steppedTick = tick;
	}

	/**
	 * Time of the frame: how far into the next game step the frame shows the camera, or, while the game is paused
	 * (no steps), the camera moving on its own time as in the original.
	 */
	public void frameTime(final float deltaTime, final boolean gamePaused) {
		if (gamePaused) {
			syncToGameStep();
			this.renderLead = 0;
			advance(deltaTime);
		}
		else {
			this.renderLead = Math.min(2 * WarsmashConstants.SIMULATION_STEP_TIME, this.renderLead + deltaTime);
		}
	}

	private void advance(final float deltaTime) {
		final CameraSetup setup = getCurrentSetup();
		// The game starts with the camera already at its preset, not moving towards it.
		final CameraRates cameraRate = this.started ? getCurrentRates() : CameraRates.INFINITY;
		this.started = true;
		if (this.panDestination != null) {
			this.target.x = applyAtRate(this.target.x, this.panDestination.x, this.panRate.x, deltaTime);
			this.target.y = applyAtRate(this.target.y, this.panDestination.y, this.panRate.y, deltaTime);
			// A pan left running would keep pulling the camera back after the player moves it some other way.
			if ((this.target.x == this.panDestination.x) && (this.target.y == this.panDestination.y)) {
				clearPan();
			}
		}
		if (this.zOffsetDestination != null) {
			this.targetZOffset = applyAtRate(this.targetZOffset, this.zOffsetDestination, this.zOffsetRate,
					deltaTime);
			if (this.targetZOffset == this.zOffsetDestination) {
				this.zOffsetDestination = null;
			}
		}
		followTargetController();
		clampTargetToBounds();
		moveFields(setup, cameraRate, deltaTime);
		this.sourceNoise.advance(deltaTime);
		this.targetNoise.advance(deltaTime);
		if (this.cinematicCamera != null) {
			this.cinematicTime += deltaTime;
		}
		updateView();
	}

	private void followTargetController() {
		if (this.targetControllerUnit == null) {
			return;
		}
		final boolean drawn = this.renderState.saved;
		// The game follows the unit where the game has it; a frame follows the unit where it is drawn.
		this.target.x = (drawn ? this.targetControllerUnit.getX()
				: this.targetControllerUnit.getSimulationUnit().getX()) + this.targetControllerXOffset;
		this.target.y = (drawn ? this.targetControllerUnit.getY()
				: this.targetControllerUnit.getSimulationUnit().getY()) + this.targetControllerYOffset;
	}

	private void moveFields(final CameraSetup cameraPreset, final CameraRates cameraRate, final float deltaTime) {
		final float newHorizontalAngle;
		if (this.targetControllerInheritOrientation && (this.targetControllerUnit != null)) {
			newHorizontalAngle = (float) Math.toRadians(this.targetControllerUnit.getSimulationUnit().getFacing() - 90);
		}
		else {
			newHorizontalAngle = (float) Math.toRadians(cameraPreset.getRotation() - 90);
		}
		this.horizontalAngle = applyAtRateAngle(this.horizontalAngle, newHorizontalAngle,
				(float) Math.toRadians(cameraRate.rotation), deltaTime);
		this.distance = applyAtRate(this.distance, Math.max(MIN_DISTANCE, cameraPreset.getDistance()),
				cameraRate.distance, deltaTime);
		this.verticalAngle = applyAtRateAngle(this.verticalAngle, (float) Math.toRadians(cameraPreset.getAoa() - 270),
				(float) Math.toRadians(cameraRate.aoa), deltaTime);
		final float fovDegrees = Math.min(MAX_FOV, Math.max(MIN_FOV, cameraPreset.getFov()));
		this.fov = applyAtRate(this.fov, (float) Math.toRadians(fovDegrees / 2),
				(float) Math.toRadians(cameraRate.fov / 2), deltaTime);
		this.roll = applyAtRateAngle(this.roll, localRadians(cameraPreset.getRoll()),
				(float) Math.toRadians(cameraRate.roll), deltaTime);
		this.localPitch = applyAtRateAngle(this.localPitch, localRadians(cameraPreset.getLocalPitch()),
				(float) Math.toRadians(cameraRate.localPitch), deltaTime);
		this.localYaw = applyAtRateAngle(this.localYaw, localRadians(cameraPreset.getLocalYaw()),
				(float) Math.toRadians(cameraRate.localYaw), deltaTime);
		this.localRoll = applyAtRateAngle(this.localRoll, localRadians(cameraPreset.getLocalRoll()),
				(float) Math.toRadians(cameraRate.localRoll), deltaTime);
	}

	private static float localRadians(final float degrees) {
		return (float) Math.toRadians(normalizeDegrees(degrees));
	}

	/**
	 * Eye and target where the camera natives report them, from the camera's current values.
	 */
	private void updateView() {
		if (this.groundHeight != null) {
			this.target.z = this.groundHeight.at(this.target.x, this.target.y)
					+ this.presets[this.currentPreset].getHeight() + this.targetZOffset;
		}
		// Scripts read the eye position, so it is worked out the same on every platform rather than by the
		// platform's own sine in a quaternion: (0, 0, 1) turned about x by the vertical angle, then about z.
		final double sinVertical = StrictMath.sin(this.verticalAngle);
		final double cosVertical = StrictMath.cos(this.verticalAngle);
		final double sinHorizontal = StrictMath.sin(this.horizontalAngle);
		final double cosHorizontal = StrictMath.cos(this.horizontalAngle);
		this.position.set(this.target.x + (float) (this.distance * sinVertical * sinHorizontal),
				this.target.y - (float) (this.distance * sinVertical * cosHorizontal),
				this.target.z + (float) (this.distance * cosVertical));
		this.noisyEye.set(this.position);
		this.noisyTarget.set(this.target);
		this.sourceNoise.apply(this.noisyEye);
		this.targetNoise.apply(this.noisyTarget);
		if (this.cinematicCamera != null) {
			placeCinematicCamera();
		}
	}

	/**
	 * SetCinematicCamera: the first camera of the model, playing its first sequence from the call on. Measured on
	 * 1.29: the model's camera stands on the game camera's target, which it follows, turned about the vertical by the
	 * sum of the game camera's rotations at every call so far (the turns add up from call to call, also across a
	 * reset); a call while a model camera already runs puts it at height 0 instead of the target's height. In the
	 * call itself the camera natives read the model's own coordinates. The camera fields keep reading the game
	 * camera's.
	 */
	public void setCinematicCamera(final com.etheller.warsmash.viewer5.handlers.mdx.Camera modelCamera,
			final MdxModel model) {
		syncToGameStep();
		this.cinematicOnTarget = this.cinematicCamera == null;
		this.cinematicCamera = modelCamera;
		this.cinematicModel = model;
		this.cinematicYaw = normalizeDegrees(this.cinematicYaw + currentRotationDegrees());
		this.cinematicTime = 0;
		this.noisyEye.set(modelCamera.position[0], modelCamera.position[1], modelCamera.position[2]);
		this.noisyTarget.set(modelCamera.targetPosition[0], modelCamera.targetPosition[1],
				modelCamera.targetPosition[2]);
	}

	public boolean isCinematicCamera() {
		return this.cinematicCamera != null;
	}

	private void placeCinematicCamera() {
		final com.etheller.warsmash.viewer5.handlers.mdx.Camera modelCamera = this.cinematicCamera;
		final int counter = (int) (this.cinematicTime * 1000);
		final List<Sequence> sequences = this.cinematicModel.getSequences();
		int sequence = -1;
		int frame = 0;
		if (!sequences.isEmpty()) {
			sequence = 0;
			final long[] interval = sequences.get(0).getInterval();
			final long length = interval[1] - interval[0];
			if (length <= 0) {
				frame = (int) interval[0];
			}
			else if (sequences.get(0).getFlags() == 0) {
				frame = (int) (interval[0] + (counter % length));
			}
			else {
				frame = (int) Math.min(interval[1], interval[0] + counter);
			}
		}
		modelCamera.getPositionTranslation(this.cinematicTemp, sequence, frame, counter);
		cinematicToWorld(this.noisyEye, modelCamera.position);
		modelCamera.getTargetTranslation(this.cinematicTemp, sequence, frame, counter);
		cinematicToWorld(this.noisyTarget, modelCamera.targetPosition);
		modelCamera.getRotation(this.cinematicTemp, sequence, frame, counter);
		this.cinematicRoll = this.cinematicTemp[0];
	}

	private void cinematicToWorld(final Vector3 out, final float[] modelPoint) {
		final double x = modelPoint[0] + this.cinematicTemp[0];
		final double y = modelPoint[1] + this.cinematicTemp[1];
		final double yaw = Math.toRadians(this.cinematicYaw);
		final double cos = StrictMath.cos(yaw);
		final double sin = StrictMath.sin(yaw);
		out.set(this.target.x + (float) ((x * cos) - (y * sin)), this.target.y + (float) ((x * sin) + (y * cos)),
				(this.cinematicOnTarget ? this.target.z : 0) + modelPoint[2] + this.cinematicTemp[2]);
	}

	private void placeCamera(final Vector3 eye, final Vector3 lookAt) {
		if (this.cinematicCamera != null) {
			// The model's own view, as the unit portraits show a model's camera.
			this.camera.perspective(this.cinematicCamera.fieldOfView * 0.75f, this.camera.getAspect(),
					Math.max(1f, this.cinematicCamera.nearClippingPlane),
					Math.max(this.cinematicCamera.nearClippingPlane + 1f, this.cinematicCamera.farClippingPlane));
			if (this.cinematicRoll == 0) {
				this.camera.moveToAndFace(eye, lookAt, this.worldUp);
				return;
			}
			final Vector3 forward = this.viewForward.set(lookAt).sub(eye).nor();
			final Vector3 right = this.viewRight.set(forward).crs(this.worldUp);
			if (right.len2() < 1e-8f) {
				right.set(1, 0, 0);
			}
			right.nor();
			final Vector3 up = this.viewUp.set(right).crs(forward).nor();
			rotateTowards(up, right, this.cinematicRoll);
			this.camera.moveToAndFace(eye, lookAt, up);
			return;
		}
		final CameraSetup setup = getCurrentSetup();
		this.camera.perspective(this.projectionHeightScale == 1 ? this.fov
				: 2 * (float) StrictMath.atan(StrictMath.tan(this.fov / 2) * this.projectionHeightScale), this.camera.getAspect(), Math.max(MIN_NEAR_Z, setup.getNearZ()),
				Math.max(MIN_FAR_Z, setup.getFarZ()));
		if ((this.roll == 0) && (this.localPitch == 0) && (this.localYaw == 0) && (this.localRoll == 0)) {
			this.camera.moveToAndFace(eye, lookAt, this.worldUp);
			return;
		}
		// The roll turns the camera about the line from the eye to the target; measured on 1.29, a positive roll turns
		// the picture anticlockwise. Reforged local angles then turn the view about the eye, in the camera's own frame:
		// yaw about the camera's up, then pitch about its right, then roll about the view. The eye and the target the
		// natives report stay where they are.
		final Vector3 forward = this.viewForward.set(lookAt).sub(eye).nor();
		final Vector3 right = this.viewRight.set(forward).crs(this.worldUp);
		if (right.len2() < 1e-8f) {
			right.set((float) StrictMath.cos(this.horizontalAngle), (float) StrictMath.sin(this.horizontalAngle), 0);
		}
		right.nor();
		final Vector3 up = this.viewUp.set(right).crs(forward).nor();
		rotateTowards(up, right, this.roll);
		right.set(forward).crs(up).nor();
		rotateTowards(forward, right, -this.localYaw);
		right.set(forward).crs(up).nor();
		rotateTowards(forward, up, this.localPitch);
		up.set(right).crs(forward).nor();
		rotateTowards(up, right, this.localRoll);
		this.viewLookAt.set(eye).add(forward);
		this.camera.moveToAndFace(eye, this.viewLookAt, up);
	}

	/**
	 * Turns a unit vector by the angle towards a unit vector perpendicular to it.
	 */
	private void rotateTowards(final Vector3 vector, final Vector3 towards, final float angle) {
		final float cos = (float) StrictMath.cos(angle);
		final float sin = (float) StrictMath.sin(angle);
		this.viewTemp.set(towards).scl(sin);
		vector.scl(cos).add(this.viewTemp).nor();
	}

	private CameraRates getCurrentRates() {
		if (this.customCameraRates != null) {
			return this.customCameraRates;
		}
		return this.cameraRates;
	}

	private CameraSetup getCurrentSetup() {
		CameraSetup setup;
		if (this.customSetup != null) {
			setup = this.customSetup;
		}
		else if (this.cameraPanControls.insertDown && !this.cameraPanControls.deleteDown) {
			setup = this.presetCamerasInsert[this.currentPreset];
		}
		else if (!this.cameraPanControls.insertDown && this.cameraPanControls.deleteDown) {
			setup = this.presetCamerasDelete[this.currentPreset];
		}
		else {
			setup = this.presetCameras[this.currentPreset];
		}
		return setup;
	}

	/**
	 * A setup applied at once: its fields jump to the values without waiting for the next step.
	 */
	private void applyFieldsNow(final CameraSetup cameraPreset, final CameraRates cameraRate) {
		moveFields(cameraPreset, cameraRate, 0);
		updateView();
	}

	public void setSourceNoise(final float magnitude, final float velocity, final boolean verticalOnly) {
		syncToGameStep();
		this.sourceNoise.set(magnitude, velocity, verticalOnly);
	}

	public void setTargetNoise(final float magnitude, final float velocity, final boolean verticalOnly) {
		syncToGameStep();
		this.targetNoise.set(magnitude, velocity, verticalOnly);
	}

	/**
	 * Measured on 1.29: the camera position natives report the shaken camera, not the steady one, and a new position
	 * only once the camera has moved there (not in the same call).
	 */
	public float getNoisyTargetX() {
		syncToGameStep();
		return this.noisyTarget.x;
	}

	public float getNoisyTargetY() {
		syncToGameStep();
		return this.noisyTarget.y;
	}

	public float getNoisyTargetZ() {
		syncToGameStep();
		return this.noisyTarget.z;
	}

	public float getEyeX() {
		syncToGameStep();
		return this.noisyEye.x;
	}

	public float getEyeY() {
		syncToGameStep();
		return this.noisyEye.y;
	}

	public float getEyeZ() {
		syncToGameStep();
		return this.noisyEye.z;
	}

	public interface GroundHeight {
		float at(float x, float y);
	}

	private static final class CameraNoise {
		private final float phaseOffset;
		private float magnitude;
		private float velocity;
		private boolean verticalOnly;
		private float phase;

		private CameraNoise(final float phaseOffset) {
			this.phaseOffset = phaseOffset;
		}

		private void set(final float magnitude, final float velocity, final boolean verticalOnly) {
			this.magnitude = magnitude;
			this.velocity = velocity;
			this.verticalOnly = verticalOnly;
		}

		private void advance(final float deltaTime) {
			if (this.magnitude == 0) {
				return;
			}
			// Velocity is how many world units a second the point sweeps, so the phase advances relative to the size.
			this.phase += (deltaTime * this.velocity) / Math.max(this.magnitude, 1f);
		}

		private void apply(final Vector3 point) {
			if (this.magnitude == 0) {
				return;
			}
			// StrictMath: the same shake on every machine, as scripts can read it.
			final float t = this.phase + this.phaseOffset;
			point.z += this.magnitude * (float) StrictMath.sin(t * 1.3f);
			if (!this.verticalOnly) {
				point.x += this.magnitude * (float) StrictMath.sin(t);
				point.y += this.magnitude * (float) StrictMath.cos(t * 0.7f);
			}
		}
	}

	/**
	 * What moves between game steps, kept aside while a frame shows the camera part of the way into the next step.
	 */
	private static final class MovingState {
		private boolean saved;
		private float horizontalAngle;
		private float verticalAngle;
		private float distance;
		private float fov;
		private float roll;
		private float localPitch;
		private float localYaw;
		private float localRoll;
		private final Vector3 target = new Vector3();
		private float targetZOffset;
		private Float zOffsetDestination;
		private Vector2 panDestination;
		private Vector2 panRate;
		private float sourcePhase;
		private float targetPhase;
		private final Vector3 position = new Vector3();
		private final Vector3 noisyEye = new Vector3();
		private final Vector3 noisyTarget = new Vector3();
		private boolean started;
		private float cinematicTime;
		private float cinematicRoll;

		private void save(final GameCameraManager camera) {
			this.saved = true;
			this.horizontalAngle = camera.horizontalAngle;
			this.verticalAngle = camera.verticalAngle;
			this.distance = camera.distance;
			this.fov = camera.fov;
			this.roll = camera.roll;
			this.localPitch = camera.localPitch;
			this.localYaw = camera.localYaw;
			this.localRoll = camera.localRoll;
			this.target.set(camera.target);
			this.targetZOffset = camera.targetZOffset;
			this.zOffsetDestination = camera.zOffsetDestination;
			this.panDestination = camera.panDestination;
			this.panRate = camera.panRate;
			this.sourcePhase = camera.sourceNoise.phase;
			this.targetPhase = camera.targetNoise.phase;
			this.position.set(camera.position);
			this.noisyEye.set(camera.noisyEye);
			this.noisyTarget.set(camera.noisyTarget);
			this.started = camera.started;
			this.cinematicTime = camera.cinematicTime;
			this.cinematicRoll = camera.cinematicRoll;
		}

		private void restore(final GameCameraManager camera) {
			camera.horizontalAngle = this.horizontalAngle;
			camera.verticalAngle = this.verticalAngle;
			camera.distance = this.distance;
			camera.fov = this.fov;
			camera.roll = this.roll;
			camera.localPitch = this.localPitch;
			camera.localYaw = this.localYaw;
			camera.localRoll = this.localRoll;
			camera.target.set(this.target);
			camera.targetZOffset = this.targetZOffset;
			camera.zOffsetDestination = this.zOffsetDestination;
			camera.panDestination = this.panDestination;
			camera.panRate = this.panRate;
			camera.sourceNoise.phase = this.sourcePhase;
			camera.targetNoise.phase = this.targetPhase;
			camera.position.set(this.position);
			camera.noisyEye.set(this.noisyEye);
			camera.noisyTarget.set(this.noisyTarget);
			camera.started = this.started;
			camera.cinematicTime = this.cinematicTime;
			camera.cinematicRoll = this.cinematicRoll;
			this.saved = false;
		}
	}

	public static float applyAtRate(final float oldValue, final float newValue, final float rate) {
		return applyAtRate(oldValue, newValue, rate, Gdx.graphics.getDeltaTime());
	}

	private static float applyAtRate(final float oldValue, final float newValue, float rate, final float deltaTime) {
		if (Float.isInfinite(rate)) {
			return newValue;
		}
		rate *= deltaTime;
		final float deltaDistance = newValue - oldValue;
		if (Math.abs(deltaDistance) < rate) {
			return newValue;
		}
		else {
			return oldValue + (Math.signum(deltaDistance) * rate);
		}
	}

	private static float applyAtRateAngle(final float oldValue, final float newValue, float rate,
			final float deltaTime) {
		if (Float.isInfinite(rate)) {
			return newValue;
		}
		rate *= deltaTime;
		final float deltaDistance = newValue - oldValue;
		final float absDistance = Math.abs(deltaDistance);
		if ((absDistance <= rate) || ((TWO_PI - absDistance) <= rate)) {
			return newValue;
		}
		else {
			float signum = Math.signum(deltaDistance);
			if (absDistance > Math.PI) {
				signum *= -1;
			}
			return (oldValue + (signum * rate)) % TWO_PI;
		}
	}

	public void resize(final Rectangle viewport) {
		this.camera.viewport(viewport);
	}

	/**
	 * The picture's centre and scale come from a smaller area than the viewport that is drawn into (the classic top
	 * bar shows the world in its corners): the field of view spans this much less than the viewport's height.
	 */
	public void setProjectionHeightScale(final float scale) {
		this.projectionHeightScale = scale;
	}

	public void applyVelocity(final float deltaTime, boolean up, boolean down, boolean left, boolean right) {
		if (this.targetControllerUnit == null) {
			final float velocityX;
			final float velocityY;
			final float scrollFactor = this.cameraPanControls.up || this.cameraPanControls.down
					|| this.cameraPanControls.left || this.cameraPanControls.right
							? GameOptions.get().getKeyboardScrollFactor()
							: GameOptions.get().getMouseScrollFactor();
			up |= this.cameraPanControls.up;
			down |= this.cameraPanControls.down;
			left |= this.cameraPanControls.left;
			right |= this.cameraPanControls.right;
			if (up) {
				if (down) {
					velocityY = 0;
				}
				else {
					velocityY = this.cameraRates.forward;
					clearPan();
				}
			}
			else if (down) {
				velocityY = -this.cameraRates.forward;
				clearPan();
			}
			else {
				velocityY = 0;
			}
			if (right) {
				if (left) {
					velocityX = 0;
				}
				else {
					velocityX = this.cameraRates.strafe;
					clearPan();
				}
			}
			else if (left) {
				velocityX = -this.cameraRates.strafe;
				clearPan();
			}
			else {
				velocityX = 0;
			}
			this.target.add(velocityX * scrollFactor * deltaTime, velocityY * scrollFactor * deltaTime, 0);
		}
		clampTargetToBounds();
	}

	private void clampTargetToBounds() {
		if (this.cameraBounds != null) {
			if (this.target.x < this.cameraBounds.x) {
				this.target.x = this.cameraBounds.x;
			}
			if (this.target.y < this.cameraBounds.y) {
				this.target.y = this.cameraBounds.y;
			}
			if (this.target.x > (this.cameraBounds.x + this.cameraBounds.width)) {
				this.target.x = this.cameraBounds.x + this.cameraBounds.width;
			}
			if (this.target.y > (this.cameraBounds.y + this.cameraBounds.height)) {
				this.target.y = this.cameraBounds.y + this.cameraBounds.height;
			}
		}
	}

	private void clearPan() {
		this.panDestination = null;
		this.panRate = null;
	}

	public void updateTargetZ(final float groundHeight) {
		this.target.z = groundHeight + this.presets[this.currentPreset].getHeight() + this.targetZOffset;
	}

	public void scrolled(final int amount) {
		this.currentPreset -= amount;
		if (this.currentPreset < 0) {
			this.currentPreset = 0;
		}
		if (this.currentPreset >= this.presets.length) {
			this.currentPreset = this.presets.length - 1;
		}
		clearCustomSetup();
	}

	public boolean keyDown(final int keycode) {
		if (keycode == Input.Keys.LEFT) {
			this.cameraPanControls.left = true;
			return true;
		}
		else if (keycode == Input.Keys.RIGHT) {
			this.cameraPanControls.right = true;
			return true;
		}
		else if (keycode == Input.Keys.DOWN) {
			this.cameraPanControls.down = true;
			return true;
		}
		else if (keycode == Input.Keys.UP) {
			this.cameraPanControls.up = true;
			return true;
		}
		else if (keycode == Input.Keys.INSERT) {
			this.cameraPanControls.insertDown = true;
			clearCustomSetup();
			return true;
		}
		else if (keycode == Input.Keys.FORWARD_DEL) {
			this.cameraPanControls.deleteDown = true;
			clearCustomSetup();
			return true;
		}
		return false;
	}

	public boolean keyUp(final int keycode) {
		if (keycode == Input.Keys.LEFT) {
			this.cameraPanControls.left = false;
			return true;
		}
		else if (keycode == Input.Keys.RIGHT) {
			this.cameraPanControls.right = false;
			return true;
		}
		else if (keycode == Input.Keys.DOWN) {
			this.cameraPanControls.down = false;
			return true;
		}
		else if (keycode == Input.Keys.UP) {
			this.cameraPanControls.up = false;
			return true;
		}
		else if (keycode == Input.Keys.INSERT) {
			this.cameraPanControls.insertDown = false;
			clearCustomSetup();
			return true;
		}
		else if (keycode == Input.Keys.FORWARD_DEL) {
			this.cameraPanControls.deleteDown = false;
			clearCustomSetup();
			return true;
		}
		return false;
	}

	private void clearCustomSetup() {
		this.customSetup = null;
		this.customCameraRates = null;
		clearPan();
	}

	public Rectangle getCameraBounds() {
		return this.cameraBounds;
	}

	public void setTargetController(final RenderUnit targetControllerUnit, final float xoffset, final float yoffset,
			final boolean inheritOrientation) {
		syncToGameStep();
		this.targetControllerUnit = targetControllerUnit;
		this.targetControllerXOffset = xoffset;
		this.targetControllerYOffset = yoffset;
		this.targetControllerInheritOrientation = inheritOrientation;

	}

	public RenderUnit getTargetControllerUnit() {
		return this.targetControllerUnit;
	}

	public void setTargetZOffset(final float targetZOffset) {
		syncToGameStep();
		this.zOffsetDestination = null;
		this.targetZOffset = targetZOffset;
	}

	public void setTargetZOffset(final float targetZOffset, final float duration) {
		syncToGameStep();
		if (duration <= 0) {
			setTargetZOffset(targetZOffset);
			return;
		}
		final float rate = Math.abs((targetZOffset - this.targetZOffset) / duration);
		this.zOffsetDestination = targetZOffset;
		this.zOffsetRate = rate;
	}

	private float currentAoaDegrees() {
		return normalizeDegrees((float) Math.toDegrees(this.verticalAngle) + 270);
	}

	private float currentRotationDegrees() {
		return normalizeDegrees((float) Math.toDegrees(this.horizontalAngle) + 90);
	}

	private float currentRollDegrees() {
		return normalizeDegrees((float) Math.toDegrees(this.roll));
	}

	private static float normalizeDegrees(final float degrees) {
		final float normalized = degrees % 360;
		return normalized < 0 ? normalized + 360 : normalized;
	}

	/**
	 * The camera as it is now, the way GetCameraField reports it: angles in radians.
	 */
	public float getField(final CCameraField field) {
		syncToGameStep();
		switch (field) {
		case TARGET_DISTANCE:
			return this.distance;
		case ANGLE_OF_ATTACK:
			return (float) Math.toRadians(currentAoaDegrees());
		case ROTATION:
			return (float) Math.toRadians(currentRotationDegrees());
		case FIELD_OF_VIEW:
			return this.fov * 2;
		case ZOFFSET:
			return this.targetZOffset;
		case ROLL:
			return (float) Math.toRadians(currentRollDegrees());
		case FARZ:
			return Math.max(MIN_FAR_Z, getCurrentSetup().getFarZ());
		case NEARZ:
			return getCurrentSetup().getField(field);
		case LOCAL_PITCH:
			return this.localPitch;
		case LOCAL_YAW:
			return this.localYaw;
		case LOCAL_ROLL:
			return this.localRoll;
		default:
			return 0;
		}
	}

	/**
	 * SetCameraField: one field of the game camera moves linearly to the value over the duration (angles in
	 * degrees), the others keep going where they were going.
	 */
	public void setField(final CCameraField field, final float value, final float duration) {
		syncToGameStep();
		if (field == CCameraField.ZOFFSET) {
			setTargetZOffset(value, duration);
			return;
		}
		final CameraSetup current = getCurrentSetup();
		final CustomCameraSetup setup = new CustomCameraSetup(current.getAoa(), current.getFov(),
				current.getRotation(), current.getRoll(), current.getDistance(), current.getFarZ(),
				current.getNearZ(), current.getHeight());
		if (current instanceof CustomCameraSetup) {
			setup.setDestPositionX(((CustomCameraSetup) current).getDestPositionX());
			setup.setDestPositionY(((CustomCameraSetup) current).getDestPositionY());
		}
		setup.setField(CCameraField.LOCAL_PITCH, current.getLocalPitch());
		setup.setField(CCameraField.LOCAL_YAW, current.getLocalYaw());
		setup.setField(CCameraField.LOCAL_ROLL, current.getLocalRoll());
		setup.setField(field, value);
		final CameraRates rates = getCurrentRates();
		float aoaRate = rates.aoa;
		float fovRate = rates.fov;
		float rotationRate = rates.rotation;
		float distanceRate = rates.distance;
		float rollRate = rates.roll;
		float localPitchRate = rates.localPitch;
		float localYawRate = rates.localYaw;
		float localRollRate = rates.localRoll;
		final boolean instant = duration <= 0;
		switch (field) {
		case ANGLE_OF_ATTACK: {
			float delta = Math.abs(normalizeDegrees(value) - currentAoaDegrees());
			delta = Math.min(delta, 360 - delta);
			aoaRate = instant ? Float.POSITIVE_INFINITY : delta / duration;
			break;
		}
		case ROTATION: {
			float delta = Math.abs(normalizeDegrees(value) - currentRotationDegrees());
			delta = Math.min(delta, 360 - delta);
			rotationRate = instant ? Float.POSITIVE_INFINITY : delta / duration;
			break;
		}
		case FIELD_OF_VIEW:
			fovRate = instant ? Float.POSITIVE_INFINITY
					: Math.abs(value - (float) Math.toDegrees(this.fov * 2)) / duration;
			break;
		case TARGET_DISTANCE:
			distanceRate = instant ? Float.POSITIVE_INFINITY : Math.abs(value - this.distance) / duration;
			break;
		case ROLL:
			rollRate = instant ? Float.POSITIVE_INFINITY : localDegreesTo(value, this.roll) / duration;
			break;
		case LOCAL_PITCH:
			localPitchRate = instant ? Float.POSITIVE_INFINITY : localDegreesTo(value, this.localPitch) / duration;
			break;
		case LOCAL_YAW:
			localYawRate = instant ? Float.POSITIVE_INFINITY : localDegreesTo(value, this.localYaw) / duration;
			break;
		case LOCAL_ROLL:
			localRollRate = instant ? Float.POSITIVE_INFINITY : localDegreesTo(value, this.localRoll) / duration;
			break;
		default:
			break;
		}
		this.customSetup = setup;
		this.customCameraRates = new CameraRates(aoaRate, fovRate, rotationRate, distanceRate,
				this.cameraRates.forward, this.cameraRates.strafe, rollRate, localPitchRate, localYawRate,
				localRollRate);
		if (instant) {
			applyFieldsNow(setup, this.customCameraRates);
		}
	}

	public void adjustField(final CCameraField field, final float offset, final float duration) {
		syncToGameStep();
		final float current;
		switch (field) {
		case ANGLE_OF_ATTACK:
			current = currentAoaDegrees();
			break;
		case ROTATION:
			current = currentRotationDegrees();
			break;
		case FIELD_OF_VIEW:
			current = (float) Math.toDegrees(this.fov * 2);
			break;
		case ROLL:
			current = currentRollDegrees();
			break;
		case LOCAL_PITCH:
		case LOCAL_YAW:
		case LOCAL_ROLL:
			current = (float) Math.toDegrees(getField(field));
			break;
		default:
			current = getField(field);
			break;
		}
		setField(field, current + offset, duration);
	}

	public void applyCameraSetupForceDuration(final CustomCameraSetup cameraSetup, final boolean doPan,
			final float forceDuration) {
		syncToGameStep();
		if (cameraSetup == null) {
			return;
		}
		this.customSetup = cameraSetup;
		setTargetZOffset(cameraSetup.getHeight(), forceDuration);
		if (forceDuration > 0) {
			this.customCameraRates = ratesTowards(cameraSetup, forceDuration);
			if (doPan) {
				panToTimed(cameraSetup.getDestPositionX(), cameraSetup.getDestPositionY(), forceDuration);
			}
		}
		else {
			applyFieldsNow(this.customSetup, CameraRates.INFINITY);
			if (doPan) {
				setTarget(cameraSetup.getDestPositionX(), cameraSetup.getDestPositionY());
			}
		}
	}

	/**
	 * Rates that bring each field from where the camera is now to the setup's value in the duration. Angles go the
	 * short way round, as the camera moves them: from an angle of attack of 335 to one of 10 it tilts 35 degrees over
	 * the whole duration. A rate from the difference of the two numbers (325) got there in a ninth of the time,
	 * while the eye was still close to the target, under the ground.
	 */
	private CameraRates ratesTowards(final CameraSetup setup, final float duration) {
		final float aoaRate = angleBetween(setup.getAoa(), currentAoaDegrees()) / duration;
		final float rotationRate = angleBetween(setup.getRotation(), currentRotationDegrees()) / duration;
		final float fovDegrees = Math.min(MAX_FOV, Math.max(MIN_FOV, setup.getFov()));
		final float fovRate = Math.abs(fovDegrees - (float) Math.toDegrees(this.fov * 2)) / duration;
		final float distanceRate = Math.abs(Math.max(MIN_DISTANCE, setup.getDistance()) - this.distance) / duration;
		return new CameraRates(aoaRate, fovRate, rotationRate, distanceRate, this.cameraRates.forward,
				this.cameraRates.strafe, localDegreesTo(setup.getRoll(), this.roll) / duration,
				localDegreesTo(setup.getLocalPitch(), this.localPitch) / duration,
				localDegreesTo(setup.getLocalYaw(), this.localYaw) / duration,
				localDegreesTo(setup.getLocalRoll(), this.localRoll) / duration);
	}

	private static float localDegreesTo(final float degrees, final float currentRadians) {
		return angleBetween(degrees, (float) Math.toDegrees(currentRadians));
	}

	private static float angleBetween(final float degrees, final float otherDegrees) {
		final float difference = Math.abs(normalizeDegrees(degrees) - normalizeDegrees(otherDegrees));
		return Math.min(difference, 360 - difference);
	}

	public void applyCameraSetup(CustomCameraSetup cameraSetup, boolean doPan, boolean panTimed) {
		syncToGameStep();
		this.customSetup = cameraSetup;
		if (doPan) {
			if (panTimed) {
				panTo(cameraSetup.getDestPositionX(), cameraSetup.getDestPositionY());
			}
			else {
				setTarget(cameraSetup.getDestPositionX(), cameraSetup.getDestPositionY());
			}
		}
	}

	public void resetToGameCamera(final float duration) {
		syncToGameStep();
		if (this.cinematicCamera != null) {
			// Measured on 1.29: the game camera comes back looking at the point the model's camera looked at.
			setTarget(this.noisyTarget.x, this.noisyTarget.y);
			this.cinematicCamera = null;
			this.cinematicModel = null;
		}
		clearCustomSetup();
		setTargetZOffset(0, duration);
		if (duration == 0) {
			this.customCameraRates = new CameraRates(9999, 9999, 9999, 9999, this.cameraRates.forward,
					this.cameraRates.strafe, 9999, 9999, 9999, 9999);
			// Measured on 1.29: the game camera is back in the same call, not at the next step.
			applyFieldsNow(getCurrentSetup(), CameraRates.INFINITY);
		}
		else {
			this.customCameraRates = ratesTowards(getCurrentSetup(), duration);
		}
	}

	public void panToTimed(float x, float y, float duration) {
		syncToGameStep();
		if (duration == 0) {
			setTarget(x, y);
		}
		else {
			this.panDestination = clampToBounds(new Vector2(x, y));
			final float yRate = (y - this.target.y) / duration;
			final float xRate = (x - this.target.x) / duration;
			this.panRate = new Vector2(Math.abs(xRate), Math.abs(yRate));
		}
	}

	// An instant placement replaces any timed pan still running; otherwise that pan keeps moving the camera off the
	// new spot (Human01 ends its intro on a pan's path, far from Arthas).
	private void setTarget(float x, float y) {
		clearPan();
		this.target.x = x;
		this.target.y = y;
	}

	public void panTo(float x, float y) {
		syncToGameStep();
		clearPan();
		this.panDestination = clampToBounds(new Vector2(x, y));
		// Measured on 1.29: a pan goes straight to the point, 3000 a second along the way, whatever the rates.
		final float dx = Math.abs(this.panDestination.x - this.target.x);
		final float dy = Math.abs(this.panDestination.y - this.target.y);
		final float length = (float) Math.sqrt((dx * dx) + (dy * dy));
		if (length > 0) {
			this.panRate = new Vector2((PAN_SPEED * dx) / length, (PAN_SPEED * dy) / length);
		}
		else {
			this.panRate = new Vector2(PAN_SPEED, PAN_SPEED);
		}
	}

	public void stopPan() {
		syncToGameStep();
		clearPan();
	}

	public void jumpTo(final float x, final float y) {
		syncToGameStep();
		clearPan();
		final Vector2 destination = clampToBounds(new Vector2(x, y));
		setTarget(destination.x, destination.y);
	}

	private Vector2 clampToBounds(final Vector2 point) {
		if (this.cameraBounds != null) {
			point.x = Math.max(this.cameraBounds.x, Math.min(point.x, this.cameraBounds.x + this.cameraBounds.width));
			point.y = Math.max(this.cameraBounds.y, Math.min(point.y, this.cameraBounds.y + this.cameraBounds.height));
		}
		return point;
	}

	public void setQuickPosition(final float x, final float y) {
		if (this.quickPosition == null) {
			this.quickPosition = new Vector2();
		}
		this.quickPosition.set(x, y);
	}

	/**
	 * Measured on 1.29: Space jumps to the place, it does not pan there.
	 *
	 * @return false when nothing has set a quick position yet
	 */
	public boolean jumpToQuickPosition() {
		if (this.quickPosition == null) {
			return false;
		}
		jumpTo(this.quickPosition.x, this.quickPosition.y);
		return true;
	}
}