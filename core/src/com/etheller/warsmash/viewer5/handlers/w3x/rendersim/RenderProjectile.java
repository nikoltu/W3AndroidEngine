package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import java.util.List;

import com.badlogic.gdx.math.Quaternion;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.Sequence;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.IndexedSequence;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.Extrapolator;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PresentationClock;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldPeerData;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldObjectFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetWidgetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;

public class RenderProjectile implements RenderEffect {
	private static final Quaternion pitchHeap = new Quaternion();
	/** Steps after its launch a missile the frames never listed is taken as over. */
	private static final int UNLISTED_STEPS = 2;

	/** Null for a missile shown from the frames only. */
	private final CProjectile simulationProjectile;
	private final float targetX;
	private final float targetY;
	/** The step the missile was launched in. */
	private final int startTick;
	private final MdxComplexInstance modelInstance;
	private float x;
	private float y;
	private float z;
	private final float startingHeight;
	private final float arcPeakHeight;
	private float totalTravelDistance;

	private float targetHeight;

	private float yaw;

	private float pitch;
	private boolean done = false;
	private float deathTime;
	private float deathTimeElapsed;
	// With a presentation clock the missile is shown from the frames of the game, by the number the frames give it.
	private int frameId;
	private boolean hadPose;
	private final Extrapolator.ObjectPose pose = new Extrapolator.ObjectPose();

	/** A missile that follows the live simulation (no simulation thread). */
	public RenderProjectile(final CProjectile simulationProjectile, final MdxComplexInstance modelInstance,
			final float z, final float arc, final War3MapViewer war3MapViewer) {
		this(simulationProjectile, modelInstance, z, arc, war3MapViewer, simulationProjectile.getX(),
				simulationProjectile.getY(), simulationProjectile.getTargetX(), simulationProjectile.getTargetY(),
				targetFlyHeight(simulationProjectile), targetImpactZ(simulationProjectile), 0, 0);
	}

	/**
	 * A missile shown from the frames of the game: it works from what the missile was when launched, so it appears
	 * and plays its impact even when the simulation is long done with it.
	 */
	public RenderProjectile(final WorldPeerData.ProjectileLaunch launch, final MdxComplexInstance modelInstance,
			final float z, final float arc, final War3MapViewer war3MapViewer) {
		this(null, modelInstance, z, arc, war3MapViewer, launch.x, launch.y, launch.targetX, launch.targetY,
				launch.targetFlyHeight, launch.targetImpactZ, launch.projectileId, launch.startTick);
	}

	private RenderProjectile(final CProjectile simulationProjectile, final MdxComplexInstance modelInstance,
			final float z, final float arc, final War3MapViewer war3MapViewer, final float x, final float y,
			final float targetX, final float targetY, final float targetFlyHeight, final float targetImpactZ,
			final int frameId, final int startTick) {
		this.simulationProjectile = simulationProjectile;
		this.modelInstance = modelInstance;
		modelInstance.hiddenInFog = true;
		this.x = x;
		this.y = y;
		this.z = z;
		this.startingHeight = z;
		this.targetX = targetX;
		this.targetY = targetY;
		this.frameId = frameId;
		this.startTick = startTick;
		final float dxToTarget = targetX - this.x;
		final float dyToTarget = targetY - this.y;
		final float d2DToTarget = (float) StrictMath.sqrt((dxToTarget * dxToTarget) + (dyToTarget * dyToTarget));
		final float startingDistance = d2DToTarget + this.totalTravelDistance;
		this.targetHeight = (war3MapViewer.terrain.getGroundHeight(targetX, targetY) + targetFlyHeight + targetImpactZ);
		this.arcPeakHeight = arc * startingDistance;
		this.yaw = (float) StrictMath.atan2(dyToTarget, dxToTarget);
		this.deathTime = war3MapViewer.simulation.getGameplayConstants().getBulletDeathTime();
	}

	private static float targetFlyHeight(final CProjectile projectile) {
		final CWidget widget = projectile.getTarget().visit(AbilityTargetWidgetVisitor.INSTANCE);
		return widget == null ? 0 : widget.getFlyHeight();
	}

	private static float targetImpactZ(final CProjectile projectile) {
		final CWidget widget = projectile.getTarget().visit(AbilityTargetWidgetVisitor.INSTANCE);
		return widget == null ? 0 : widget.getImpactZ();
	}

	/** The number the frames of the game give this missile; without it the missile is shown from the live one. */
	public void setFrameId(final int frameId) {
		this.frameId = frameId;
	}

	public void setDeathTime(float time) {
		this.deathTime = time;
	}

	public void setImpactZ(final War3MapViewer war3MapViewer, float impactZ) {
		this.targetHeight = (war3MapViewer.terrain.getGroundHeight(this.targetX, this.targetY) + impactZ);
	}

