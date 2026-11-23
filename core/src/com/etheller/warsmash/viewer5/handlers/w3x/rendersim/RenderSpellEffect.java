package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import com.badlogic.gdx.math.Quaternion;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;

import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.IndexedSequence;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;

public class RenderSpellEffect extends AllocatedHandle implements RenderEffect, ScriptEffect {
	public static final PrimaryTag[] DEFAULT_ANIMATION_QUEUE = { PrimaryTag.BIRTH, PrimaryTag.STAND, PrimaryTag.DEATH };
	public static final PrimaryTag[] STAND_ONLY = { PrimaryTag.STAND };
	public static final PrimaryTag[] DEATH_ONLY = { PrimaryTag.DEATH };
	public static final PrimaryTag[] STAND_THEN_DEATH = { PrimaryTag.STAND, PrimaryTag.DEATH };
	private SequenceLoopMode sequenceLoopMode;
	private MdxComplexInstance modelInstance;
	private PrimaryTag[] animationQueue;
	private final EnumSet<SecondaryTag> requiredAnimationNames;
	private int animationQueueIndex;
	private List<Sequence> sequences;
	private boolean killWhenDone = true;
	private String modelName;
	private CWidget widget;
	private List<String> attachPoints;
	private float pointX;
	private float pointY;
	private float yaw;
	private Object savedForm;
	private final War3MapViewer viewer;
	private boolean scriptPositionKnown;
	private float baseZ;
	private float baseRenderZ;
	private float scriptX;
	private float scriptY;
	private float scriptZ;
	private float scriptPitch;
	private float scriptRoll;
	private final float[] scriptScale = { 1, 1, 1 };
	/** The height it was last drawn at, for what asks after the model is gone. */
	private float lastLocalZ;
	private final EnumSet<SecondaryTag> scriptSubAnimations = EnumSet.noneOf(SecondaryTag.class);
	private final List<Integer> scriptQueuedSequences = new java.util.ArrayList<>();
	/** Whether the model is still being read: the effect starts its animations when it is built. */
	private boolean waiting;

	public RenderSpellEffect(final MdxComplexInstance modelInstance, final War3MapViewer war3MapViewer, final float yaw,
			final PrimaryTag[] animationQueue, final EnumSet<SecondaryTag> requiredAnimationNames) {
		this.viewer = war3MapViewer;
		this.modelInstance = modelInstance;
		modelInstance.hiddenInFog = true;
		this.animationQueue = animationQueue;
		this.requiredAnimationNames = requiredAnimationNames;
		final MdxModel model = (MdxModel) this.modelInstance.model;
		this.sequences = model.getSequences();
		this.sequenceLoopMode = SequenceLoopMode.MODEL_LOOP;
		this.modelInstance.setSequenceLoopMode(sequenceLoopMode);
		this.modelInstance.localRotation.setFromAxisRad(0, 0, 1, yaw);
		this.modelInstance.sequenceEnded = true;
		if (modelInstance.isWaiting()) {
			this.waiting = true;
			modelInstance.modelLoadedObserver = this::modelBuilt;
		}
		else {
			start();
		}
	}

	private void start() {
		final MdxModel model = (MdxModel) this.modelInstance.model;
		playNextAnimation();
		if ((this.modelInstance.sequence == -1) && (model.getSequences().size() > 0)) {
			this.modelInstance.setSequence(0);
			this.animationQueueIndex = 0;
		}
	}

	private void modelBuilt() {
		this.waiting = false;
		// an effect taken away while it waited has nothing to play
		if ((this.modelInstance != null) && (this.animationQueue.length > 0)) {
			start();
		}
	}

	/** Builds the model now when it is still being read, for what needs its sequences. */
	private void ensureBuilt() {
		if (this.waiting) {
			((MdxModel) this.modelInstance.model).finishPending();
		}
	}

