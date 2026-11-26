package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.EnumSet;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.BuildingShadow;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.Extrapolator;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PresentationClock;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldObjectFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;

public class RenderDestructable extends RenderDoodad implements RenderWidget {
	private static final String TEX_FILE = "texFile"; // replaced from 'btxf'
	private static final String TEX_ID = "texID"; // replaced from 'btxi'
	private static final String SEL_CIRCLE_SIZE = "selcircsize"; // replaced from 'bgsc'

	private final MdxModel portraitModel;
	private float life;
	public Rectangle walkableBounds;
	private final CDestructable simulationDestructable;
	private SplatMover selectionCircle;
	private SplatMover selectionPreviewHighlight;
	/** Made when the instance loads, or when the game first asks for it, so what stays out of sight has none. */
	private UnitAnimationListenerImpl unitAnimationListenerImpl;
	private boolean dead;
	private BuildingShadow destructableShadow;
	private final boolean selectable;
	private boolean blighted = false;
	private boolean changedOutOfSight;
	private final int replaceableTextureId;
	private String replaceableTextureFile;
	private final War3MapViewer map;
	private boolean active;
	// With a presentation clock the state is read from the frames of the game, by the handle.
	private final int handleId;
	private final int serial;
	private final float maxLife;
	private final Extrapolator.ObjectPose pose = new Extrapolator.ObjectPose();
	private boolean frameDead;
	private boolean frameBlighted;
	private float frameLife;
	private boolean released;
	/** The shadow image to lay once the model says whether it casts a shadow of its own, see {@link #modelBuilt}. */
	private final String shadowFile;
	/** What a script asked of the animation while the model was not built, replayed when it is. */
	private String waitingAnimation;
	private float waitingSpeed = 1f;

	public RenderDestructable(final War3MapViewer map, final MdxModel model, MdxModel portraitModel,
			final GameObject row, final float[] location3D, final float[] scale3D, final float facingRadians,
			final float selectionScale, final float maxPitch, final float maxRoll, final float life,
			final BuildingShadow destructableShadow, final CDestructable simulationDestructable,
			final int doodadVariation) {
		this(map, model, portraitModel, row, location3D, scale3D, facingRadians, selectionScale, maxPitch, maxRoll,
				life, destructableShadow, null, false, simulationDestructable, doodadVariation);
	}

	/**
	 * @param waitForModel whether the model may still be being read: the destructable then shows when it is built, and
	 *                     the shadow image of {@code shadowFile} is decided then too
	 */
	public RenderDestructable(final War3MapViewer map, final MdxModel model, MdxModel portraitModel,
			final GameObject row, final float[] location3D, final float[] scale3D, final float facingRadians,
			final float selectionScale, final float maxPitch, final float maxRoll, final float life,
			final BuildingShadow destructableShadow, final String shadowFile, final boolean waitForModel,
			final CDestructable simulationDestructable, final int doodadVariation) {
		this(map, model, portraitModel, row, location3D, scale3D, facingRadians, selectionScale, maxPitch, maxRoll,
				life, destructableShadow, shadowFile, waitForModel, false, simulationDestructable, doodadVariation);
	}

