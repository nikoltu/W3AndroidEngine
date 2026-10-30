package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build;

import java.util.*;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitTypeRequirement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.menu.CAbilityMenu;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CBuildingPathingType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public abstract class AbstractCAbilityBuild extends AbstractCAbility implements CAbilityMenu {
	private static boolean REFUND_ON_ORDER_CANCEL = false;
	private final Set<War3ID> structuresBuilt;

	public AbstractCAbilityBuild(final int handleId, final War3ID code, final List<War3ID> structuresBuilt) {
		super(handleId, code);
		this.structuresBuilt = new LinkedHashSet<>(structuresBuilt);
	}

	public Collection<War3ID> getStructuresBuilt() {
		return this.structuresBuilt;
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		final War3ID orderIdAsRawtype = new War3ID(orderId);
		if (this.structuresBuilt.contains(orderIdAsRawtype)) {
			final CUnitType unitType = game.getUnitData().getUnitType(orderIdAsRawtype);
			if (unitType != null) {
				final CPlayer player = game.getPlayer(unit.getPlayerIndex());
				final List<CUnitTypeRequirement> requirements = unitType.getRequirements();
				final boolean techtreeAllowedByMax = player.isTechtreeAllowedByMax(orderIdAsRawtype);
				boolean requirementsMet = techtreeAllowedByMax;
				for (final CUnitTypeRequirement requirement : requirements) {
					if (player.getTechtreeUnlocked(requirement.getRequirement()) < requirement.getRequiredLevel()) {
						requirementsMet = false;
					}
				}
				if (requirementsMet) {
					if ((player.getGold() >= unitType.getGoldCost())) {
						if ((player.getLumber() >= unitType.getLumberCost())) {
							if ((unitType.getFoodUsed() == 0)
									|| ((player.getFoodUsed() + unitType.getFoodUsed()) <= player.getFoodCap())) {

								receiver.useOk();
							}
							else {
								receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_FOOD);
							}
						}
						else {
							receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_LUMBER);
						}
					}
					else {
						receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_GOLD);
					}
				}
				else {
					if (techtreeAllowedByMax) {
						for (final CUnitTypeRequirement requirement : requirements) {
							receiver.missingRequirement(requirement.getRequirement(), requirement.getRequiredLevel());
						}
					}
					else {
						receiver.techtreeMaximumReached();
					}
				}
			}
			else {
				receiver.useOk();
			}
		}
		else {
			/// ???
			receiver.useOk();
		}
	}

	@Override
	public final void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public final void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		final War3ID orderIdAsWar3ID = new War3ID(orderId);
		if (this.structuresBuilt.contains(orderIdAsWar3ID)) {
			final CUnitType unitTypeToCreate = game.getUnitData().getUnitType(orderIdAsWar3ID);
			final ArgbImage buildingPathingPixelMap = unitTypeToCreate.getBuildingPathingPixelMap();
			final boolean canBeBuiltOnThem = unitTypeToCreate.isCanBeBuiltOnThem();
			roundTargetPoint(target, unitTypeToCreate);
			final float x = target.getX();
			final float y = target.getY();
			final boolean buildLocationObstructed = AbstractCAbilityBuild.isBuildLocationObstructed(game,
					unitTypeToCreate, buildingPathingPixelMap, canBeBuiltOnThem, x, y, unit,
					BuildOnBuildingIntersector.INSTANCE.reset(x, y));
			if (buildLocationObstructed) {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_BUILD_THERE);
			}
			else {
				receiver.targetOk(target);
			}
		}
		else {
			receiver.orderIdNotAccepted();
		}
	}

	@Override
	public final void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		return true;
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
		if (REFUND_ON_ORDER_CANCEL) {
			final CPlayer player = game.getPlayer(unit.getPlayerIndex());
			final War3ID orderIdAsRawtype = new War3ID(orderId);
			final CUnitType unitType = game.getUnitData().getUnitType(orderIdAsRawtype);
			player.refundFor(unitType);
			if (unitType.getFoodUsed() != 0) {
				player.setFoodUsed(player.getFoodUsed() - unitType.getFoodUsed());
			}
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}

	public static boolean isBuildLocationObstructed(final CSimulation simulation, final CUnitType unitTypeToCreate,
			final ArgbImage buildingPathingPixelMap, final boolean canBeBuiltOnThem, final float targetX,
			final float targetY, final CUnit worker, final BuildOnBuildingIntersector buildOnBuildingIntersector) {
		return isBuildLocationObstructed(simulation, unitTypeToCreate, buildingPathingPixelMap, canBeBuiltOnThem,
				targetX, targetY, worker, buildOnBuildingIntersector, false);
	}

	public static boolean isBuildLocationObstructed(final CSimulation simulation, final CUnitType unitTypeToCreate,
			final ArgbImage buildingPathingPixelMap, final boolean canBeBuiltOnThem, final float targetX,
			final float targetY, final CUnit worker, final BuildOnBuildingIntersector buildOnBuildingIntersector,
			final boolean ignoreBlight) {
		boolean buildLocationObstructed = false;
		if (canBeBuiltOnThem) {
			simulation.getWorldCollision().enumBuildingsAtPoint(targetX, targetY,
					buildOnBuildingIntersector.reset(targetX, targetY));
			buildLocationObstructed = (buildOnBuildingIntersector.getUnitToBuildOn() == null);
		}
		else if (buildingPathingPixelMap != null) {
			final EnumSet<CBuildingPathingType> preventedPathingTypes = unitTypeToCreate.getPreventedPathingTypes();
			EnumSet<CBuildingPathingType> requiredPathingTypes = unitTypeToCreate.getRequiredPathingTypes();
			if (ignoreBlight && requiredPathingTypes.contains(CBuildingPathingType.BLIGHTED)) {
				requiredPathingTypes = EnumSet.copyOf(requiredPathingTypes);
				requiredPathingTypes.remove(CBuildingPathingType.BLIGHTED);
			}

			if (!simulation.getPathingGrid().checkPathingTexture(targetX, targetY,
					(int) simulation.getGameplayConstants().getBuildingAngle(), buildingPathingPixelMap,
					preventedPathingTypes, requiredPathingTypes, simulation.getWorldCollision(), worker, simulation,
					worker == null ? -1 : worker.getPlayerIndex())) {
				buildLocationObstructed = true;
			}
		}
		return buildLocationObstructed;
	}

	private static final Rectangle footprintRect = new Rectangle();
	private static final List<CUnit> unitsToMoveAside = new ArrayList<>();

	public static void moveControlledUnitsAside(final CSimulation simulation, final CUnit structure,
			final CUnit worker) {
		final ArgbImage pathingMap = structure.getUnitType().getBuildingPathingPixelMap();
		if (pathingMap == null) {
			return;
		}
		final float width = pathingMap.getWidth() * 32f;
		final float height = pathingMap.getHeight() * 32f;
		final float offsetX = ((pathingMap.getWidth() % 2) == 1) ? 16f : 0f;
		final float offsetY = ((pathingMap.getHeight() % 2) == 1) ? 16f : 0f;
		footprintRect.set((structure.getX() - (width / 2)) + offsetX, (structure.getY() - (height / 2)) + offsetY,
				width, height);
		final int controllerIndex = worker.getPlayerIndex();
		unitsToMoveAside.clear();
		simulation.getWorldCollision().enumUnitsInRect(footprintRect, (unit) -> {
			if ((unit != structure) && (unit != worker) && !unit.isBuilding()
					&& (unit.getMovementType() != PathingGrid.MovementType.FLY)
					&& ((unit.getPlayerIndex() == controllerIndex) || simulation.getPlayer(unit.getPlayerIndex())
							.hasAlliance(controllerIndex, CAllianceType.SHARED_CONTROL))) {
				unitsToMoveAside.add(unit);
			}
			return false;
		});
		for (int i = 0; i < unitsToMoveAside.size(); i++) {
			final CUnit unit = unitsToMoveAside.get(i);
			unit.setPointAndCheckUnstuck(unit.getX(), unit.getY(), simulation);
		}
		unitsToMoveAside.clear();
	}

	public static void roundTargetPoint(final Vector2 point, final CUnitType unitType) {
		final ArgbImage buildingPathingPixelMap = unitType.getBuildingPathingPixelMap();
		if (buildingPathingPixelMap != null) {
			point.x = roundToBuildGrid(point.x, buildingPathingPixelMap.getWidth());
			point.y = roundToBuildGrid(point.y, buildingPathingPixelMap.getHeight());
		}
	}

	// Measured: the original game cuts the point towards zero to the 64 grid; a footprint with an odd number of
	// 64 steps sits 32 further away from zero.
	public static float roundToBuildGrid(final float value, final int pathingCells) {
		final float cut = (int) (value / 64f) * 64f;
		if (((pathingCells / 2) % 2) == 1) {
			return value < 0 ? cut - 32f : cut + 32f;
		}
		return cut;
	}
}
