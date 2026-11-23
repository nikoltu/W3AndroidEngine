package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.EnumSet;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSoundset;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.BuildingShadow;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandButtonListener;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandCardPopulatingAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.Extrapolator;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PresentationClock;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.UnitFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityEntangledMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityOverlayedMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbilityGenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CRarityControl;

public class RenderUnit implements RenderWidget {
	public static final Color ETHEREAL = new Color(0.75f, 1, 0.5f, 0.5f);
	public static final Color DEFAULT = new Color(1, 1, 1, 1);
	public static final Quaternion tempQuat = new Quaternion();
	private static final float[] heapZ = new float[3];
	// Where the unit shows after the presentation clock ran past the newest frame is corrected over a few frames.
	private static final float CORRECTION_DECAY = 0.6f;
	private static final float MAX_CORRECTION = 256f;
	public MdxComplexInstance instance;
	public final float[] location = new float[3];
	public float selectionScale;
	public UnitSoundset soundset;
	private RenderUnitType portraitType;
	public int playerIndex;
	private final CUnit simulationUnit;
	public static final int LANDMARK_NONE = 0;
	public static final int LANDMARK_GOLD_MINE = 1;
	public static final int LANDMARK_ENTANGLED_MINE = 2;
	public static final int LANDMARK_OTHER_OVERLAY_MINE = 3;
	public static final int LANDMARK_NEUTRAL_BUILDING = 4;
	private int landmarkAbilityCount = -1;
	private CUnitType landmarkUnitType;
	private int landmarkKind;
	public SplatMover shadow;
	public BuildingShadow buildingShadowInstance;
	public SplatMover selectionCircle;
	public SplatMover selectionPreviewHighlight;

	private float facing;

	private boolean swimming;
	private boolean working;
	private boolean invisible;
	private boolean illusionTint;
	// Blue cast the original game gives illusions in the eyes of their owner and allies.
	private static final float[] ILLUSION_TINT = { 0.35f, 0.55f, 1.0f };

	private boolean dead = false;

	private UnitAnimationListenerImpl unitAnimationListenerImpl;
	private OrientationInterpolation orientationInterpolation;
	private float currentTurnVelocity = 0;
	public long lastUnitResponseEndTimeMillis;
	private boolean corpse;
	private boolean boneCorpse;
	private boolean building;
	private RenderUnitType typeData;
	public MdxModel specialArtModel;
	public SplatMover uberSplat;
	private boolean decalsRemovedWhileHidden;
	private SplatMover hiddenUberSplat;
	private boolean buildingShadowHidden;
	private boolean shownToLocalPlayer;
	private RenderFogGhost fogGhost;
	private int shownTeamColor;
	private float selectionHeight;
	private RenderUnit preferredSelectionReplacement;
	private float[] currentColor = { 1, 1, 1, 1 };

	// With a presentation clock the unit is shown from the frames of the game, never from the live simulation unit.
	private int handleId;
	private final MovementType[] movementTypes = MovementType.values();
	private boolean frameMode;
	private boolean havePose;
	private final Extrapolator.UnitPose pose = new Extrapolator.UnitPose();
	private float poseX;
	private float poseY;
	private boolean poseDead;
	private int frameLandmark = LANDMARK_NONE;
	private boolean frameMinimapIconHidden;
	private boolean frameUsesAltMinimapIcon;
	private int frameTeamPlayer;
	private int frameTeamRelation;
	private boolean fadeActive;
	private float fadeRemaining;
	private float fadeTimeout;
	private boolean extrapolatedBefore;
	private float correctionX;
	private float correctionY;

	public RenderUnit(final War3MapViewer map, final float x, final float y, final float z, final int playerIndex,
			final CUnit simulationUnit, final RenderUnitType typeData, final BuildingShadow buildingShadow,
			final float selectionCircleScaleFactor, final float animationWalkSpeed, final float animationRunSpeed,
			final float scalingValue) {
		this.simulationUnit = simulationUnit;
		this.handleId = simulationUnit.getHandleId();
		this.frameMode = map.getPresentation() != null;
		resetRenderUnit(map, x, y, z, playerIndex, simulationUnit, typeData, true, buildingShadow,
				selectionCircleScaleFactor, animationWalkSpeed, animationRunSpeed, scalingValue);

	}

	public MdxModel getPortraitModel() {
		return this.portraitType == null ? null : this.portraitType.getPortraitModel();
	}

	public void resetRenderUnit(final War3MapViewer map, final float x, final float y, final float z,
			final int playerIndex, final CUnit simulationUnit, final RenderUnitType typeData, boolean updatePortrait,
			final BuildingShadow buildingShadow, final float selectionCircleScaleFactor, final float animationWalkSpeed,
			final float animationRunSpeed, final float scalingValue) {
		if (updatePortrait) {
			this.portraitType = typeData;
		}
		this.typeData = typeData;
		this.specialArtModel = typeData.getSpecialArtModel();
		if (this.buildingShadowInstance != null) {
			this.buildingShadowInstance.remove();
		}
		this.buildingShadowInstance = buildingShadow;
		if (this.instance != null) {
			this.instance.detach();
		}
		final MdxComplexInstance instance = (MdxComplexInstance) typeData.getModel().addWaitingInstance();

		this.location[0] = x;
		this.location[1] = y;
		this.location[2] = z;
		instance.move(this.location);
		this.facing = simulationUnit.getFacing();
		final float angle = (float) Math.toRadians(this.facing);
//		instance.localRotation.setFromAxisRad(RenderMathUtils.VEC3_UNIT_Z, angle);
		instance.rotate(tempQuat.setFromAxisRad(RenderMathUtils.VEC3_UNIT_Z, angle));
		this.playerIndex = playerIndex & 0xFFFF;
		this.shownTeamColor = this.playerIndex;
		instance.setTeamColor(this.playerIndex);
		instance.setScene(map.worldScene);
		this.unitAnimationListenerImpl = new UnitAnimationListenerImpl(instance, animationWalkSpeed, animationRunSpeed);
		map.setSimulationAnimationListener(simulationUnit, this.unitAnimationListenerImpl);

		if (typeData != null) {
			boolean changedAnimProps = false;
			for (final SecondaryTag secondaryTag : typeData.getRequiredAnimationNames()) {
				if (this.unitAnimationListenerImpl.addSecondaryTag(secondaryTag)) {
					changedAnimProps = true;
				}
			}
			if (changedAnimProps) {
				this.unitAnimationListenerImpl.forceResetCurrentAnimation();
			}
			map.simulationAnimationTagsReady(simulationUnit, this.unitAnimationListenerImpl);

			heapZ[2] = simulationUnit.getFlyHeight();
			this.location[2] += heapZ[2];

			instance.move(heapZ);
			final Vector3 tintingColor = typeData.getTintingColor();
			this.currentColor = new float[] { tintingColor.x, tintingColor.y, tintingColor.z, 1.0f };
			instance.uniformScale(scalingValue);

			this.selectionScale = typeData.getSelectScale() * selectionCircleScaleFactor;
			this.selectionHeight = typeData.getSelectHeight();
			int orientationInterpolationOrdinal = typeData.getOrientationInterpolation();
			if ((orientationInterpolationOrdinal < 0)
					|| (orientationInterpolationOrdinal >= OrientationInterpolation.VALUES.length)) {
				orientationInterpolationOrdinal = 0;
			}
			this.orientationInterpolation = OrientationInterpolation.VALUES[orientationInterpolationOrdinal];

			final float blendTime = typeData.getBlendTime();
			instance.setBlendTime(blendTime * 1000.0f);
		}

		this.instance = instance;
		if (typeData != null) {
			updateColor(map);
		}
		this.soundset = typeData.getSoundset();
		this.building = simulationUnit.isBuilding();
	}

