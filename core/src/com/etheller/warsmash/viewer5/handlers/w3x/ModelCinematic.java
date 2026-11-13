package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.List;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;

/**
 * A model made as a whole scene (its own terrain, actors, camera and sounds) that plays shot after shot, each shot one
 * of its sequences. PlayModelCinematic fills the screen with it and shows the game again when the last shot is over;
 * the in-game variant puts the model in the world at a place and turns the game's camera into the model's, so the
 * interface and the world stay as they are. The map goes on meanwhile, so the trigger that asked for it can wait for
 * it.
 */
public final class ModelCinematic {
	private static final float MODEL_FOV_SCALE = 0.75f;
	private static final float DEFAULT_FOV = 70f;
	private static final float DEFAULT_NEAR = 16f;
	private static final float DEFAULT_FAR = 20000f;
	private static final float STEP_MILLISECONDS = WarsmashConstants.SIMULATION_STEP_TIME * 1000f;

	/**
	 * Where the cinematic is in time, counted in game steps from the step that asked for it. What the scripts ask is
	 * read from this alone, so it comes out the same on every machine, with or without a picture, and whether or not
	 * the player skips what is drawn.
	 */
	public static final class Timeline {
		private final long[] shotEnds;
		private final int startTick;

		private Timeline(final MdxModel model, final int startTick) {
			final List<Sequence> sequences = model.getSequences();
			final long origin = sequences.get(0).getInterval()[0];
			this.shotEnds = new long[sequences.size()];
			for (int i = 0; i < this.shotEnds.length; i++) {
				this.shotEnds[i] = sequences.get(i).getInterval()[1] - origin;
			}
			this.startTick = startTick;
		}

		public int getShotCount() {
			return this.shotEnds.length;
		}

		private float elapsed(final int tick) {
			return Math.max(0, tick - this.startTick) * STEP_MILLISECONDS;
		}

		/** @return the shot now playing, counted from 1, or 0 when the cinematic is over */
		public int getCurrentShot(final int tick) {
			final float elapsed = elapsed(tick);
			for (int i = 0; i < this.shotEnds.length; i++) {
				if (elapsed < this.shotEnds[i]) {
					return i + 1;
				}
			}
			return 0;
		}

		/** @return the seconds until the last shot ends */
		public float getRemainingSeconds(final int tick) {
			return Math.max(0f, (this.shotEnds[this.shotEnds.length - 1] - elapsed(tick)) / 1000f);
		}
	}

	private final War3MapViewer viewer;
	private final MdxModel model;
	private final Timeline timeline;
	private final boolean inGame;
	private final Scene scene;
	private final MdxComplexInstance instance;
	private final com.etheller.warsmash.viewer5.handlers.mdx.Camera[] shotCameras;
	private final float[] positionOffset = new float[3];
	private final float[] targetOffset = new float[3];
	private final float[] roll = new float[3];
	private final Vector3 eye = new Vector3();
	private final Vector3 target = new Vector3();
	private final Vector3 up = new Vector3();
	private final Vector3 right = new Vector3();
	private final Vector3 forward = new Vector3();
	private final Vector3 worldUp = new Vector3(0, 0, 1);
	private final Vector3 origin = new Vector3();
	private final Quaternion facing = new Quaternion();
	private int shot;
	private boolean started;
	private boolean finished;
	private boolean skipped;

	private ModelCinematic(final War3MapViewer viewer, final MdxModel model, final boolean inGame, final float x,
			final float y, final float z, final float rotationDegrees) {
		this.viewer = viewer;
		this.model = model;
		this.inGame = inGame;
		this.timeline = new Timeline(model, viewer.simulation.getGameTurnTick());
		this.scene = inGame ? viewer.worldScene : viewer.addWorldScene();
		if (!inGame) {
			this.scene.enableAudio();
		}
		this.instance = (MdxComplexInstance) model.addInstance();
		this.instance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
		this.instance.setSequence(0);
		this.origin.set(x, y, z);
		this.facing.setFromAxisRad(RenderMathUtils.VEC3_UNIT_Z, (float) Math.toRadians(rotationDegrees));
		if (inGame) {
			this.instance.setLocation(x, y, z);
			this.instance.rotate(this.facing);
		}
		this.instance.setScene(this.scene);
		this.shotCameras = shotCameras(model);
	}