	@Override
	public boolean updateAnimations(final War3MapViewer war3MapViewer, final float deltaTime) {
		if (this.modelInstance == null) {
			return true;
		}
		if (this.waiting) {
			return false;
		}
		if (this.modelInstance.sequenceEnded && !this.scriptQueuedSequences.isEmpty()
				&& (this.animationQueueIndex >= this.animationQueue.length)) {
			playScriptSequence(this.scriptQueuedSequences.remove(0));
		}
		final boolean everythingDone = this.modelInstance.sequenceEnded && this.animationQueueIndex >= this.animationQueue.length
				&& this.scriptQueuedSequences.isEmpty();
		if (everythingDone) {
			if (this.killWhenDone) {
				if (!war3MapViewer.worldScene.leaveParticles(this.modelInstance)) {
					if (this.modelInstance.parent != null) {
						this.modelInstance.setParent(null);
					}
					war3MapViewer.worldScene.removeInstance(this.modelInstance);
				}
				this.lastLocalZ = this.modelInstance.localLocation.z;
				// A script may keep the handle for good; it must not keep the model with it.
				this.modelInstance = null;
				this.sequences = Collections.emptyList();
				return true;
			} else {
				this.animationQueueIndex = 0;
				return false;
			}
		}
		playNextAnimation();
		return everythingDone;
	}

	private void playNextAnimation() {
		while (this.modelInstance.sequenceEnded && (this.animationQueueIndex < this.animationQueue.length)) {
			applySequence();
			this.animationQueueIndex++;
		}
	}

	public void applySequence() {
		if ((this.modelInstance == null) || this.waiting) {
			return;
		}
		final PrimaryTag tag = this.animationQueue[this.animationQueueIndex];
		final IndexedSequence sequence = SequenceUtils.selectSequence(tag, requiredAnimationNames, this.sequences,
				true);
		if ((sequence != null) && (sequence.index != -1)) {
			if ((tag == PrimaryTag.STAND) && (sequenceLoopMode != SequenceLoopMode.NEVER_LOOP)) {
				this.modelInstance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
			}
			else {
				this.modelInstance.setSequenceLoopMode(sequenceLoopMode);
			}
			this.modelInstance.setSequence(sequence.index);
		}
	}

	public void setAnimations(final PrimaryTag[] animations, final boolean killWhenDone) {
		this.animationQueue = animations;
		this.animationQueueIndex = 0;
		setKillWhenDone(killWhenDone);
		if (this.waiting) {
			// the first of them is played when the model is built
			return;
		}
		applySequence();
		this.animationQueueIndex++;
	}

	@Override
	public void setKillWhenDone(final boolean killWhenDone) {
		this.killWhenDone = killWhenDone;
		if (this.modelInstance == null) {
			return;
		}
		if (killWhenDone) {
			sequenceLoopMode = SequenceLoopMode.NEVER_LOOP;
			this.modelInstance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
		}
		else {
			this.modelInstance.setSequenceLoopMode(sequenceLoopMode);
		}
	}

	/** What a saved game needs to show this effect again. */
	public RenderSpellEffect setOrigin(final String modelName, final CWidget widget, final List<String> attachPoints,
			final float pointX, final float pointY, final float yaw) {
		this.modelName = modelName;
		this.widget = widget;
		this.attachPoints = attachPoints;
		this.pointX = pointX;
		this.pointY = pointY;
		this.yaw = yaw;
		return this;
	}

	public String getModelName() {
		return this.modelName;
	}

	public CWidget getWidget() {
		return this.widget;
	}

	public List<String> getAttachPoints() {
		return this.attachPoints;
	}

	public float getPointX() {
		return this.pointX;
	}

	public float getPointY() {
		return this.pointY;
	}

	public float getYaw() {
		return this.yaw;
	}

	public float getLocalZ() {
		if (this.modelInstance == null) {
			return this.lastLocalZ;
		}
		return this.modelInstance.localLocation.z;
	}

	public Object getSavedForm() {
		return this.savedForm;
	}

	public void setSavedForm(final Object savedForm) {
		this.savedForm = savedForm;
	}

	/** Stays until something ends it, unlike an effect that plays once and is gone. */
	public boolean isLasting() {
		return (this.modelInstance != null) && (this.modelName != null) && (this.sequenceLoopMode != SequenceLoopMode.NEVER_LOOP)
				&& (this.animationQueue.length > 0);
	}

	@Override
	public void removeWithoutDeath() {
		if (this.modelInstance == null) {
			return;
		}
		this.animationQueue = new PrimaryTag[0];
		this.animationQueueIndex = 0;
		this.killWhenDone = true;
		this.modelInstance.sequenceEnded = true;
		this.modelInstance.hide();
	}