	public void populateCommandCard(final CSimulation game, final GameUI gameUI,
			final CommandButtonListener commandButtonListener, final AbilityDataUI abilityDataUI,
			final int subMenuOrderId, final boolean multiSelect, final int localPlayerIndex) {
		final CommandCardPopulatingAbilityVisitor commandCardPopulatingVisitor = CommandCardPopulatingAbilityVisitor.INSTANCE
				.reset(game, gameUI, this.simulationUnit, commandButtonListener, abilityDataUI, subMenuOrderId,
						multiSelect, localPlayerIndex);
		for (final CAbility ability : this.simulationUnit.getAbilities()) {
			if (!this.simulationUnit.isPaused() || (ability instanceof CBuff)
					|| (ability instanceof AbilityGenericSingleIconPassiveAbility)) {
				final int containerMenuOrderId = ability.getContainerMenuOrderId();
				if (containerMenuOrderId == 0) {
					ability.visit(commandCardPopulatingVisitor);
				}
				else if (containerMenuOrderId == subMenuOrderId) {
					// The spells of a spell book are on its menu; they show as if on the main card.
					commandCardPopulatingVisitor.setMenuBaseOrderId(0);
					ability.visit(commandCardPopulatingVisitor);
					commandCardPopulatingVisitor.setMenuBaseOrderId(subMenuOrderId);
				}
			}
		}
	}