	/**
	 * @param lazy whether the model instance is only loaded once it is first in view or asked for, so that thousands
	 *             of destructables out of sight (the platforms of a map's scripted terrain) cost little more than their
	 *             place
	 */
	public RenderDestructable(final War3MapViewer map, final MdxModel model, MdxModel portraitModel,
			final GameObject row, final float[] location3D, final float[] scale3D, final float facingRadians,
			final float selectionScale, final float maxPitch, final float maxRoll, final float life,
			final BuildingShadow destructableShadow, final String shadowFile, final boolean waitForModel,
			final boolean lazy, final CDestructable simulationDestructable, final int doodadVariation) {
		super(map, model, row, location3D, scale3D, facingRadians, maxPitch, maxRoll, selectionScale, doodadVariation,
				waitForModel, lazy);
		this.map = map;
		this.shadowFile = shadowFile;
		this.portraitModel = portraitModel;
		this.life = simulationDestructable.getLife();
		this.handleId = simulationDestructable.getHandleId();
		this.serial = simulationDestructable.getCreationSerial();
		this.maxLife = simulationDestructable.getMaxLife();
		this.destructableShadow = destructableShadow;
		this.simulationDestructable = simulationDestructable;
		this.replaceableTextureFile = row.getFieldAsString(TEX_FILE, 0);
		this.replaceableTextureId = row.getFieldAsInteger(TEX_ID, 0);
		if ((this.replaceableTextureFile != null) && (this.replaceableTextureFile.length() > 1)) {
			final int dotIndex = this.replaceableTextureFile.lastIndexOf('.');
			if (dotIndex != -1) {
				this.replaceableTextureFile = this.replaceableTextureFile.substring(0, dotIndex);
			}
			if (simulationDestructable.isBlighted()) {
				this.blighted = true;
				this.replaceableTextureFile = blightTextureFile(map, this.replaceableTextureFile);
			}
		}
		final boolean waiting = this.instance.isWaiting();
		if (!waiting) {
			modelBuilt(false);
		}
		this.selectionScale *= row.getFieldAsFloat(SEL_CIRCLE_SIZE, 0);
		final boolean deferred = getInstance().isLazyLoad();
		this.selectable = row.readSLKTagBoolean("selectable");
		((MdxComplexInstance) this.instance).sequenceEndObserver = this::wake;
		if (deferred && !map.hasSimulationThread()) {
			// with a simulation thread its events find the listener by the handle, so it is made at once
			simulationDestructable.setUnitAnimationListenerSource(this::animationListener);
		}
		else {
			animationListener();
		}
		if (deferred) {
			getInstance().modelLoadedObserver = this::lazyLoaded;
		}
		else if (waiting) {
			// the animation the listener keeps is started by its own observer, after the model's part is done here
			final Runnable animationStart = getInstance().modelLoadedObserver;
			getInstance().modelLoadedObserver = () -> {
				modelBuilt(true);
				if (animationStart != null) {
					animationStart.run();
				}
				replayWaitingScript();
				if (hasPendingChange()) {
					// what happened while it was not loaded was not seen: it shows how it ended
					this.changedOutOfSight = true;
				}
				wake();
			};
		}
	}

	private void lazyLoaded() {
		final boolean made = this.unitAnimationListenerImpl != null;
		modelBuilt(true);
		if (made) {
			// it kept what the game asked for while the model was not loaded
			this.unitAnimationListenerImpl.modelLoaded();
		}
		else {
			animationListener();
		}
		replayWaitingScript();
		if (hasPendingChange()) {
			// what happened while it was not loaded was not seen: it shows how it ended
			this.changedOutOfSight = true;
		}
		wake();
	}

	/** The animation listener of the game's destructable, made now if the destructable has none yet. */
	private UnitAnimationListenerImpl animationListener() {
		if (this.unitAnimationListenerImpl == null) {
			final Runnable loadObserver = getInstance().modelLoadedObserver;
			final UnitAnimationListenerImpl listener = new UnitAnimationListenerImpl(getInstance(), 0, 0);
			if (this.instance.isWaiting() && (loadObserver != null)) {
				// the listener took the observer of a waiting instance; the destructable's own is the one that runs
				getInstance().modelLoadedObserver = loadObserver;
			}
			this.unitAnimationListenerImpl = listener;
			this.simulationDestructable.setUnitAnimationListener(listener);
			if (!this.released) {
				// a removal already played has dropped the handle, which a late entry would keep for a recycled one
				this.map.destructablePeerCreated(this.simulationDestructable);
			}
			listener.playAnimation(true, getAnimation(), SequenceUtils.EMPTY, 1.0f, true);
			listener.setActivityObserver(this::wake);
		}
		return this.unitAnimationListenerImpl;
	}

	/**
	 * What the destructable takes from its model's contents: its scene shadow, replaceable texture and shadow image.
	 *
	 * @param late whether the destructable has been made for a while, so that it may be gone again
	 */
	private void modelBuilt(final boolean late) {
		final MdxModel model = (MdxModel) this.instance.model;
		final MdxComplexInstance mdxInstance = getInstance();
		mdxInstance.sceneryShadow = this.map.getDynamicShadowManager().castsSceneShadow(model, getScaleZ());
		if ((this.replaceableTextureFile != null) && (this.replaceableTextureFile.length() > 1)) {
			this.instance.setReplaceableTexture(this.replaceableTextureId, this.replaceableTextureFile + ".blp");
			if (model.hd) {
				this.instance.setReplaceableTextureHD(this.replaceableTextureId, this.replaceableTextureFile);
			}
		}
		final PresentationClock clock = this.map.getPresentation();
		// with frames, a destructable no frame lists yet is as the game made it: dead when it had no life then
		final boolean gameDead = clock == null ? this.simulationDestructable.isDead()
				: (readFrame(clock) ? this.frameDead : this.life <= 0);
		if ((this.shadowFile != null) && !mdxInstance.sceneryShadow && !this.dead && !gameDead
				&& (!late || (this.map.getRenderPeer(this.simulationDestructable) == this))) {
			this.destructableShadow = this.map.terrain.addShadow(this.shadowFile, this.x, this.y, true);
		}
	}