	public void setHeight(final float height) {
		if (this.modelInstance == null) {
			return;
		}
		this.modelInstance.setLocation(modelInstance.localLocation.x, modelInstance.localLocation.y, height);
	}

	@Override
	public RenderSpellEffect getLiveEffect() {
		return this.modelInstance == null ? null : this;
	}

	public MdxComplexInstance getModelInstance() {
		return this.modelInstance;
	}

	private boolean isScriptPlaced() {
		return (this.widget == null) && (this.modelName != null);
	}

	private void ensureScriptPosition() {
		if (!this.scriptPositionKnown) {
			this.scriptPositionKnown = true;
			this.baseRenderZ = getLocalZ();
			// The getters report the terrain height under the creation point, not the walkable height drawn at.
			this.baseZ = this.viewer == null ? this.baseRenderZ
					: this.viewer.terrain.getGroundHeight(this.pointX, this.pointY);
			this.scriptX = this.pointX;
			this.scriptY = this.pointY;
			this.scriptZ = this.baseZ;
		}
	}

	/** The position the local getters report; an effect on a widget reports zeros, as measured on 1.29. */
	public float getScriptX() {
		if (!isScriptPlaced()) {
			return 0;
		}
		ensureScriptPosition();
		return this.scriptX;
	}

	public float getScriptY() {
		if (!isScriptPlaced()) {
			return 0;
		}
		ensureScriptPosition();
		return this.scriptY;
	}

	public float getScriptZ() {
		if (!isScriptPlaced()) {
			return 0;
		}
		ensureScriptPosition();
		return this.scriptZ;
	}

	/**
	 * Moves an effect placed on the ground; an effect on a widget stays on it. Measured on 1.29: setting only X or
	 * Y (or the position from a location) takes the other coordinates from where the effect was created, not from
	 * where it was moved to, and drops it back to its creation height.
	 */
	public void setScriptPosition(final Float x, final Float y, final Float z, final boolean overGround) {
		if (!isScriptPlaced()) {
			return;
		}
		ensureScriptPosition();
		final boolean keepCurrent = (z != null) && (x == null) && (y == null);
		this.scriptX = x != null ? x : keepCurrent ? this.scriptX : this.pointX;
		this.scriptY = y != null ? y : keepCurrent ? this.scriptY : this.pointY;
		this.scriptZ = z != null ? z : this.baseZ;
		float renderZ = z != null ? this.scriptZ : this.baseRenderZ;
		if (overGround && (this.viewer != null)) {
			renderZ += this.viewer.terrain.getGroundHeight(this.scriptX, this.scriptY);
		}
		if (this.modelInstance != null) {
			this.modelInstance.setLocation(this.scriptX, this.scriptY, renderZ);
		}
		this.lastLocalZ = renderZ;
	}

	/** Where the effect stands now, from its own fields so that it is known while the model is still being read. */
	public float getPlacedX() {
		if (this.modelInstance != null) {
			return this.modelInstance.localLocation.x;
		}
		return this.scriptPositionKnown ? this.scriptX : this.pointX;
	}

	public float getPlacedY() {
		if (this.modelInstance != null) {
			return this.modelInstance.localLocation.y;
		}
		return this.scriptPositionKnown ? this.scriptY : this.pointY;
	}

	public float getSavedScaleX() {
		return this.scriptScale[0];
	}

	public float getSavedScaleY() {
		return this.scriptScale[1];
	}

	public float getSavedScaleZ() {
		return this.scriptScale[2];
	}

	public float getScriptPitch() {
		return this.scriptPitch;
	}

	public float getScriptRoll() {
		return this.scriptRoll;
	}

	/** Jumps to the given time, in seconds, into the animation playing now. */
	public void setScriptTime(final float seconds) {
		if (this.modelInstance == null) {
			return;
		}
		ensureBuilt();
		final int sequenceIndex = this.modelInstance.sequence;
		if ((sequenceIndex < 0) || (sequenceIndex >= this.sequences.size())) {
			return;
		}
		final Sequence sequence = this.sequences.get(sequenceIndex);
		final long start = sequence.getInterval()[0];
		final long length = sequence.getInterval()[1] - start;
		long offset = (long) (seconds * 1000);
		if (length > 0) {
			offset = Math.max(0, Math.min(length, offset));
		}
		this.modelInstance.setFrame((int) (start + offset));
	}

