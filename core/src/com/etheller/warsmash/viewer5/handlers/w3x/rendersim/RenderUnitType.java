package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.EnumSet;
import java.util.function.Supplier;

import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.units.manager.MutableObjectData.WorldEditorDataType;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSoundset;

public class RenderUnitType implements RenderWidgetType {
	private MdxModel model;
	private final Supplier<MdxModel> modelLoader;
	private final Supplier<MdxModel> portraitLoader;
	private boolean portraitLoaded;
	private MdxModel portraitModel;
	/** Since when no instance of the model was seen alive, 0 while one is or the model is not loaded. */
	private long modelIdleSinceNanos;
	private final MdxModel specialArtModel;
	private final ArgbImage buildingPathingPixelMap;
	private final float maxPitch;
	private final float maxRoll;
	private final float sampleRadius;
	private final boolean allowCustomTeamColor;
	private final int teamColor;
	private final float animationRunSpeed;
	private final float scalingValue;
	private final float animationWalkSpeed;
	private final String buildingShadow;
	private final String uberSplat;
	private final float uberSplatScaleValue;
	private final EnumSet<SecondaryTag> requiredAnimationNamesForAttachments;
	private final EnumSet<SecondaryTag> requiredAnimationNames;
	private final RenderShadowType renderShadowType;
	private final UnitSoundset soundset;
	private final Vector3 tintingColor;
	private final float selectScale;
	private final float selectHeight;
	private final int orientationInterpolation;
	private final float blendTime;

	public RenderUnitType(MdxModel model, Supplier<MdxModel> modelLoader, Supplier<MdxModel> portraitLoader, MdxModel specialArtModel,
			ArgbImage buildingPathingPixelMap, final float maxPitch, final float maxRoll, final float sampleRadius,
			final boolean allowCustomTeamColor, final int teamColor, final float animationRunSpeed,
			final float animationWalkSpeed, final float scalingValue, final String buildingShadow,
			final String uberSplat, final float uberSplatScaleValue,
			final EnumSet<SecondaryTag> requiredAnimationNamesForAttachments,
			EnumSet<SecondaryTag> requiredAnimationNames, RenderShadowType renderShadowType, UnitSoundset soundset,
			Vector3 tintingColor, float selectScale, float selectHeight, int orientationInterpolation,
			float blendTime) {
		this.model = model;
		claim(model);
		this.modelLoader = modelLoader;
		this.portraitLoader = portraitLoader;
		this.specialArtModel = specialArtModel;
		this.buildingPathingPixelMap = buildingPathingPixelMap;
		this.maxPitch = maxPitch;
		this.maxRoll = maxRoll;
		this.sampleRadius = sampleRadius;
		this.allowCustomTeamColor = allowCustomTeamColor;
		this.teamColor = teamColor;
		this.animationRunSpeed = animationRunSpeed;
		this.animationWalkSpeed = animationWalkSpeed;
		this.scalingValue = scalingValue;
		this.buildingShadow = buildingShadow;
		this.uberSplat = uberSplat;
		this.uberSplatScaleValue = uberSplatScaleValue;
		this.requiredAnimationNamesForAttachments = requiredAnimationNamesForAttachments;
		this.requiredAnimationNames = requiredAnimationNames;
		this.renderShadowType = renderShadowType;
		this.soundset = soundset;
		this.tintingColor = tintingColor;
		this.selectScale = selectScale;
		this.selectHeight = selectHeight;
		this.orientationInterpolation = orientationInterpolation;
		this.blendTime = blendTime;
	}

	/** The model of the type, read and built again when it was let go (see {@link #releaseModel}). */
	public MdxModel getModel() {
		if (this.model == null) {
			this.model = this.modelLoader.get();
			claim(this.model);
		}
		this.modelIdleSinceNanos = 0;
		return this.model;
	}

	/** Whether the model is loaded and built, so that a unit made now shows it at once. */
	boolean isModelReady() {
		final MdxModel model = this.model;
		return (model != null) && !model.isPending();
	}

	private static void claim(final MdxModel model) {
		if ((model != null) && !"".equals(model.fetchUrl)) {
			model.claim();
		}
	}

	/** Whether the model was shown and no unit or picture of it exists now. */
	boolean isModelIdle() {
		final MdxModel model = this.model;
		return (model != null) && !"".equals(model.fetchUrl) && model.isReleasable() && model.hasBeenShown()
				&& !model.hasLiveInstances();
	}

	long getModelIdleSinceNanos() {
		return this.modelIdleSinceNanos;
	}

	void setModelIdleSinceNanos(final long nanos) {
		this.modelIdleSinceNanos = nanos;
	}

	/**
	 * Lets go of the model of an idle type, which is read again when a unit of the type comes.
	 *
	 * @return the model when no other type holds it either, so that the viewer can drop it; else null
	 */
	MdxModel releaseModel() {
		final MdxModel model = this.model;
		this.model = null;
		this.modelIdleSinceNanos = 0;
		if (this.portraitModel == model) {
			// a type with no portrait of its own shows its model: it is asked for again with the portrait
			this.portraitModel = null;
			this.portraitLoaded = false;
		}
		return model.release() ? model : null;
	}

	/** Made when first asked for: most types are never selected, and their portraits would be read for nothing. */
	public MdxModel getPortraitModel() {
		if (!this.portraitLoaded) {
			this.portraitModel = this.portraitLoader.get();
			this.portraitLoaded = true;
		}
		return this.portraitModel;
	}

	public MdxModel getSpecialArtModel() {
		return this.specialArtModel;
	}

	public ArgbImage getBuildingPathingPixelMap() {
		return this.buildingPathingPixelMap;
	}

	public float getMaxPitch() {
		return this.maxPitch;
	}

	public float getMaxRoll() {
		return this.maxRoll;
	}

	public float getElevationSampleRadius() {
		return this.sampleRadius;
	}

	public boolean isAllowCustomTeamColor() {
		return this.allowCustomTeamColor;
	}

	public int getTeamColor() {
		return this.teamColor;
	}

	public float getAnimationRunSpeed() {
		return this.animationRunSpeed;
	}

	public float getAnimationWalkSpeed() {
		return this.animationWalkSpeed;
	}

	public float getScalingValue() {
		return this.scalingValue;
	}

	public String getBuildingShadow() {
		return this.buildingShadow;
	}

	public String getUberSplat() {
		return this.uberSplat;
	}

	public float getUberSplatScaleValue() {
		return this.uberSplatScaleValue;
	}

	public EnumSet<SecondaryTag> getRequiredAnimationNamesForAttachments() {
		return this.requiredAnimationNamesForAttachments;
	}

	public EnumSet<SecondaryTag> getRequiredAnimationNames() {
		return this.requiredAnimationNames;
	}

	public RenderShadowType getRenderShadowType() {
		return this.renderShadowType;
	}

	public UnitSoundset getSoundset() {
		return this.soundset;
	}

	public Vector3 getTintingColor() {
		return this.tintingColor;
	}

	public float getSelectScale() {
		return this.selectScale;
	}

	public float getSelectHeight() {
		return this.selectHeight;
	}

	public int getOrientationInterpolation() {
		return this.orientationInterpolation;
	}

	public float getBlendTime() {
		return this.blendTime;
	}

	@Override
	public WorldEditorDataType getType() {
		return WorldEditorDataType.UNITS;
	}
}
