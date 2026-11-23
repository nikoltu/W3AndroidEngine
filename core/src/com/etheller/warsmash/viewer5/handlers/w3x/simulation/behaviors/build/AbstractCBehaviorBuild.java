package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build;

import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityBuildInProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMinable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityOverlayedMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * A worker walking to a site and starting a building there. The races differ in what the worker does once the
 * building is started: a Peasant steps aside and builds it, a Peon goes inside, an Acolyte summons it and leaves.
 */
public abstract class AbstractCBehaviorBuild extends CAbstractRangedBehavior {
	private int highlightOrderId;
	private War3ID orderId;
	private boolean charged = false;
	private final BuildOnBuildingIntersector buildOnBuildingIntersector;

	public AbstractCBehaviorBuild(final CUnit unit) {
		super(unit);
		this.buildOnBuildingIntersector = new BuildOnBuildingIntersector();
	}

	public CBehavior reset(final CSimulation game, final AbilityPointTarget target, final int orderId,
			final int highlightOrderId) {
		// Measured on 1.29: the cost is paid when the build order is given and paid back when another order (also
		// another build order) replaces it before the building is started.
		refundUnbuilt(game);
		this.highlightOrderId = highlightOrderId;
		this.orderId = new War3ID(orderId);
		this.charged = true;
		resetStructure();
		return innerReset(game, target);
	}