	@Override
	public void updateAnimations(final War3MapViewer map) {
		final PresentationClock clock = map.getPresentation();
		final boolean fromFrame = clock != null;
		final UnitFrameState frameState;
		final int frameRow;
		final int frameFlags;
		final int teamColor;
		if (fromFrame) {
			this.frameMode = true;
			this.havePose = Extrapolator.sampleUnit(clock, this.handleId, this.pose);
			if (!this.havePose) {
				// no frame has the unit yet, it shows once one has
				this.instance.hide();
				return;
			}
			frameState = this.pose.state;
			frameRow = this.pose.row;
			frameFlags = frameState.flags[frameRow];
			this.poseX = this.pose.x;
			this.poseY = this.pose.y;
			this.poseDead = (frameFlags & UnitFrameState.DEAD) != 0;
			this.frameTeamPlayer = frameState.teamPlayer[frameRow];
			this.frameTeamRelation = frameState.relation[frameRow];
			this.frameLandmark = frameState.landmark[frameRow];
			this.frameMinimapIconHidden = (frameFlags & UnitFrameState.HIDE_MINIMAP_ICON) != 0;
			this.frameUsesAltMinimapIcon = (frameFlags & UnitFrameState.ALT_MINIMAP_ICON) != 0;
			this.fadeActive = (frameFlags & UnitFrameState.FADING) != 0;
			this.fadeRemaining = this.pose.fadeRemaining;
			this.fadeTimeout = frameState.fadeTimeout[frameRow];
			teamColor = map.getAllyColorFilter().worldColorOfRelation(this.frameTeamRelation, this.playerIndex);
		}
		else {
			frameState = null;
			frameRow = -1;
			frameFlags = 0;
			teamColor = map.getAllyColorFilter().worldColor(map.simulation, map.getLocalPlayerIndex(),
					this.simulationUnit.getFakePlayerIndex(), this.playerIndex);
		}
		if (teamColor != this.shownTeamColor) {
			this.shownTeamColor = teamColor;
			this.instance.setTeamColor(teamColor);
		}
		this.instance.teamGlowHidden = !(fromFrame ? (frameFlags & UnitFrameState.TEAM_GLOW) != 0
				: this.simulationUnit.isTeamGlowShown());
		final boolean wasHidden = this.instance.hidden();
		final boolean removed = fromFrame ? (frameFlags & UnitFrameState.HIDDEN) != 0 : this.simulationUnit.isHidden();
		if (removed != this.decalsRemovedWhileHidden) {
			// A hidden building (a gold mine under a Haunted Gold Mine or Entangled Gold Mine) takes its ground
			// texture and building shadow with it; fog alone keeps them, as for any remembered building.
			this.decalsRemovedWhileHidden = removed;
			if (removed) {
				removeBuildingDecalSplats(map);
			}
			else if (this.building && !simulationDead()) {
				createBuildingDecalSplats(map);
			}
		}
		if (removed || !isShownToLocalPlayer(map, fromFrame, frameFlags)) {
			if (this.shownToLocalPlayer) {
				this.shownToLocalPlayer = false;
				if (!this.dead && (this.fogGhost == null) && !removed
						&& (fromFrame ? (frameFlags & UnitFrameState.BUILDING) != 0
								: this.simulationUnit.isBuilding())
						&& ((fromFrame ? frameState.owner[frameRow] : this.simulationUnit.getPlayerIndex()) != map
								.getLocalPlayerIndex())) {
					this.fogGhost = map.addFogGhost(this);
				}
			}
			showBuildingDecals(map, (this.fogGhost != null) && this.fogGhost.isShown());
			if (!wasHidden) {
				if (this.selectionCircle != null) {
					this.selectionCircle.hide();
				}
				if (this.selectionPreviewHighlight != null) {
					this.selectionPreviewHighlight.hide();
				}
				if (this.shadow != null) {
					this.shadow.hide();
				}
			}
			this.instance.hide();
			return;
		} else {
			this.shownToLocalPlayer = true;
			this.instance.show();
			showBuildingDecals(map, true);
			if (wasHidden) {
				if (this.selectionCircle != null) {
					this.selectionCircle.show(map.terrain.centerOffset);
				}
				if (this.selectionPreviewHighlight != null) {
					this.selectionPreviewHighlight.show(map.terrain.centerOffset);
				}
				if (this.shadow != null) {
					this.shadow.show(map.terrain.centerOffset);
				}
				repositioned(map);
			}
		}
		final float prevX = this.location[0];
		final float prevY = this.location[1];
		final float deltaTime = Gdx.graphics.getDeltaTime();
		if (fromFrame) {
			placeFromFrame();
		}
		else {
			final float simulationX = this.simulationUnit.getX();
			final float simulationY = this.simulationUnit.getY();
			final float simDx = simulationX - this.location[0];
			final float simDy = simulationY - this.location[1];
			final float distanceToSimulation = (float) Math.sqrt((simDx * simDx) + (simDy * simDy));
			final int speed = this.simulationUnit.getSpeed();
			final float speedDelta = speed * deltaTime;
			if ((distanceToSimulation > speedDelta) && (deltaTime < 1.0)) {
				// The 1.0 here says that after 1 second of lag, units just teleport to show
				// where they actually are
				this.location[0] += (speedDelta * simDx) / distanceToSimulation;
				this.location[1] += (speedDelta * simDy) / distanceToSimulation;
			} else {
				this.location[0] = simulationX;
				this.location[1] = simulationY;
			}
		}
		final float dx = this.location[0] - prevX;
		final float dy = this.location[1] - prevY;
		final float groundHeight;
		final MovementType movementType = fromFrame ? this.movementTypes[frameState.movementType[frameRow]]
				: this.simulationUnit.getMovementType();
		final short terrainPathing = map.terrain.pathingGrid.getPathing(this.location[0], this.location[1]);
		boolean swimming = (movementType == MovementType.AMPHIBIOUS)
				&& PathingGrid.isPathingFlag(terrainPathing, PathingGrid.PathingType.SWIMMABLE)
				&& !PathingGrid.isPathingFlag(terrainPathing, PathingGrid.PathingType.WALKABLE);
		final boolean invisible = ((fromFrame ? frameState.invisLevels[frameRow] : this.simulationUnit.getInvisLevels())
				& 1) > 0;
		final boolean working = fromFrame ? (frameFlags & UnitFrameState.BUSY) != 0
				: this.simulationUnit.getBuildQueueTypes()[0] != null;
		final float groundHeightTerrain = map.terrain.getGroundHeight(this.location[0], this.location[1]);
		float groundHeightTerrainAndWater;
		MdxComplexInstance currentWalkableUnder;
		final boolean standingOnWater = (swimming) || (movementType == MovementType.FLOAT)
				|| (movementType == MovementType.FLY) || (movementType == MovementType.HOVER);
		if (standingOnWater) {
			groundHeightTerrainAndWater = Math.max(groundHeightTerrain,
					map.terrain.getWaterHeight(this.location[0], this.location[1]));
		} else {
			// land units will have their feet pass under the surface of the water
			groundHeightTerrainAndWater = groundHeightTerrain;
		}
		if (movementType == MovementType.FLOAT) {
			// boats cant go on bridges
			groundHeight = groundHeightTerrainAndWater;
			currentWalkableUnder = null;
		} else {
			currentWalkableUnder = map.getHighestWalkableUnder(this.location[0], this.location[1]);
			War3MapViewer.gdxRayHeap.set(this.location[0], this.location[1], 4096, 0, 0, -8192);
			if ((currentWalkableUnder != null)
					&& currentWalkableUnder.intersectRayWithCollision(War3MapViewer.gdxRayHeap,
							War3MapViewer.intersectionHeap, true, true)
					&& (War3MapViewer.intersectionHeap.z > groundHeightTerrainAndWater)) {
				groundHeight = War3MapViewer.intersectionHeap.z;
				swimming = false; // Naga Royal Guard should slither across a bridge, not swim in rock
			} else {
				groundHeight = groundHeightTerrainAndWater;
				currentWalkableUnder = null;
			}
		}
		boolean changedAnimationProperties = false;
		if (swimming && !this.swimming) {
			if (this.unitAnimationListenerImpl.addSecondaryTag(AnimationTokens.SecondaryTag.SWIM)) {
				changedAnimationProperties = true;
			}
		} else if (!swimming && this.swimming) {
			if (this.unitAnimationListenerImpl.removeSecondaryTag(AnimationTokens.SecondaryTag.SWIM)) {
				changedAnimationProperties = true;
			}
		}
		if (working && !this.working) {
			if (this.unitAnimationListenerImpl.addSecondaryTag(AnimationTokens.SecondaryTag.WORK)) {
				changedAnimationProperties = true;
			}
		} else if (!working && this.working) {
			if (this.unitAnimationListenerImpl.removeSecondaryTag(AnimationTokens.SecondaryTag.WORK)) {
				changedAnimationProperties = true;
			}
		}
		if (changedAnimationProperties) {
			this.unitAnimationListenerImpl.forceResetCurrentAnimation();
		}
		boolean colorNeedsUpdate = false;
		if (invisible && !this.invisible) {
			// turn invisible instantly
			colorNeedsUpdate = true;
		} else if (!invisible && this.invisible) {
			// show instantly
			colorNeedsUpdate = true;
		} else if (fromFrame ? this.fadeActive
				: (this.simulationUnit.getFadeTimer() != null) && this.simulationUnit.getFadeTimer().isRunning()) {
			colorNeedsUpdate = true;
		}
		final boolean illusionTint = fromFrame ? (frameFlags & UnitFrameState.ILLUSION_ALLY) != 0
				: this.simulationUnit.isIllusion() && map.simulation.getPlayer(map.getLocalPlayerIndex())
						.hasAlliance(this.simulationUnit.getPlayerIndex(), CAllianceType.PASSIVE);
		if (illusionTint != this.illusionTint) {
			this.illusionTint = illusionTint;
			colorNeedsUpdate = true;
		}
		this.invisible = invisible;
		this.swimming = swimming;
		this.working = working;
		if (colorNeedsUpdate) {
			updateColor(map);
		}
		final boolean dead = fromFrame ? (frameFlags & UnitFrameState.DEAD) != 0 : this.simulationUnit.isDead();
		final boolean corpse = fromFrame ? (frameFlags & UnitFrameState.CORPSE) != 0 : this.simulationUnit.isCorpse();
		final boolean boneCorpse = fromFrame ? (frameFlags & UnitFrameState.BONE_CORPSE) != 0
				: this.simulationUnit.isBoneCorpse();
		final boolean building = fromFrame ? (frameFlags & UnitFrameState.BUILDING) != 0
				: this.simulationUnit.isBuilding();
		// With scene shadows a corpse is geometry lying on the ground and keeps casting until it is gone.
		this.instance.groundShadow = building ? !dead && map.getDynamicShadowManager().isSceneShadows()
				: (!dead || map.getDynamicShadowManager().isSceneShadows())
						&& this.typeData.getRenderShadowType() != null;
		if (dead) {
			if (!this.dead) {
				this.unitAnimationListenerImpl.playAnimation(true, PrimaryTag.DEATH, SequenceUtils.EMPTY, 1.0f, true);
				removeSplats(map);
			}
		} else if (building != this.building) {
			if (building) {
				if (this.shadow != null) {
					this.shadow.hide();
				}
				createBuildingDecalSplats(map);
			} else {
				if (this.shadow != null) {
					this.shadow.show(map.terrain.centerOffset);
				}
				removeBuildingDecalSplats(map);
			}
			this.building = building;
		}
		if (boneCorpse && !this.boneCorpse) {
			final float endingDecayTime = fromFrame ? frameState.decayTime[frameRow]
					: this.simulationUnit.getEndingDecayTime(map.simulation);
			if (fromFrame ? (frameFlags & UnitFrameState.HERO) != 0 : this.simulationUnit.getUnitType().isHero()) {
				this.unitAnimationListenerImpl.playAnimationWithDuration(true, PrimaryTag.DISSIPATE,
						SequenceUtils.EMPTY, endingDecayTime, true);
			} else {
				this.unitAnimationListenerImpl.playAnimationWithDuration(true, PrimaryTag.DECAY, SequenceUtils.BONE,
						endingDecayTime, true);
			}
		} else if (corpse && !this.corpse) {
			this.unitAnimationListenerImpl.playAnimationWithDuration(true, PrimaryTag.DECAY, SequenceUtils.FLESH,
					fromFrame ? frameState.decayTime[frameRow]
							: map.simulation.getGameplayConstants().getDecayTime(),
					true);
		}
		this.dead = dead;
		this.corpse = corpse;
		this.boneCorpse = boneCorpse;
		this.location[2] = (fromFrame ? this.pose.z : this.simulationUnit.getFlyHeight()) + groundHeight;
		final float selectionCircleHeight = this.selectionHeight + groundHeight;
		this.instance.moveTo(this.location);
		float simulationFacing = fromFrame ? this.pose.facing : this.simulationUnit.getFacing();
		if (simulationFacing < 0) {
			simulationFacing += 360;
		}
		float renderFacing = this.facing;
		if (renderFacing < 0) {
			renderFacing += 360;
		}
		float facingDelta = simulationFacing - renderFacing;
		if (facingDelta < -180) {
			facingDelta = 360 + facingDelta;
		}
		if (facingDelta > 180) {
			facingDelta = -360 + facingDelta;
		}
		final float absoluteFacingDelta = Math.abs(facingDelta);
		final float turningSign = Math.signum(facingDelta);

		final float absoluteFacingDeltaRadians = (float) Math.toRadians(absoluteFacingDelta);
		float acceleration;
		final boolean endPhase = (absoluteFacingDeltaRadians <= this.orientationInterpolation.getEndingAccelCutoff())
				&& ((this.currentTurnVelocity * turningSign) > 0);
		if (endPhase) {
			this.currentTurnVelocity = (1
					- ((this.orientationInterpolation.getEndingAccelCutoff() - absoluteFacingDeltaRadians)
							/ this.orientationInterpolation.getEndingAccelCutoff()))
					* (this.orientationInterpolation.getMaxVelocity()) * turningSign;
		} else {
			acceleration = this.orientationInterpolation.getStartingAcceleration() * turningSign;
			this.currentTurnVelocity = this.currentTurnVelocity + acceleration;
		}
		if ((this.currentTurnVelocity * turningSign) > this.orientationInterpolation.getMaxVelocity()) {
			this.currentTurnVelocity = this.orientationInterpolation.getMaxVelocity() * turningSign;
		}
		float angleToAdd = (float) ((Math.toDegrees(this.currentTurnVelocity) * deltaTime) / 0.03f);

		if (absoluteFacingDelta < Math.abs(angleToAdd)) {
			angleToAdd = facingDelta;
			this.currentTurnVelocity = 0.0f;
		}
		this.facing = (((this.facing + angleToAdd) % 360) + 360) % 360;
		this.instance.setLocalRotation(tempQuat.setFromAxis(RenderMathUtils.VEC3_UNIT_Z, this.facing));

		final float facingRadians = (float) Math.toRadians(this.facing);
		final float maxPitch = (float) Math.toRadians(this.typeData.getMaxPitch());
		final float maxRoll = (float) Math.toRadians(this.typeData.getMaxRoll());
		final float sampleRadius = this.typeData.getElevationSampleRadius();
		float pitch, roll;
		final float pitchSampleForwardX = this.location[0] + (sampleRadius * (float) Math.cos(facingRadians));
		final float pitchSampleForwardY = this.location[1] + (sampleRadius * (float) Math.sin(facingRadians));
		final float pitchSampleBackwardX = this.location[0] - (sampleRadius * (float) Math.cos(facingRadians));
		final float pitchSampleBackwardY = this.location[1] - (sampleRadius * (float) Math.sin(facingRadians));
		final double leftOfFacingAngle = facingRadians + (Math.PI / 2);
		final float rollSampleForwardX = this.location[0] + (sampleRadius * (float) Math.cos(leftOfFacingAngle));
		final float rollSampleForwardY = this.location[1] + (sampleRadius * (float) Math.sin(leftOfFacingAngle));
		final float rollSampleBackwardX = this.location[0] - (sampleRadius * (float) Math.cos(leftOfFacingAngle));
		final float rollSampleBackwardY = this.location[1] - (sampleRadius * (float) Math.sin(leftOfFacingAngle));
		final float pitchSampleGroundHeight1;
		final float pitchSampleGroundHeight2;
		final float rollSampleGroundHeight1;
		final float rollSampleGroundHeight2;
		if (currentWalkableUnder != null) {
			pitchSampleGroundHeight1 = getGroundHeightSample(groundHeight, currentWalkableUnder, pitchSampleBackwardX,
					pitchSampleBackwardY);
			pitchSampleGroundHeight2 = getGroundHeightSample(groundHeight, currentWalkableUnder, pitchSampleForwardX,
					pitchSampleForwardY);
			rollSampleGroundHeight1 = getGroundHeightSample(groundHeight, currentWalkableUnder, rollSampleBackwardX,
					rollSampleBackwardY);
			rollSampleGroundHeight2 = getGroundHeightSample(groundHeight, currentWalkableUnder, rollSampleForwardX,
					rollSampleForwardY);
		} else {
			final float pitchGroundHeight1 = map.terrain.getGroundHeight(pitchSampleBackwardX, pitchSampleBackwardY);
			final float pitchGroundHeight2 = map.terrain.getGroundHeight(pitchSampleForwardX, pitchSampleForwardY);
			final float rollGroundHeight1 = map.terrain.getGroundHeight(rollSampleBackwardX, rollSampleBackwardY);
			final float rollGroundHeight2 = map.terrain.getGroundHeight(rollSampleForwardX, rollSampleForwardY);
			if (standingOnWater) {
				pitchSampleGroundHeight1 = Math.max(pitchGroundHeight1,
						map.terrain.getWaterHeight(pitchSampleBackwardX, pitchSampleBackwardY));
				pitchSampleGroundHeight2 = Math.max(pitchGroundHeight2,
						map.terrain.getWaterHeight(pitchSampleForwardX, pitchSampleForwardY));
				rollSampleGroundHeight1 = Math.max(rollGroundHeight1,
						map.terrain.getWaterHeight(rollSampleBackwardX, rollSampleBackwardY));
				rollSampleGroundHeight2 = Math.max(rollGroundHeight2,
						map.terrain.getWaterHeight(rollSampleForwardX, rollSampleForwardY));
			} else {
				pitchSampleGroundHeight1 = pitchGroundHeight1;
				pitchSampleGroundHeight2 = pitchGroundHeight2;
				rollSampleGroundHeight1 = rollGroundHeight1;
				rollSampleGroundHeight2 = rollGroundHeight2;
			}
		}
		pitch = Math.max(-maxPitch, Math.min(maxPitch,
				(float) Math.atan2(pitchSampleGroundHeight2 - pitchSampleGroundHeight1, sampleRadius * 2)));
		roll = Math.max(-maxRoll, Math.min(maxRoll,
				(float) Math.atan2(rollSampleGroundHeight2 - rollSampleGroundHeight1, sampleRadius * 2)));
		this.instance.rotate(tempQuat.setFromAxisRad(RenderMathUtils.VEC3_UNIT_Y, -pitch));
		this.instance.rotate(tempQuat.setFromAxisRad(RenderMathUtils.VEC3_UNIT_X, roll));

		map.worldScene.instanceMoved(this.instance, this.location[0], this.location[1]);
		if (this.building && ((dx != 0) || (dy != 0))) {
			moveBuildingDecals(map, this.location[0], this.location[1], dx, dy);
		}
		if (this.shadow != null) {
			this.shadow.move(dx, dy, map.terrain.centerOffset);
			this.shadow.setHeightAbsolute(currentWalkableUnder != null, groundHeight + map.imageWalkableZOffset);
		}
		if (this.selectionCircle != null) {
			this.selectionCircle.move(dx, dy, map.terrain.centerOffset);
			this.selectionCircle.setHeightAbsolute(
					(currentWalkableUnder != null)
							|| ((movementType == MovementType.FLY) || (movementType == MovementType.HOVER)),
					selectionCircleHeight + map.imageWalkableZOffset);
		}
		if (this.selectionPreviewHighlight != null) {
			this.selectionPreviewHighlight.move(dx, dy, map.terrain.centerOffset);
			this.selectionPreviewHighlight.setHeightAbsolute(
					(currentWalkableUnder != null)
							|| ((movementType == MovementType.FLY) || (movementType == MovementType.HOVER)),
					selectionCircleHeight + map.imageWalkableZOffset);
		}
		this.unitAnimationListenerImpl.update();
		if (fromFrame) {
			if (!dead && ((frameFlags & UnitFrameState.CONSTRUCTING) != 0)) {
				this.instance.setFrameByRatio(this.pose.constructRatio);
			}
		}
		else if (!dead && this.simulationUnit.isConstructingOrUpgrading()) {
			final War3ID upgradeIdType = this.simulationUnit.getUpgradeIdType();
			final CUnitType constructedType = upgradeIdType != null
					? map.simulation.getUnitData().getUnitType(upgradeIdType)
					: this.simulationUnit.getUnitType();
			this.instance.setFrameByRatio(
					this.simulationUnit.getConstructionProgress() / constructedType.getBuildTime());
		}
	}

