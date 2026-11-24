package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.Locale;
import java.util.SplittableRandom;

import com.badlogic.gdx.math.Quaternion;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;

import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

public class RenderDoodad {
	private static final int SAMPLE_RADIUS = 4;
	private static final float[] VERTEX_COLOR_HEAP = { 0f, 0f, 0f, 1f };
	public final ModelInstance instance;
	private final GameObject row;
	private final float maxPitch;
	private final float maxRoll;
	protected float x;
	protected float y;
	protected float selectionScale;
	// The map's own record of this doodad (war3map.doo), as the Blz doodad natives read it.
	private final float z;
	private final float[] scale;
	private final float yaw;
	private float pitch;
	private float roll;
	private final int variation;

	private static final String DOODAD_COLOR_RED = "vertR"; // replaced from 'dvr1'
	private static final String DOODAD_COLOR_GREEN = "vertG"; // replaced from 'dvg1'
	private static final String DOODAD_COLOR_BLUE = "vertB"; // replaced from 'dvb1'

	private CFogState fogState;
	/** Whether the doodad keeps animating and emitting while its place is not in sight (data field animInFog). */
	private final boolean animatesInFog;
	private boolean fogFrozen;
	private byte lastFogStateColor;
	private final float[] vertexColorBase;

	/**
	 * A non-looping sequence a script chose stays on its last frame (a banner that died stays gone); the stand
	 * animation only takes over again when the script picks one.
	 */
	private boolean holdsScriptedEnd;
	/** Picks among a doodad's stand animations; made from the doodad's own place so every run shows the same ones. */
	private SplittableRandom animationRandom;

	private SplittableRandom animationRandom() {
		if (this.animationRandom == null) {
			this.animationRandom = new SplittableRandom(
					(Float.floatToIntBits(this.x) * 0x9E3779B97F4A7C15L) ^ (Float.floatToIntBits(this.y) * 0xC2B2AE3D27D4EB4FL)
							^ (Float.floatToIntBits(this.z) * 0x165667B19E3779F9L)
							^ (((long) this.row.getId().hashCode() << 32) | this.variation));
		}
		return this.animationRandom;
	}

	/**
	 * Makes the doodad's animation clock run only while it is in view: what ends while it is not is played through when
	 * it is seen again, the stand animation following each end by the doodad's own picks.
	 */
	public void enableLazyClock() {
		final MdxComplexInstance mdxInstance = (MdxComplexInstance) this.instance;
		mdxInstance.lazyClock = true;
		mdxInstance.sequenceEndObserver = this::pickStandSequence;
	}

	/** Starts the first stand animation once the model has loaded; false while it has not. */
	public boolean startStandSequence() {
		final MdxComplexInstance mdxInstance = (MdxComplexInstance) this.instance;
		if (mdxInstance.sequence != -1) {
			return true;
		}
		pickStandSequence();
		if (mdxInstance.sequence == -1) {
			return false;
		}
		// The clock starts now, not at the time the doodad was made.
		mdxInstance.settleClock(false);
		return true;
	}

	private void pickStandSequence() {
		if (!this.holdsScriptedEnd) {
			final SplittableRandom random = animationRandom();
			SequenceUtils.randomSequence((MdxComplexInstance) this.instance, getAnimation(), SequenceUtils.EMPTY, true,
					() -> random.nextDouble());
		}
	}

	public boolean holdsScriptedEnd() {
		return this.holdsScriptedEnd;
	}

	public boolean isOfType(final String doodadId) {
		return doodadId.equals(this.row.getId());
	}

	public float getX() {
		return this.x;
	}

	public float getY() {
		return this.y;
	}

	public float getZ() {
		return this.z;
	}

	public float getScaleX() {
		return this.scale[0];
	}

	public float getScaleY() {
		return this.scale[1];
	}

	public float getScaleZ() {
		return this.scale[2];
	}

	public float getYaw() {
		return this.yaw;
	}

	public float getPitch() {
		return this.pitch;
	}

	public float getRoll() {
		return this.roll;
	}

	public int getVariation() {
		return this.variation;
	}

	public int getTypeId() {
		return War3ID.fromString(this.row.getId()).getValue();
	}