	@Override
	public boolean updateAnimations(final War3MapViewer war3MapViewer, final float deltaTime) {
		final PresentationClock clock = war3MapViewer.getPresentation();
		final boolean fromFrame = (this.simulationProjectile == null) || ((clock != null) && (this.frameId != 0));
		final boolean wasDone = this.done;
		boolean havePose = false;
		if (fromFrame) {
			havePose = (clock != null) && Extrapolator.sampleObject(clock, Extrapolator.PROJECTILES, this.frameId, 0, this.pose);
			this.hadPose |= havePose;
			// a missile the frames no longer list has landed
			this.done = havePose ? (this.pose.state.flags[this.pose.row] & WorldObjectFrameState.DEAD) != 0
					: (this.hadPose || this.done);
			if (!this.done && !this.hadPose && (clock != null)) {
				// one that was over within a step no frame lists: it lands where it was headed
				final SimFrame shown = clock.frameBefore();
				if ((shown != null) && (shown.tick > (this.startTick + UNLISTED_STEPS))) {
					this.done = true;
					this.x = this.targetX;
					this.y = this.targetY;
					this.z = this.targetHeight;
				}
			}
		}
		else {
			this.done = this.simulationProjectile.isDone();
		}
		if (this.done) {
			final MdxModel model = (MdxModel) this.modelInstance.model;
			final List<Sequence> sequences = model.getSequences();
			final IndexedSequence sequence = SequenceUtils.selectSequence(PrimaryTag.DEATH, SequenceUtils.EMPTY,
					sequences, true);
			if ((sequence != null) && this.done && !wasDone) {
				this.modelInstance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
				this.modelInstance.setSequence(sequence.index);
			}
		}
		else {
			if (this.modelInstance.sequenceEnded || (this.modelInstance.sequence == -1)) {
				SequenceUtils.randomStandSequence(this.modelInstance);
			}
		}
		final float simX = fromFrame ? (havePose ? this.pose.x : this.x) : this.simulationProjectile.getX();
		final float simY = fromFrame ? (havePose ? this.pose.y : this.y) : this.simulationProjectile.getY();
		final float simDx = simX - this.x;
		final float simDy = simY - this.y;
		final float simD = (float) StrictMath.sqrt((simDx * simDx) + (simDy * simDy));
		final float speed = fromFrame ? simD
				: StrictMath.min(simD, this.simulationProjectile.getSpeed() * deltaTime);
		if (simD > 0) {
			if (fromFrame) {
				this.x = simX;
				this.y = simY;
			}
			else {
				this.x = this.x + ((speed * simDx) / simD);
				this.y = this.y + ((speed * simDy) / simD);
			}
			final float targetX = fromFrame ? this.pose.targetX : this.simulationProjectile.getTargetX();
			final float targetY = fromFrame ? this.pose.targetY : this.simulationProjectile.getTargetY();
			final float dxToTarget = targetX - this.x;
			final float dyToTarget = targetY - this.y;
			final float d2DToTarget = (float) StrictMath.sqrt((dxToTarget * dxToTarget) + (dyToTarget * dyToTarget));
			final float startingDistance = d2DToTarget + this.totalTravelDistance;
			final float halfStartingDistance = startingDistance / 2f;

			final float dtsz = this.targetHeight - this.startingHeight;
			final float d1z = dtsz / (halfStartingDistance * 2);
			this.totalTravelDistance += speed;
			final float dz = d1z * this.totalTravelDistance;

			final float distanceToPeak = this.totalTravelDistance - halfStartingDistance;
			final float normPeakDist = distanceToPeak / halfStartingDistance;
			final float currentHeightPercentage = 1 - (normPeakDist * normPeakDist);
			final float arcCurrentHeight = currentHeightPercentage * this.arcPeakHeight;
			this.z = this.startingHeight + dz + arcCurrentHeight;

			if (!this.done) {
				this.yaw = (float) StrictMath.atan2(dyToTarget, dxToTarget);

				final float slope = (-2 * (normPeakDist) * this.arcPeakHeight) / halfStartingDistance;
				this.pitch = (float) StrictMath.atan2(slope + d1z, 1);
			}
		}
		if (this.done) {
			this.pitch = 0;
			this.deathTimeElapsed += deltaTime;
		}

		this.modelInstance.setLocation(this.x, this.y, this.z);
		this.modelInstance.localRotation.setFromAxisRad(0, 0, 1, this.yaw);
		this.modelInstance.rotate(pitchHeap.setFromAxisRad(0, -1, 0, this.pitch));
		war3MapViewer.worldScene.instanceMoved(this.modelInstance, this.x, this.y);

		final boolean everythingDone = this.done
				&& (this.modelInstance.sequenceEnded
				|| (this.deathTimeElapsed >= this.deathTime));
		if (everythingDone && !war3MapViewer.worldScene.leaveParticles(this.modelInstance)) {
			war3MapViewer.worldScene.removeInstance(this.modelInstance);
		}
		return everythingDone;
	}
}