	/** Places the unit where the frames around the presentation time put it. */
	private void placeFromFrame() {
		float x = this.pose.x;
		float y = this.pose.y;
		if (this.pose.extrapolated) {
			this.extrapolatedBefore = true;
		}
		else if (this.extrapolatedBefore) {
			// a frame came after the clock ran past the last one: the way back to the real position is walked, not jumped
			this.extrapolatedBefore = false;
			final float errorX = this.location[0] - x;
			final float errorY = this.location[1] - y;
			if (((errorX * errorX) + (errorY * errorY)) <= (MAX_CORRECTION * MAX_CORRECTION)) {
				this.correctionX = errorX;
				this.correctionY = errorY;
			}
			else {
				this.correctionX = 0;
				this.correctionY = 0;
			}
		}
		x += this.correctionX;
		y += this.correctionY;
		this.correctionX *= CORRECTION_DECAY;
		this.correctionY *= CORRECTION_DECAY;
		if (((this.correctionX * this.correctionX) + (this.correctionY * this.correctionY)) < 0.01f) {
			this.correctionX = 0;
			this.correctionY = 0;
		}
		this.location[0] = x;
		this.location[1] = y;
	}

	private float simulationX() {
		return this.frameMode ? (this.havePose ? this.poseX : this.location[0]) : this.simulationUnit.getX();
	}