	public void setPlayerColor(final int ordinal) {
		if (this.instance instanceof MdxComplexInstance) {
			((MdxComplexInstance) this.instance).setTeamColor(ordinal);
		}
	}

	/** An animation by name; random picks one of the sequences that share the name (Stand, Stand - 2, ...). */
	public void playAnimation(final String animation, final boolean random) {
		if (!random || !(this.instance instanceof MdxComplexInstance)) {
			playAnimation(animation);
			return;
		}
		final MdxComplexInstance mdxInstance = (MdxComplexInstance) this.instance;
		mdxInstance.settleClock(true);
		final Sequence[] sequences = ((MdxModel) mdxInstance.model).getSequences().toArray(new Sequence[0]);
		final String prefix = animation.toLowerCase(Locale.ROOT);
		int matches = 0;
		for (final Sequence sequence : sequences) {
			if (sequence.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
				matches++;
			}
		}
		if (matches == 0) {
			return;
		}
		int pick = animationRandom().nextInt(matches);
		for (int i = 0; i < sequences.length; i++) {
			if (sequences[i].getName().toLowerCase(Locale.ROOT).startsWith(prefix) && (pick-- == 0)) {
				mdxInstance.setSequence(i);
				this.holdsScriptedEnd = sequences[i].getFlags() != 0;
				return;
			}
		}
	}

	public void playAnimation(final String animation) {
		if (this.instance instanceof MdxComplexInstance) {
			final MdxComplexInstance mdxInstance = (MdxComplexInstance) this.instance;
			mdxInstance.settleClock(true);
			final Sequence[] sequences = ((MdxModel) mdxInstance.model).getSequences().toArray(new Sequence[0]);
			for (int i = 0; i < sequences.length; i++) {
				if (sequences[i].getName().toLowerCase(Locale.ROOT).startsWith(animation.toLowerCase(Locale.ROOT))) {
					mdxInstance.setSequence(i);
					this.holdsScriptedEnd = sequences[i].getFlags() != 0;
					return;
				}
			}
		}
	}

	public RenderDoodad(final War3MapViewer map, final MdxModel model, final GameObject row, final float[] location3D,
			final float[] scale3D, final float facingRadians, final float maxPitch, final float maxRoll,
			final float selectionScale, final int doodadVariation) {
		this(map, model, row, location3D, scale3D, facingRadians, maxPitch, maxRoll, selectionScale, doodadVariation,
				false);
	}

	/**
	 * @param waitForModel whether the instance is made before the model is built (see
	 *                     {@link MdxModel#addWaitingInstance()}); what depends on the model's contents is then left to
	 *                     the owner once it is built
	 */
	protected RenderDoodad(final War3MapViewer map, final MdxModel model, final GameObject row,
			final float[] location3D, final float[] scale3D, final float facingRadians, final float maxPitch,
			final float maxRoll, final float selectionScale, final int doodadVariation, final boolean waitForModel) {
		this(map, model, row, location3D, scale3D, facingRadians, maxPitch, maxRoll, selectionScale, doodadVariation,
				waitForModel, false);
	}