	private void replayWaitingScript() {
		final String animation = this.waitingAnimation;
		this.waitingAnimation = null;
		if (animation != null) {
			setAnimation(animation);
		}
		if (this.waitingSpeed != 1f) {
			setAnimationSpeed(this.waitingSpeed);
		}
	}

	@Override
	protected boolean usesAnimInFog() {
		return false;
	}

	/**
	 * A destructable is only updated while something can change: the game changed it, a script gave it an animation,
	 * or the animation ended. Everything else is a tree standing in its loop.
	 */
	public void wake() {
		if (!this.active && !this.released) {
			this.active = true;
			this.map.widgets.active.add(this);
		}
	}

	public void sleep() {
		this.active = false;
	}

	/** Whether the game removed the destructable and the world let go of it (see {@link #release}). */
	public boolean isReleased() {
		return this.released;
	}

	/**
	 * Lets go of what a removed destructable holds in the world: its shadow, selection marks and the game's link to it,
	 * so that nothing keeps its model instance alive once the lists of the viewer have dropped it.
	 */
	public void release(final War3MapViewer viewer) {
		this.released = true;
		this.active = false;
		if (this.destructableShadow != null) {
			this.destructableShadow.remove();
			this.destructableShadow = null;
		}
		if (this.selectionCircle != null) {
			this.selectionCircle.destroy(Gdx.gl30, viewer.terrain.centerOffset);
			this.selectionCircle = null;
		}
		if (this.selectionPreviewHighlight != null) {
			this.selectionPreviewHighlight.destroy(Gdx.gl30, viewer.terrain.centerOffset);
			this.selectionPreviewHighlight = null;
		}
		this.simulationDestructable.setUnitAnimationListener(null);
		this.simulationDestructable.setUnitAnimationListenerSource(null);
		getInstance().sequenceEndObserver = null;
		if (this.unitAnimationListenerImpl != null) {
			this.unitAnimationListenerImpl.setActivityObserver(null);
		}
	}

	public boolean canSleep() {
		return !this.changedOutOfSight;
	}

	/** Whether the game changed this destructable in a way {@link #updateAnimations} reacts to. */
	public boolean hasPendingChange() {
		final PresentationClock clock = this.map.getPresentation();
		if (clock != null) {
			if (!readFrame(clock)) {
				return false;
			}
			return (this.frameDead != this.dead) || (this.frameBlighted && !this.blighted)
					|| (!this.frameDead && (Math.abs(this.life - this.frameLife) > 0.003f));
		}
		final boolean dead = this.simulationDestructable.isDead();
		return (dead != this.dead) || (this.simulationDestructable.isBlighted() && !this.blighted)
				|| (!dead && (Math.abs(this.life - this.simulationDestructable.getLife()) > 0.003f));
	}

	/** Takes the state the frames give at the presentation time; false when no frame has this destructable. */
	private boolean readFrame(final PresentationClock clock) {
		if (!Extrapolator.sampleObject(clock, Extrapolator.TREES, this.handleId, this.serial, this.pose)) {
			return false;
		}
		final WorldObjectFrameState state = this.pose.state;
		final int flags = state.flags[this.pose.row];
		this.frameDead = (flags & WorldObjectFrameState.DEAD) != 0;
		this.frameBlighted = (flags & WorldObjectFrameState.BLIGHTED) != 0;
		this.frameLife = state.life[this.pose.row] * this.maxLife;
		return true;
	}

	// Only trees have blight art (AshenTreeBlight); other replaceable textures such as Cliff\Cliff0 keep their own.
	private static String blightTextureFile(final War3MapViewer map, final String textureFile) {
		final String blightFile = textureFile + "Blight";
		if (map.dataSource.has(blightFile + ".blp") || map.dataSource.has(blightFile + ".dds")) {
			return blightFile;
		}
		return textureFile;
	}