	private float simulationY() {
		return this.frameMode ? (this.havePose ? this.poseY : this.location[1]) : this.simulationUnit.getY();
	}

	private boolean simulationDead() {
		return this.frameMode ? this.poseDead : this.simulationUnit.isDead();
	}

	private void removeSplats(final War3MapViewer map) {
		if (this.shadow != null) {
			this.shadow.destroy(Gdx.gl30, map.terrain.centerOffset);
			this.shadow = null;
		}
		removeBuildingDecalSplats(map);
		if (this.selectionCircle != null) {
			this.selectionCircle.destroy(Gdx.gl30, map.terrain.centerOffset);
			this.selectionCircle = null;
		}
		if (this.selectionPreviewHighlight != null) {
			this.selectionPreviewHighlight.destroy(Gdx.gl30, map.terrain.centerOffset);
			this.selectionPreviewHighlight = null;
		}
	}

	public void removeBuildingDecalSplats(final War3MapViewer map) {
		if (this.buildingShadowInstance != null) {
			this.buildingShadowInstance.remove();
			this.buildingShadowInstance = null;
		}
		if (this.uberSplat != null) {
			this.uberSplat.destroy(Gdx.gl30, map.terrain.centerOffset);
			this.uberSplat = null;
		}
	}

	public void createBuildingDecalSplats(final War3MapViewer map) {
		final float unitX = simulationX();
		final float unitY = simulationY();
		if (this.buildingShadowInstance == null) {
			final String buildingShadow = this.typeData.getBuildingShadow();
			if (buildingShadow != null) {
				this.buildingShadowInstance = map.terrain.addShadow(buildingShadow, unitX, unitY);
			}
		}
		if (this.uberSplat == null) {
			final String uberSplatTexturePath = this.typeData.getUberSplat();
			if (uberSplatTexturePath != null) {
				this.uberSplat = map.addUberSplatIngame(unitX, unitY, uberSplatTexturePath,
						this.typeData.getUberSplatScaleValue());
			}
		}
	}