	/**
	 * @param lazy whether the instance is made empty and loads when it is first in view (see
	 *             {@code Model.addLazyInstance()}); its owner then handles it as one made before its model was built
	 */
	protected RenderDoodad(final War3MapViewer map, final MdxModel model, final GameObject row,
			final float[] location3D, final float[] scale3D, final float facingRadians, final float maxPitch,
			final float maxRoll, final float selectionScale, final int doodadVariation, final boolean waitForModel,
			final boolean lazy) {
		this.maxPitch = maxPitch;
		this.maxRoll = maxRoll;
		this.z = location3D[2];
		this.scale = scale3D.clone();
		this.yaw = facingRadians;
		this.variation = doodadVariation;
		this.animatesInFog = !usesAnimInFog() || row.readSLKTagBoolean("animInFog");
		final boolean isSimple = row.readSLKTagBoolean("lightweight");
		ModelInstance instance;

		if (isSimple && false) {
			instance = model.addInstance(1);
		}
		else {
			instance = lazy ? model.addLazyInstance()
					: waitForModel ? model.addWaitingInstance() : model.addInstance();
			((MdxComplexInstance) instance).setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
		}

		if (!waitForModel && !instance.isWaiting()) {
			((MdxComplexInstance) instance).sceneryShadow = map.getDynamicShadowManager().castsSceneShadow(model,
					scale3D[2]);
		}
		instance.move(location3D);
		// TODO: the following pitch/roll system is a heuristic, and we probably want to
		// revisit it later.
		// Specifically, I was pretty convinced that whichever is applied first
		// (pitch/roll) should be used to do a projection onto the already-tilted plane
		// to find the angle used for the other of the two
		// (instead of measuring down from an imaginary flat ground plane, as we do
		// currently).
		float pitch, roll;
		this.x = location3D[0];
		this.y = location3D[1];
		applyColor(row, doodadVariation + 1, instance);
		this.vertexColorBase = new float[] { ((MdxComplexInstance) instance).vertexColor[0],
				((MdxComplexInstance) instance).vertexColor[1], ((MdxComplexInstance) instance).vertexColor[2] };
		final float pitchSampleForwardX = this.x + (SAMPLE_RADIUS * (float) Math.cos(facingRadians));
		final float pitchSampleForwardY = this.y + (SAMPLE_RADIUS * (float) Math.sin(facingRadians));
		final float pitchSampleBackwardX = this.x - (SAMPLE_RADIUS * (float) Math.cos(facingRadians));
		final float pitchSampleBackwardY = this.y - (SAMPLE_RADIUS * (float) Math.sin(facingRadians));
		final float pitchSampleGroundHeight1 = map.terrain.getGroundHeight(pitchSampleBackwardX, pitchSampleBackwardY);
		final float pitchSampleGorundHeight2 = map.terrain.getGroundHeight(pitchSampleForwardX, pitchSampleForwardY);
		pitch = Math.max(-maxPitch, Math.min(maxPitch,
				(float) Math.atan2(pitchSampleGorundHeight2 - pitchSampleGroundHeight1, SAMPLE_RADIUS * 2)));
		final double leftOfFacingAngle = facingRadians + (Math.PI / 2);
		final float rollSampleForwardX = this.x + (SAMPLE_RADIUS * (float) Math.cos(leftOfFacingAngle));
		final float rollSampleForwardY = this.y + (SAMPLE_RADIUS * (float) Math.sin(leftOfFacingAngle));
		final float rollSampleBackwardX = this.x - (SAMPLE_RADIUS * (float) Math.cos(leftOfFacingAngle));
		final float rollSampleBackwardY = this.y - (SAMPLE_RADIUS * (float) Math.sin(leftOfFacingAngle));
		final float rollSampleGroundHeight1 = map.terrain.getGroundHeight(rollSampleBackwardX, rollSampleBackwardY);
		final float rollSampleGroundHeight2 = map.terrain.getGroundHeight(rollSampleForwardX, rollSampleForwardY);
		roll = Math.max(-maxRoll, Math.min(maxRoll,
				(float) Math.atan2(rollSampleGroundHeight2 - rollSampleGroundHeight1, SAMPLE_RADIUS * 2)));
		this.pitch = pitch;
		this.roll = roll;
		instance.rotate(new Quaternion().setFromAxisRad(RenderMathUtils.VEC3_UNIT_Z, facingRadians));
		instance.rotate(new Quaternion().setFromAxisRad(RenderMathUtils.VEC3_UNIT_Y, -pitch));
		instance.rotate(new Quaternion().setFromAxisRad(RenderMathUtils.VEC3_UNIT_X, roll));
//		instance.rotate(new Quaternion().setEulerAnglesRad(facingRadians, 0, 0));
		instance.scale(scale3D);
		this.selectionScale = selectionScale;
		instance.setScene(map.worldScene);

		this.instance = instance;
		this.row = row;

		final CPlayerFogOfWar fogOfWar = map.getFogOfWar();
		final PathingGrid pathingGrid = map.simulation.getPathingGrid();
		final int fogOfWarIndexX = pathingGrid.getFogOfWarIndexX(this.x);
		final int fogOfWarIndexY = pathingGrid.getFogOfWarIndexY(this.y);
		this.fogState = fogOfWar.getFogState(map.simulation, fogOfWarIndexX, fogOfWarIndexY);
		this.lastFogStateColor = map.getFogShade(this.fogState.getMask());
		for (int i = 0; i < this.vertexColorBase.length; i++) {
			VERTEX_COLOR_HEAP[i] = (this.vertexColorBase[i] * (255 - (this.lastFogStateColor & 0xFF))) / 255f;
		}
		((MdxComplexInstance) this.instance).setVertexColor(VERTEX_COLOR_HEAP);
		applyFogAnimation();
	}