	/** The sequence an animation name asks for (the script's name and the sub animations it set), or -1. */
	private int selectScriptSequence(final String animationName) {
		final EnumSet<PrimaryTag> primaryTags = EnumSet.noneOf(PrimaryTag.class);
		final EnumSet<SecondaryTag> secondaryTags = EnumSet.noneOf(SecondaryTag.class);
		Sequence.populateTags(primaryTags, secondaryTags, animationName == null ? "" : animationName);
		secondaryTags.addAll(this.scriptSubAnimations);
		final IndexedSequence sequence = SequenceUtils.selectSequence(Sequence.any(primaryTags), secondaryTags,
				this.sequences, true);
		return (sequence == null) ? -1 : sequence.index;
	}

	private void playScriptSequence(final int sequenceIndex) {
		this.animationQueue = new PrimaryTag[0];
		this.animationQueueIndex = 0;
		final Sequence sequence = this.sequences.get(sequenceIndex);
		// A stand animation keeps running, any other plays once and holds its last frame until the next one.
		this.modelInstance.setSequenceLoopMode(sequence.getPrimaryTags().contains(PrimaryTag.STAND)
				? SequenceLoopMode.ALWAYS_LOOP : SequenceLoopMode.NEVER_LOOP);
		this.modelInstance.setSequence(sequenceIndex);
	}

	/** BlzSetSpecialEffectAnimation and BlzPlaySpecialEffect: the animation plays now, the queued ones are dropped. */
	public void playScriptAnimation(final String animationName) {
		if (this.modelInstance == null) {
			return;
		}
		ensureBuilt();
		final int index = selectScriptSequence(animationName);
		if (index >= 0) {
			this.scriptQueuedSequences.clear();
			playScriptSequence(index);
		}
	}

	/** BlzQueueSpecialEffectAnimation: plays after the animation playing now and the ones queued before. */
	public void queueScriptAnimation(final String animationName) {
		if (this.modelInstance == null) {
			return;
		}
		ensureBuilt();
		final int index = selectScriptSequence(animationName);
		if (index >= 0) {
			if (this.modelInstance.sequenceEnded && this.scriptQueuedSequences.isEmpty()) {
				playScriptSequence(index);
			}
			else {
				this.scriptQueuedSequences.add(index);
			}
		}
	}

	public void addScriptSubAnimation(final SecondaryTag tag) {
		this.scriptSubAnimations.add(tag);
	}

	public void removeScriptSubAnimation(final SecondaryTag tag) {
		this.scriptSubAnimations.remove(tag);
	}

	public void clearScriptSubAnimations() {
		this.scriptSubAnimations.clear();
	}

	/** The scale of the three axes after BlzSetSpecialEffectMatrixScale, or the uniform scale. */
	public void setScriptMatrixScale(final float x, final float y, final float z) {
		this.scriptScale[0] = x;
		this.scriptScale[1] = y;
		this.scriptScale[2] = z;
		if (this.modelInstance != null) {
			this.modelInstance.setScale(new float[] { x, y, z });
		}
	}

	public float getScriptScale() {
		return this.modelInstance == null ? this.scriptScale[0] : this.modelInstance.localScale.x;
	}

	/** BlzResetSpecialEffectMatrix: the scale and the turn the script gave go back to what the effect had. */
	public void resetScriptMatrix() {
		this.scriptScale[0] = 1;
		this.scriptScale[1] = 1;
		this.scriptScale[2] = 1;
		if (this.modelInstance != null) {
			this.modelInstance.setUniformScale(1f);
		}
		if (isScriptPlaced()) {
			setScriptOrientation(this.yaw, 0, 0);
		}
	}

	public void setScriptOrientation(final float yaw, final float pitch, final float roll) {
		if (!isScriptPlaced()) {
			return;
		}
		this.yaw = yaw;
		this.scriptPitch = pitch;
		this.scriptRoll = roll;
		final Quaternion rotation = new Quaternion().setFromAxisRad(0, 0, 1, yaw);
		rotation.mul(new Quaternion().setFromAxisRad(0, -1, 0, pitch));
		rotation.mul(new Quaternion().setFromAxisRad(1, 0, 0, roll));
		if (this.modelInstance != null) {
			this.modelInstance.setRotation(new float[] { rotation.x, rotation.y, rotation.z, rotation.w });
		}
	}
}