	private float getGroundHeightSample(final float groundHeight, final MdxComplexInstance currentWalkableUnder,
			final float sampleX, final float sampleY) {
		final float sampleGroundHeight;
		War3MapViewer.gdxRayHeap.origin.x = sampleX;
		War3MapViewer.gdxRayHeap.origin.y = sampleY;
		if (currentWalkableUnder.intersectRayWithCollision(War3MapViewer.gdxRayHeap, War3MapViewer.intersectionHeap,
				true, true)) {
			sampleGroundHeight = War3MapViewer.intersectionHeap.z;
		} else {
			sampleGroundHeight = groundHeight;
		}
		return sampleGroundHeight;
	}

	public CUnit getSimulationUnit() {
		return this.simulationUnit;
	}

	public EnumSet<AnimationTokens.SecondaryTag> getSecondaryAnimationTags() {
		return this.unitAnimationListenerImpl.secondaryAnimationTags;
	}

	private void moveBuildingDecals(final War3MapViewer map, final float x, final float y, final float dx,
			final float dy) {
		if (this.buildingShadowInstance != null) {
			this.buildingShadowInstance.move(x, y);
		}
		if (this.uberSplat != null) {
			this.uberSplat.move(dx, dy, map.terrain.centerOffset);
		}
	}

	public void repositioned(final War3MapViewer map) {
		final float prevX = this.location[0];
		final float prevY = this.location[1];
		final float simulationX = simulationX();
		final float simulationY = simulationY();
		final float dx = simulationX - prevX;
		final float dy = simulationY - prevY;
		if (this.shadow != null) {
			this.shadow.move(dx, dy, map.terrain.centerOffset);
		}
		if (this.selectionCircle != null) {
			this.selectionCircle.move(dx, dy, map.terrain.centerOffset);
		}
		if (this.selectionPreviewHighlight != null) {
			this.selectionPreviewHighlight.move(dx, dy, map.terrain.centerOffset);
		}
		if (this.building && ((dx != 0) || (dy != 0))) {
			moveBuildingDecals(map, simulationX, simulationY, dx, dy);
		}
		this.location[0] = simulationX;
		this.location[1] = simulationY;
	}

	@Override
	public MdxComplexInstance getInstance() {
		return this.instance;
	}

	@Override
	public CWidget getSimulationWidget() {
		return this.simulationUnit;
	}

	@Override
	public boolean isIntersectedOnMeshAlways() {
		return this.frameMode ? this.building : this.simulationUnit.isBuilding();
	}

	@Override
	public float getSelectionScale() {
		return this.selectionScale;
	}

	@Override
	public float getX() {
		return this.location[0];
	}

	@Override
	public float getY() {
		return this.location[1];
	}

	@Override
	public float getZ() {
		return this.location[2];
	}

	@Override
	public void unassignSelectionCircle() {
		this.selectionCircle = null;
	}

	@Override
	public void assignSelectionCircle(final SplatMover t) {
		this.selectionCircle = t;
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
		return this.simulationUnit.isVisible(simulation, byPlayer)
				&& this.simulationUnit.isSelectable(simulation, byPlayer);
	}