	/** Destructable data has no animInFog field; they keep animating wherever they stand. */
	protected boolean usesAnimInFog() {
		return true;
	}

	/**
	 * A doodad that does not animate in fog stands still and stops emitting particles and ribbons while its place is
	 * out of sight; particles already in the air die out on their own.
	 */
	private void applyFogAnimation() {
		final boolean freeze = !this.animatesInFog && !CFogState.VISIBLE.equals(this.fogState);
		if (freeze != this.fogFrozen) {
			this.fogFrozen = freeze;
			final MdxComplexInstance mdxInstance = (MdxComplexInstance) this.instance;
			mdxInstance.setAnimationSpeed(freeze ? 0f : 1f);
			mdxInstance.emittersSuppressed = freeze;
		}
	}

	public void applyColor(final GameObject row, final int doodadVariation, final ModelInstance instance) {
		String variationString = Integer.toString(doodadVariation);
		if (variationString.length() < 2) {
			variationString = "0" + variationString;
		}
		final int vertR = colorChannel(row, DOODAD_COLOR_RED, variationString, doodadVariation);
		final int vertG = colorChannel(row, DOODAD_COLOR_GREEN, variationString, doodadVariation);
		final int vertB = colorChannel(row, DOODAD_COLOR_BLUE, variationString, doodadVariation);
		((MdxComplexInstance) instance).setVertexColor(new float[] { vertR / 255f, vertG / 255f, vertB / 255f });
	}

	/**
	 * The 3.0 data lists one value per variation (vertR=255,255,...); older data and map edits have a field for each
	 * (vertR01). A missing one would read as 0, which paints the doodad solid black.
	 */
	private static int colorChannel(final GameObject row, final String field, final String variationString,
			final int doodadVariation) {
		final String perVariationField = field + variationString;
		if (!row.getField(perVariationField).isEmpty()) {
			return row.getFieldValue(perVariationField);
		}
		return row.getFieldValue(field, doodadVariation - 1);
	}

	public PrimaryTag getAnimation() {
		return PrimaryTag.STAND;
	}


	/**
	 * @param shadeByState fog shade of each {@link CFogState}, by ordinal, worked out once for the whole pass
	 */
	public void updateFog(final War3MapViewer war3MapViewer, final CPlayerFogOfWar fogOfWar,
			final PathingGrid pathingGrid, final byte[] shadeByState) {
		updateFog(war3MapViewer, fogOfWar, pathingGrid, shadeByState, false);
	}

	/**
	 * @param snap whether the shade jumps to the fog state instead of fading to it, as when a loaded game puts the
	 *             fog in place at once
	 * @return whether the shade has not reached the fog state yet
	 */
	public boolean updateFog(final War3MapViewer war3MapViewer, final CPlayerFogOfWar fogOfWar,
			final PathingGrid pathingGrid, final byte[] shadeByState, final boolean snap) {
		final CFogState newFogState = fogOfWar.getFogState(war3MapViewer.simulation,
				pathingGrid.getFogOfWarIndexX(this.x), pathingGrid.getFogOfWarIndexY(this.y));
		this.fogState = newFogState;
		applyFogAnimation();
		final byte shade = shadeByState[newFogState.ordinal()];
		if (shade != this.lastFogStateColor) {
			this.lastFogStateColor = snap ? shade : War3MapViewer.fadeLineOfSightColor(this.lastFogStateColor, shade);
			for (int i = 0; i < this.vertexColorBase.length; i++) {
				VERTEX_COLOR_HEAP[i] = (this.vertexColorBase[i] * (255 - (this.lastFogStateColor & 0xFF))) / 255f;
			}
			((MdxComplexInstance) this.instance).setVertexColor(VERTEX_COLOR_HEAP);
		}
		return shade != this.lastFogStateColor;
	}

	public float[] getVertexColor() {
		return ((MdxComplexInstance) this.instance).vertexColor;
	}
}