	/** Forgets the building of the previous order. */
	protected abstract void resetStructure();

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		final CUnitType unitType = simulation.getUnitData().getUnitType(this.orderId);
		final ArgbImage buildingPathingPixelMap = unitType.getBuildingPathingPixelMap();
		if (buildingPathingPixelMap == null) {
			return this.unit.canReach(this.target.getX(), this.target.getY(), getRangeWithoutPathing(unitType));
		}
		if (unitType.isCanBeBuiltOnThem()) {
			return this.unit.canReachToPathingTouching(simulation.getGameplayConstants().getBuildingAngle(),
					buildingPathingPixelMap, this.target.getX(), this.target.getY());
		}
		return this.unit.canReachToPathing(0, simulation.getGameplayConstants().getBuildingAngle(),
				buildingPathingPixelMap, this.target.getX(), this.target.getY());
	}

	protected float getRangeWithoutPathing(final CUnitType unitType) {
		return 0;
	}

	@Override
	public int getHighlightOrderId() {
		return this.highlightOrderId;
	}

	/**
	 * Starts the building at the target. When the site is taken the cost is paid back, the player is told and null is
	 * returned.
	 */
	protected CUnit startStructure(final CSimulation simulation) {
		this.charged = false;
		final CUnitType unitTypeToCreate = simulation.getUnitData().getUnitType(this.orderId);
		final ArgbImage buildingPathingPixelMap = unitTypeToCreate.getBuildingPathingPixelMap();
		final boolean canBeBuiltOnThem = unitTypeToCreate.isCanBeBuiltOnThem();
		final boolean buildLocationObstructed = AbstractCAbilityBuild.isBuildLocationObstructed(simulation,
				unitTypeToCreate, buildingPathingPixelMap, canBeBuiltOnThem, this.target.getX(), this.target.getY(),
				this.unit, this.buildOnBuildingIntersector);
		final int playerIndex = this.unit.getPlayerIndex();
		if (buildLocationObstructed) {
			refund(simulation.getPlayer(playerIndex), unitTypeToCreate);
			simulation.getCommandErrorListener().showInterfaceError(playerIndex,
					CommandStringErrorKeys.UNABLE_TO_BUILD_THERE);
			return null;
		}
		final CUnit constructedStructure = simulation.createUnit(this.orderId, playerIndex, this.target.getX(),
				this.target.getY(), simulation.getGameplayConstants().getBuildingAngle(), true);
		AbstractCAbilityBuild.moveControlledUnitsAside(simulation, constructedStructure, this.unit);
		if (canBeBuiltOnThem) {
			overlayMine(constructedStructure);
		}
		assignWorker(constructedStructure);
		final CAbilityBuildInProgress abilityBuildInProgress = new CAbilityBuildInProgress(
				simulation.getHandleIdAllocator().createId());
		constructedStructure.setFoodUsed(unitTypeToCreate.getFoodUsed());
		constructedStructure.add(simulation, abilityBuildInProgress);
		for (final CAbility ability : constructedStructure.getAbilities()) {
			ability.visit(AbilityDisableWhileUnderConstructionVisitor.INSTANCE);
		}
		this.unit.checkDisabledAbilities(simulation, true);
		onStructureStarted(simulation, constructedStructure, unitTypeToCreate, abilityBuildInProgress);
		simulation.getPlayer(playerIndex).addTechtreeInProgress(simulation, this.orderId);
		simulation.unitConstructedEvent(this.unit, constructedStructure);
		// Measured on 1.29: the start event sees the full life; the starting life is applied after it, over any
		// life the event's triggers set.
		constructedStructure.setLife(simulation,
				constructedStructure.getMaximumLife() * WarsmashConstants.BUILDING_CONSTRUCT_START_LIFE);
		return constructedStructure;
	}

	private void overlayMine(final CUnit constructedStructure) {
		final CUnit unitToBuildOn = this.buildOnBuildingIntersector.getUnitToBuildOn();
		CAbilityGoldMinable abilityGoldMine = null;
		if (unitToBuildOn != null) {
			for (final CAbility ability : unitToBuildOn.getAbilities()) {
				if ((ability instanceof CAbilityGoldMinable) && !ability.isDisabled()
						&& ((CAbilityGoldMinable) ability).isBaseMine()) {
					abilityGoldMine = (CAbilityGoldMinable) ability;
				}
			}
		}
		if (abilityGoldMine != null) {
			for (final CAbility ability : constructedStructure.getAbilities()) {
				if (ability instanceof final CAbilityOverlayedMine blightedGoldMine) {
					blightedGoldMine.setParentMine(unitToBuildOn, abilityGoldMine);
					unitToBuildOn.setHidden(true);
					unitToBuildOn.setPaused(true);
				}
			}
		}
	}

	/** Who builds the new building: the worker, or (paused) whoever repairs it. */
	protected abstract void assignWorker(CUnit constructedStructure);

	/** What the worker does once the building is started, before the start is announced. */
	protected abstract void onStructureStarted(CSimulation simulation, CUnit constructedStructure,
			CUnitType unitTypeToCreate, CAbilityBuildInProgress abilityBuildInProgress);

	/** Moves the worker from the site to its edge on the side the worker came from. */
	protected void stepAside(final CSimulation simulation, final CUnitType unitTypeToCreate) {
		final float deltaX = this.unit.getX() - this.target.getX();
		final float deltaY = this.unit.getY() - this.target.getY();
		final float delta = (float) Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
		this.unit.setPointAndCheckUnstuck(
				this.target.getX() + ((deltaX / delta) * unitTypeToCreate.getCollisionSize()),
				this.target.getY() + ((deltaY / delta) * unitTypeToCreate.getCollisionSize()), simulation);
		simulation.unitRepositioned(this.unit);
	}

	@Override
	protected boolean checkTargetStillValid(final CSimulation simulation) {
		return true;
	}

	@Override
	protected CBehavior updateOnInvalidTarget(final CSimulation simulation) {
		return this.unit.pollNextOrderBehavior(simulation);
	}

	@Override
	protected void resetBeforeMoving(final CSimulation simulation) {

	}

	@Override
	public void begin(final CSimulation game) {

	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
		if (interrupted) {
			refundUnbuilt(game);
		}
	}

	private void refundUnbuilt(final CSimulation game) {
		if (this.charged) {
			this.charged = false;
			refund(game.getPlayer(this.unit.getPlayerIndex()), game.getUnitData().getUnitType(this.orderId));
		}
	}

	private void refund(final CPlayer player, final CUnitType unitTypeToCreate) {
		player.setFoodUsed(player.getFoodUsed() - unitTypeToCreate.getFoodUsed());
		player.refundFor(unitTypeToCreate);
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
		if (interrupted) {
			refundUnbuilt(game);
		}
	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.BUILD;
	}
}