	/**
	 * Each shot has a camera of its own: the camera named after the shot's number (Shot 020 has CAM_020), or else the
	 * camera in the shot's place in the list when there are as many as shots, or else the first.
	 */
	private static com.etheller.warsmash.viewer5.handlers.mdx.Camera[] shotCameras(final MdxModel model) {
		final List<Sequence> sequences = model.getSequences();
		final List<com.etheller.warsmash.viewer5.handlers.mdx.Camera> cameras = model.getCameras();
		final com.etheller.warsmash.viewer5.handlers.mdx.Camera[] result = new com.etheller.warsmash.viewer5.handlers.mdx.Camera[sequences.size()];
		if (cameras.isEmpty()) {
			return result;
		}
		for (int i = 0; i < result.length; i++) {
			final String number = trailingNumber(sequences.get(i).getName());
			com.etheller.warsmash.viewer5.handlers.mdx.Camera found = null;
			for (final com.etheller.warsmash.viewer5.handlers.mdx.Camera camera : cameras) {
				if (!number.isEmpty() && number.equals(trailingNumber(camera.name))) {
					found = camera;
					break;
				}
			}
			if (found == null) {
				found = cameras.size() == result.length ? cameras.get(i) : cameras.get(0);
			}
			result[i] = found;
		}
		return result;
	}

	private static String trailingNumber(final String name) {
		int start = name == null ? 0 : name.length();
		while ((start > 0) && Character.isDigit(name.charAt(start - 1))) {
			start--;
		}
		return name == null ? "" : name.substring(start);
	}

	/** @return the model of a cinematic when it is in the game data and has shots to play, else null */
	public static MdxModel load(final War3MapViewer viewer, final String path) {
		if ((path == null) || path.isEmpty()) {
			return null;
		}
		final String mdx = War3MapViewer.mdx(path);
		if (!viewer.dataSource.has(mdx) && !viewer.dataSource.has(War3MapViewer.mdl(mdx))) {
			Log.warn("The cinematic model " + path + " is not in the game data");
			return null;
		}
		final MdxModel model = viewer.loadModelMdx(path);
		if ((model == null) || model.getSequences().isEmpty()) {
			Log.warn("The cinematic model " + path + " cannot play");
			return null;
		}
		return model;
	}

	/** @return the cinematic now playing, or null when the model is not there */
	public static ModelCinematic play(final War3MapViewer viewer, final String path, final boolean inGame,
			final float x, final float y, final float z, final float rotationDegrees) {
		final MdxModel model = load(viewer, path);
		if (model == null) {
			return null;
		}
		return new ModelCinematic(viewer, model, inGame, x, y, z, rotationDegrees);
	}

	public MdxModel getModel() {
		return this.model;
	}

	public Timeline getTimeline() {
		return this.timeline;
	}

	public boolean isInGame() {
		return this.inGame;
	}

	public void skip() {
		this.skipped = true;
	}

	/** @return whether the last shot has played to its end or the player skipped it */
	public boolean isDone() {
		return this.skipped || this.finished;
	}

	/**
	 * Moves on to the next shot when one has ended. The model plays by the time of the picture; the first frame after
	 * loading would otherwise start the first shot a few seconds in, so the picture begins again at the very start.
	 */
	private void advance() {
		if (!this.started) {
			this.started = true;
			this.instance.setSequence(0);
			return;
		}
		if (!this.instance.sequenceEnded) {
			return;
		}
		if ((this.shot + 1) < this.model.getSequences().size()) {
			this.shot++;
			this.instance.setSequence(this.shot);
		}
		else {
			this.finished = true;
		}
	}