	public MdxModel getPortraitModel() {
		return this.portraitModel;
	}

	@Override
	public PrimaryTag getAnimation() {
		if (this.life <= 0) {
			return PrimaryTag.DEATH;
		}
		return super.getAnimation();
	}

	@Override
	public MdxComplexInstance getInstance() {
		return (MdxComplexInstance) this.instance;
	}

	@Override
	public CWidget getSimulationWidget() {
		return this.simulationDestructable;
	}

	@Override
	public void updateAnimations(final War3MapViewer war3MapViewer) {
		// TODO maybe move getAnimation behaviors to here and make this thing not a
		// doodad

		if (this.instance.isWaiting()) {
			// what changed is dealt with once the model is built
			return;
		}
		final PresentationClock clock = war3MapViewer.getPresentation();
		final boolean dead;
		final boolean blighted;
		final float simulationLife;
		if (clock != null) {
			if (!readFrame(clock)) {
				animationListener().update();
				return;
			}
			dead = this.frameDead;
			blighted = this.frameBlighted;
			simulationLife = this.frameLife;
		}
		else {
			dead = this.simulationDestructable.isDead();
			blighted = this.simulationDestructable.isBlighted();
			simulationLife = this.simulationDestructable.getLife();
		}
		final boolean changed = (dead != this.dead) || (blighted && !this.blighted)
				|| (!dead && (Math.abs(this.life - simulationLife) > 0.003f));
		if (changed && !war3MapViewer.isPointShownToViewer(this.x, this.y)) {
			// Measured on 1.29: the fog keeps a tree as it was last seen; one cut or grown back out of sight
			// shows its new state, without the fall or the growth, once the place is seen again.
			this.changedOutOfSight = true;
			animationListener().update();
			return;
		}
		final boolean changedOutOfSight = this.changedOutOfSight;
		this.changedOutOfSight = false;
		if (dead && !this.dead) {
			animationListener().playAnimation(true, PrimaryTag.DEATH, SequenceUtils.EMPTY, 1.0f, true);
			if (changedOutOfSight) {
				getInstance().setFrame(getInstance().clampFrame(Integer.MAX_VALUE));
			}
			if (this.destructableShadow != null) {
				this.destructableShadow.remove();
				this.destructableShadow = null;
			}
			if (this.selectionCircle != null) {
				this.selectionCircle.destroy(Gdx.gl30, war3MapViewer.terrain.centerOffset);
				this.selectionCircle = null;
			}
			if (this.selectionPreviewHighlight != null) {
				this.selectionPreviewHighlight.destroy(Gdx.gl30, war3MapViewer.terrain.centerOffset);
				this.selectionPreviewHighlight = null;
			}
		}
		else if (!dead) {
			if (this.dead) {
				if (changedOutOfSight) {
					animationListener().playAnimation(true, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f,
							true);
				}
				else {
					animationListener().playAnimation(true, PrimaryTag.BIRTH, SequenceUtils.EMPTY, 1.0f,
							true);
					animationListener().queueAnimation(PrimaryTag.STAND, SequenceUtils.EMPTY, true);
				}
				this.life = simulationLife;
				// TODO add back shadow here

			}
			else {
				if (Math.abs(this.life - simulationLife) > 0.003f) {
					if (!changedOutOfSight && (this.life > simulationLife)) {
						animationListener().playAnimation(true, PrimaryTag.STAND, SequenceUtils.HIT, 1.0f,
								true);
					}
					this.life = simulationLife;
				}
			}
		}
		this.dead = dead;
		if (blighted && !this.blighted) {
			this.blighted = blighted;
			if ((this.replaceableTextureFile != null) && (this.replaceableTextureFile.length() > 1)) {
				final String blightFile = blightTextureFile(war3MapViewer, this.replaceableTextureFile);
				if (!blightFile.equals(this.replaceableTextureFile)) {
					this.replaceableTextureFile = blightFile;
					this.instance.setReplaceableTexture(this.replaceableTextureId, blightFile + ".blp");
				}
			}
		}
		animationListener().update();
	}

	@Override
	public boolean isIntersectedOnMeshAlways() {
		return true;
	}

