package com.etheller.warsmash.viewer5.handlers.w3x;

import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;

/**
 * PlayModelCinematic: a model made as a whole scene (its own terrain, actors, camera and sounds) fills the screen and
 * plays its first sequence once, then the game is shown again. The map goes on meanwhile, so the trigger that asked for
 * it can wait for it; the interface stays hidden.
 */
public final class ModelCinematic {
	private static final float MODEL_FOV_SCALE = 0.75f;
	private static final float DEFAULT_FOV = 70f;
	private static final float DEFAULT_NEAR = 16f;
	private static final float DEFAULT_FAR = 20000f;

	private final War3MapViewer viewer;
	private final MdxModel model;
	private final Scene scene;
	private final MdxComplexInstance instance;
	private final com.etheller.warsmash.viewer5.handlers.mdx.Camera modelCamera;
	private final float[] positionOffset = new float[3];
	private final float[] targetOffset = new float[3];
	private final float[] roll = new float[3];
	private final Vector3 eye = new Vector3();
	private final Vector3 target = new Vector3();
	private final Vector3 up = new Vector3();
	private final Vector3 right = new Vector3();
	private final Vector3 forward = new Vector3();
	private final Vector3 worldUp = new Vector3(0, 0, 1);
	private boolean skipped;

	private ModelCinematic(final War3MapViewer viewer, final MdxModel model) {
		this.viewer = viewer;
		this.model = model;
		this.scene = viewer.addSimpleScene();
		this.scene.enableAudio();
		this.instance = (MdxComplexInstance) model.addInstance();
		this.instance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
		this.instance.setSequence(0);
		this.instance.setScene(this.scene);
		this.modelCamera = model.getCameras().isEmpty() ? null : model.getCameras().get(0);
	}

	/** @return the cinematic now playing, or null when the model is not there */
	public static ModelCinematic play(final War3MapViewer viewer, final String path) {
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
		return new ModelCinematic(viewer, model);
	}

	public MdxModel getModel() {
		return this.model;
	}

	public void skip() {
		this.skipped = true;
	}

	/** @return whether the sequence has played to its end or the player skipped it */
	public boolean isDone() {
		return this.skipped || this.instance.sequenceEnded;
	}

	/** Draws the scene over the whole view; the game's own scenes are not drawn meanwhile. */
	public void render() {
		final Camera camera = this.scene.camera;
		camera.viewport(this.viewer.worldScene.camera.rect);
		placeCamera(camera);
		camera.update();
		this.scene.startFrame();
		this.scene.renderOpaque();
		this.scene.renderTranslucent();
	}

	private void placeCamera(final Camera camera) {
		if (this.modelCamera == null) {
			final float distance = Math.max(500f, this.model.bounds.r * 2f);
			camera.perspective(DEFAULT_FOV, camera.getAspect(), DEFAULT_NEAR, DEFAULT_FAR);
			this.eye.set(0, -distance, distance * 0.5f);
			this.target.set(0, 0, 0);
			camera.moveToAndFace(this.eye, this.target, this.worldUp);
			return;
		}
		final int sequence = this.instance.sequence;
		final int frame = this.instance.frame;
		final int counter = this.instance.counter;
		this.modelCamera.getPositionTranslation(this.positionOffset, sequence, frame, counter);
		this.modelCamera.getTargetTranslation(this.targetOffset, sequence, frame, counter);
		this.modelCamera.getRotation(this.roll, sequence, frame, counter);
		this.eye.set(this.modelCamera.position[0] + this.positionOffset[0],
				this.modelCamera.position[1] + this.positionOffset[1],
				this.modelCamera.position[2] + this.positionOffset[2]);
		this.target.set(this.modelCamera.targetPosition[0] + this.targetOffset[0],
				this.modelCamera.targetPosition[1] + this.targetOffset[1],
				this.modelCamera.targetPosition[2] + this.targetOffset[2]);
		camera.perspective(this.modelCamera.fieldOfView * MODEL_FOV_SCALE, camera.getAspect(),
				Math.max(1f, this.modelCamera.nearClippingPlane),
				Math.max(this.modelCamera.nearClippingPlane + 1f, this.modelCamera.farClippingPlane));
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
		if (this.scene.audioContext != null) {
			this.scene.disableAudio();
		}
		this.viewer.removeScene(this.scene);
	}
}