	@Override
	public SplatMover getSelectionPreviewHighlight() {
		return this.selectionPreviewHighlight;
	}

	public void onRemove(final War3MapViewer map) {
		if (this.fogGhost != null) {
			this.fogGhost.takeBuildingDecals(this.buildingShadowInstance, this.uberSplat);
			this.buildingShadowInstance = null;
			this.uberSplat = null;
			this.fogGhost = null;
		}
		removeSplats(map);
	}

	/** A ghost that is drawn, not one under the black mask. */
	public boolean hasShownFogGhost() {
		return (this.fogGhost != null) && this.fogGhost.isShown();
	}

	/**
	 * The ground texture and shadow of a building show where the building does or its ghost does: a building never
	 * seen, or remembered under the black mask, does not give itself away by them.
	 */
	private void showBuildingDecals(final War3MapViewer map, final boolean shown) {
		if (shown) {
			if ((this.hiddenUberSplat != null) && (this.hiddenUberSplat == this.uberSplat)) {
				this.uberSplat.show(map.terrain.centerOffset);
			}
			this.hiddenUberSplat = null;
			if (this.buildingShadowHidden) {
				this.buildingShadowHidden = false;
				final String buildingShadow = this.typeData.getBuildingShadow();
				if ((this.buildingShadowInstance == null) && (buildingShadow != null) && this.building
						&& !simulationDead()) {
					this.buildingShadowInstance = map.terrain.addShadow(buildingShadow, simulationX(),
							simulationY());
				}
			}
		}
		else {
			if ((this.uberSplat != null) && (this.hiddenUberSplat != this.uberSplat)) {
				this.uberSplat.hide();
				this.hiddenUberSplat = this.uberSplat;
			}
			if (this.buildingShadowInstance != null) {
				this.buildingShadowInstance.remove();
				this.buildingShadowInstance = null;
				this.buildingShadowHidden = true;
			}
		}
	}

	void fogGhostMaskChanged(final War3MapViewer map) {
		if (!this.shownToLocalPlayer) {
			showBuildingDecals(map, (this.fogGhost != null) && this.fogGhost.isShown());
		}
	}

	void fogGhostRemoved(final RenderFogGhost ghost) {
		if (this.fogGhost == ghost) {
			this.fogGhost = null;
		}
	}

	/**
	 * A unit that dies where the local player cannot see stays out of sight, a building as it was last seen.
	 */
	private boolean isShownToLocalPlayer(final War3MapViewer map, final boolean fromFrame, final int frameFlags) {
		if (map.isObserverView()) {
			return true;
		}
		if (fromFrame) {
			return (frameFlags & UnitFrameState.VISIBLE_TO_LOCAL) != 0;
		}
		if (this.simulationUnit.isDead()) {
			return map.getFogOfWar().isVisible(map.simulation, map.simulation.getPathingGrid(),
					this.simulationUnit.getX(), this.simulationUnit.getY());
		}
		return map.isShownToViewer(this.simulationUnit);
	}

	public void setPreferredSelectionReplacement(final RenderUnit preferredSelectionReplacement) {
		this.preferredSelectionReplacement = preferredSelectionReplacement;
	}

	public RenderUnit getPreferredSelectionReplacement() {
		return this.preferredSelectionReplacement;
	}

	@Override
	public SplatMover getSelectionCircle() {
		return this.selectionCircle;
	}

	public boolean groupsWith(final RenderUnit selectedUnit) {
		if (this.frameMode) {
			return this.typeData == selectedUnit.typeData;
		}
		return this.simulationUnit.getUnitType() == selectedUnit.getSimulationUnit().getUnitType();
	}

	public void setPlayerColor(final int ordinal) {
		this.playerIndex = ordinal;
		this.shownTeamColor = ordinal;
		getInstance().setTeamColor(ordinal);
	}

	public float getFacing() {
		return this.facing;
	}

	public void setFacing(final float facing) {
		this.facing = facing;
	}

	@Override
	public boolean isShowSelectionCircleAboveWater() {
		return this.simulationUnit.isMovementOnWaterAllowed()
				|| (this.simulationUnit.getMovementType() == MovementType.HOVER)
				|| (this.simulationUnit.getMovementType() == MovementType.FLY);
	}

	/**
	 * What kind of minimap landmark the unit is, kept until its ability list or type changes: the abilities are
	 * searched for the mine data, and the minimap asks for every unit every frame.
	 */
	public int getMinimapLandmarkKind() {
		if (this.frameMode) {
			return this.frameLandmark;
		}
		final int abilityCount = this.simulationUnit.getAbilities().size();
		final CUnitType unitType = this.simulationUnit.getUnitType();
		if ((abilityCount != this.landmarkAbilityCount) || (unitType != this.landmarkUnitType)) {
			this.landmarkAbilityCount = abilityCount;
			this.landmarkUnitType = unitType;
			this.landmarkKind = minimapLandmarkKindOf(this.simulationUnit);
		}
		return this.landmarkKind;
	}

	public static int minimapLandmarkKindOf(final CUnit unit) {
		if (unit.getGoldMineData() != null) {
			return LANDMARK_GOLD_MINE;
		}
		final CAbilityOverlayedMine overlayed = unit.getOverlayedGoldMineData();
		if (overlayed != null) {
			return overlayed instanceof CAbilityEntangledMine ? LANDMARK_ENTANGLED_MINE : LANDMARK_OTHER_OVERLAY_MINE;
		}
		return unit.getUnitType().isNeutralBuildingShowMinimapIcon() ? LANDMARK_NEUTRAL_BUILDING : LANDMARK_NONE;
	}

	/** The player the unit is drawn as, and what the local player takes it for; from the frames of the game. */
	public int getFrameTeamPlayer() {
		return this.frameTeamPlayer;
	}

	public int getFrameTeamRelation() {
		return this.frameTeamRelation;
	}

	public boolean isFrameMinimapIconHidden() {
		return this.frameMinimapIconHidden;
	}

	public boolean isFrameUsesAltMinimapIcon() {
		return this.frameUsesAltMinimapIcon;
	}

	public RenderUnitType getTypeData() {
		return this.typeData;
	}