	@Override
	public float getSelectionScale() {
		return this.selectionScale;
	}

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
		return this.instance.localLocation.z;
	}

	@Override
	public void unassignSelectionCircle() {
		this.selectionCircle = null;
	}

	@Override
	public void assignSelectionCircle(final SplatMover selectionCircle) {
		this.selectionCircle = selectionCircle;
	}

	@Override
	public void unassignSelectionPreviewHighlight() {
		this.selectionPreviewHighlight = null;
	}

	@Override
	public void assignSelectionPreviewHighlight(final SplatMover t) {
		this.selectionPreviewHighlight = t;
	}

	@Override
	public boolean isSelectable(final CSimulation simulation, final int byPlayer) {
		return this.selectable;
	}

	@Override
	public SplatMover getSelectionPreviewHighlight() {
		return this.selectionPreviewHighlight;
	}

	@Override
	public SplatMover getSelectionCircle() {
		return this.selectionCircle;
	}

	public CDestructable getSimulationDestructable() {
		return this.simulationDestructable;
	}

	public UnitAnimationListenerImpl getUnitAnimationListenerImpl() {
		return animationListener();
	}

	public void setAnimation(final String sequence) {
		if (this.instance.isWaiting()) {
			this.waitingAnimation = sequence;
			return;
		}
		final EnumSet<PrimaryTag> primaryTags = EnumSet.noneOf(PrimaryTag.class);
		PrimaryTag bestPrimaryTag = null;
		final EnumSet<SecondaryTag> secondaryTags = EnumSet.noneOf(SecondaryTag.class);
		TokenLoop: for (final String token : sequence.split("\\s+")) {
			final String upperCaseToken = token.toUpperCase(Locale.ROOT);
			for (final PrimaryTag primaryTag : PrimaryTag.values()) {
				if (upperCaseToken.equals(primaryTag.name())) {
					primaryTags.add(primaryTag);
					bestPrimaryTag = primaryTag;
					continue TokenLoop;
				}
			}
			for (final SecondaryTag secondaryTag : SecondaryTag.VALUES) {
				if (upperCaseToken.equals(secondaryTag.name())) {
					secondaryTags.add(secondaryTag);
					continue TokenLoop;
				}
			}
			break;
		}
		this.dead = this.simulationDestructable.isDead();
		this.life = this.simulationDestructable.getLife();
		animationListener().playAnimation(true, bestPrimaryTag, secondaryTags, 1.0f, true);
	}

	public void setHidden(final boolean hidden) {
		if (this.instance != null) {
			if (hidden) {
				this.instance.hide();
			}
			else {
				this.instance.show();
			}
		}
	}

	/**
	 * Destructable animations are single sequences here, so a queued one plays right away.
	 */
	public void queueAnimation(final String sequence) {
		setAnimation(sequence);
	}

	public void setAnimationSpeed(final float speed) {
		if (this.instance.isWaiting()) {
			this.waitingSpeed = speed;
			return;
		}
		if (this.instance instanceof MdxComplexInstance) {
			((MdxComplexInstance) this.instance).setAnimationSpeed(speed);
		}
	}

	public void notifyLifeRestored() {
		this.dead = this.simulationDestructable.isDead();
		this.life = this.simulationDestructable.getLife();
		if ((this.unitAnimationListenerImpl == null) && this.instance.isLazyLoad()) {
			// the listener plays what it shows now when it is made
			return;
		}
		animationListener().playAnimation(true, getAnimation(), SequenceUtils.EMPTY, 1.0f, true);
	}

	@Override
	public boolean isShowSelectionCircleAboveWater() {
		return false;
	}

	private static final String DOODAD_COLOR_RED = "colorR"; // replaced from 'bvcr'
	private static final String DOODAD_COLOR_GREEN = "colorG"; // replaced from 'bvcg'
	private static final String DOODAD_COLOR_BLUE = "colorB"; // replaced from 'bvcb'

	@Override
	public void applyColor(final GameObject row, final int doodadVariation, final ModelInstance instance) {
		final int vertR = row.getFieldValue(DOODAD_COLOR_RED);
		final int vertG = row.getFieldValue(DOODAD_COLOR_GREEN);
		final int vertB = row.getFieldValue(DOODAD_COLOR_BLUE);
		((MdxComplexInstance) instance).setVertexColor(new float[] { vertR / 255f, vertG / 255f, vertB / 255f });
	}
}