	/** Draws the scene over the whole view; the game's own scenes are not drawn meanwhile. */
	public void render() {
		advance();
		final Camera camera = this.scene.camera;
		camera.viewport(this.viewer.worldScene.camera.rect);
		placeCamera(camera);
		camera.update();
		this.scene.startFrame();
		this.scene.renderOpaque();
		this.scene.renderTranslucent();
	}

	/** The in-game variant: the game's camera shows what the model's camera shows, in the place the model is put. */
	public void placeWorldCamera() {
		advance();
		final Camera camera = this.viewer.worldScene.camera;
		placeCamera(camera);
		camera.update();
	}

	private void placeCamera(final Camera camera) {
		if (this.shotCameras[this.shot] == null) {
			if (this.inGame) {
				return;
			}
			final float distance = Math.max(500f, this.model.bounds.r * 2f);
			camera.perspective(DEFAULT_FOV, camera.getAspect(), DEFAULT_NEAR, DEFAULT_FAR);
			this.eye.set(0, -distance, distance * 0.5f);
			this.target.set(0, 0, 0);
			camera.moveToAndFace(this.eye, this.target, this.worldUp);
			return;
		}
		final com.etheller.warsmash.viewer5.handlers.mdx.Camera modelCamera = this.shotCameras[this.shot];
		final int sequence = this.instance.sequence;
		final int frame = this.instance.frame;
		final int counter = this.instance.counter;
		modelCamera.getPositionTranslation(this.positionOffset, sequence, frame, counter);
		modelCamera.getTargetTranslation(this.targetOffset, sequence, frame, counter);
		modelCamera.getRotation(this.roll, sequence, frame, counter);
		this.eye.set(modelCamera.position[0] + this.positionOffset[0],
				modelCamera.position[1] + this.positionOffset[1],
				modelCamera.position[2] + this.positionOffset[2]);
		this.target.set(modelCamera.targetPosition[0] + this.targetOffset[0],
				modelCamera.targetPosition[1] + this.targetOffset[1],
				modelCamera.targetPosition[2] + this.targetOffset[2]);
		if (this.inGame) {
			this.eye.mul(this.facing).add(this.origin);
			this.target.mul(this.facing).add(this.origin);
		}
		camera.perspective(modelCamera.fieldOfView * MODEL_FOV_SCALE, camera.getAspect(),
				Math.max(1f, modelCamera.nearClippingPlane),
				Math.max(modelCamera.nearClippingPlane + 1f, modelCamera.farClippingPlane));
		if (this.roll[0] == 0) {
			camera.moveToAndFace(this.eye, this.target, this.worldUp);
			return;
		}
		this.forward.set(this.target).sub(this.eye).nor();
		this.right.set(this.forward).crs(this.worldUp);
		if (this.right.len2() < 1e-8f) {
			this.right.set(1, 0, 0);
		}
		this.right.nor();
		this.up.set(this.right).crs(this.forward).nor();
		final float cos = (float) Math.cos(this.roll[0]);
		final float sin = (float) Math.sin(this.roll[0]);
		// The view turns about its own axis: the up direction swings towards the right.
		final float upX = (this.up.x * cos) + (this.right.x * sin);
		final float upY = (this.up.y * cos) + (this.right.y * sin);
		final float upZ = (this.up.z * cos) + (this.right.z * sin);
		this.up.set(upX, upY, upZ);
		camera.moveToAndFace(this.eye, this.target, this.up);
	}

	public void dispose() {
		this.instance.detach();
		if (!this.inGame) {
			if (this.scene.audioContext != null) {
				this.scene.disableAudio();
			}
			this.viewer.removeScene(this.scene);
			this.scene.dispose();
		}
	}
}