	public void setVertexColoring(final War3MapViewer map, final Color color) {
		this.currentColor = new float[] { color.r, color.g, color.b, color.a };
		updateColor(map);
	}

	public void setVertexColoring(final War3MapViewer map, final float r, final float g, final float b) {
		this.currentColor = new float[] { r, g, b, 1 };
		updateColor(map);
	}

	public void setVertexColoring(final War3MapViewer map, final float r, final float g, final float b, final float a) {
		final float[] color = new float[] { r, g, b, a };
		this.currentColor = color;
		updateColor(map);
	}

	private void updateColor(final War3MapViewer map) {
		float finalAlpha = this.currentColor[3];
		if (this.invisible) {
			finalAlpha *= 0.5f;
		} else if (this.frameMode) {
			if (this.fadeActive && (this.fadeTimeout > 0)) {
				finalAlpha *= ((this.fadeRemaining / this.fadeTimeout) * 0.5f) + 0.5f;
			}
		} else {
			final CTimer fadeTimer = this.simulationUnit.getFadeTimer();
			if ((fadeTimer != null) && fadeTimer.isRunning()) {
				final float fadeAmount = map.getRemainingSecondsForRender(fadeTimer) / fadeTimer.getTimeoutTime();
				finalAlpha *= (fadeAmount * 0.5f) + 0.5f;
			}
		}
		if (this.illusionTint) {
			this.instance.setVertexColor(this.currentColor[0] * ILLUSION_TINT[0],
					this.currentColor[1] * ILLUSION_TINT[1], this.currentColor[2] * ILLUSION_TINT[2], finalAlpha);
			return;
		}
		this.instance.setVertexColor(this.currentColor[0], this.currentColor[1], this.currentColor[2], finalAlpha);
	}

	public float[] getVertexColoring() {
		return this.currentColor;
	}

	public void queueAnimation(final String whichAnimation) {
		final EnumSet<AnimationTokens.PrimaryTag> primaryTags = EnumSet.noneOf(AnimationTokens.PrimaryTag.class);
		final EnumSet<AnimationTokens.SecondaryTag> secondaryTag = EnumSet.noneOf(AnimationTokens.SecondaryTag.class);
		Sequence.populateTags(primaryTags, secondaryTag, whichAnimation);
		final AnimationTokens.PrimaryTag primaryTag = Sequence.any(primaryTags);
		this.unitAnimationListenerImpl.queueAnimation(primaryTag, secondaryTag, true);
	}

	public void playAnimation(final String whichAnimation) {
		final EnumSet<AnimationTokens.PrimaryTag> primaryTags = EnumSet.noneOf(AnimationTokens.PrimaryTag.class);
		final EnumSet<AnimationTokens.SecondaryTag> secondaryTag = EnumSet.noneOf(AnimationTokens.SecondaryTag.class);
		Sequence.populateTags(primaryTags, secondaryTag, whichAnimation);
		final AnimationTokens.PrimaryTag primaryTag = Sequence.any(primaryTags);
		this.unitAnimationListenerImpl.playAnimation(true, primaryTag, secondaryTag, 1.0f, true);
	}

	public void resetAnimation() {
		this.unitAnimationListenerImpl.playAnimation(true, AnimationTokens.PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f,
				true);
	}

	public void playAnimation(final int index) {
		this.unitAnimationListenerImpl.playAnimation(true, index, 1.0f, true);
	}

	public void playAnimationWithRarity(final String whichAnimation, final CRarityControl control) {
		final EnumSet<AnimationTokens.PrimaryTag> primaryTags = EnumSet.noneOf(AnimationTokens.PrimaryTag.class);
		final EnumSet<AnimationTokens.SecondaryTag> secondaryTag = EnumSet.noneOf(AnimationTokens.SecondaryTag.class);
		Sequence.populateTags(primaryTags, secondaryTag, whichAnimation);
		final AnimationTokens.PrimaryTag primaryTag = Sequence.any(primaryTags);
		// TODO: below is not doing probably what it should.... We can assume "control
		// == RARE" means to INTENTIONALLY PLAY THE RARE ONES but at the moment, this
		// flag we are passing means to ALLOW SOMETIMES playing the rare ones
		this.unitAnimationListenerImpl.playAnimation(true, primaryTag, secondaryTag, 1.0f,
				control == CRarityControl.RARE);
	}

	public void addAnimationProperties(final String properties, final boolean add) {
		final EnumSet<AnimationTokens.PrimaryTag> primaryTags = EnumSet.noneOf(AnimationTokens.PrimaryTag.class);
		final EnumSet<AnimationTokens.SecondaryTag> secondaryTags = EnumSet.noneOf(AnimationTokens.SecondaryTag.class);
		Sequence.populateTags(primaryTags, secondaryTags, properties);
		boolean changedAnimProps = false;
		if (add) {
			for (final AnimationTokens.SecondaryTag tag : secondaryTags) {
				if (this.unitAnimationListenerImpl.addSecondaryTag(tag)) {
					changedAnimProps = true;
				}
			}
		} else {
			for (final AnimationTokens.SecondaryTag tag : secondaryTags) {
				if (this.unitAnimationListenerImpl.removeSecondaryTag(tag)) {
					changedAnimProps = true;
				}
			}
		}
		if (changedAnimProps) {
			this.unitAnimationListenerImpl.forceResetCurrentAnimation();
		}
	}

	public void lockTargetFacing(final String boneNameString, final RenderUnit renderPeerTarget, final float offsetX,
			final float offsetY, final float offsetZ) {
		if (boneNameString != null) {
			if ("head".equals(boneNameString.toLowerCase(Locale.ROOT))) {
				this.unitAnimationListenerImpl.lockHeadFacing(renderPeerTarget.instance,
						new Vector3(offsetX, offsetY, offsetZ));
			} else if ("turret".equals(boneNameString.toLowerCase(Locale.ROOT))) {
				this.unitAnimationListenerImpl.lockTurretFacing(renderPeerTarget.instance,
						new Vector3(offsetX, offsetY, offsetZ));
			}
		}
	}

	public void resetLookAt() {
		this.unitAnimationListenerImpl.clearHeadFacing();
		this.unitAnimationListenerImpl.clearTurretFacing();
	}
}
