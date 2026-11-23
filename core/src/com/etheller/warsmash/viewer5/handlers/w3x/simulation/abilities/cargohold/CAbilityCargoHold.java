package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericNoIconAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CAbilityCargoHold extends AbstractGenericNoIconAbility {
	private int cargoCapacity;
	private final List<CUnit> cargoUnits;
	private int cargoVersion;
	private float duration;
	private final float castRange;
	private float area;
	private EnumSet<CTargetType> targetsAllowed;

	public CAbilityCargoHold(final int handleId, final War3ID code, final War3ID alias, final int cargoCapacity,
			final float duration, final float castRange, final EnumSet<CTargetType> targetsAllowed) {
		this(handleId, code, alias, cargoCapacity, duration, castRange, 0, targetsAllowed);
	}

	public CAbilityCargoHold(final int handleId, final War3ID code, final War3ID alias, final int cargoCapacity,
			final float duration, final float castRange, final float area,
			final EnumSet<CTargetType> targetsAllowed) {
		super(handleId, code, alias);
		this.area = area;
		this.cargoCapacity = cargoCapacity;
		this.cargoUnits = new ArrayList<>();
		this.duration = duration;
		this.castRange = castRange;
		this.targetsAllowed = targetsAllowed;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {

	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unloadAllInstant(game, unit);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId == OrderIds.unload) {
			final int targetIndex = this.cargoUnits.indexOf(target);
			if (targetIndex != -1) {
				// Measured on 1.29: the unit comes off on the next game step, and the transport's order goes on.
				final CUnit passenger = this.cargoUnits.get(targetIndex);
				final CTimer dropTimer = new CTimer() {
					@Override
					public void onFire(final CSimulation simulation) {
						final int index = CAbilityCargoHold.this.cargoUnits.indexOf(passenger);
						if ((index == -1) || caster.isDead()) {
							return;
						}
						final float[] spot = findDropSpot(simulation, caster, passenger, caster.getX(), caster.getY());
						if (spot != null) {
							dropUnitByIndex(simulation, caster, index, spot[0], spot[1]);
						}
					}
				};
				dropTimer.setTimeoutTime(WarsmashConstants.SIMULATION_STEP_TIME);
				dropTimer.start(game);
				return false;
			}
		}
		return true;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return caster.pollNextOrderBehavior(game);
	}

	public void dropUnitByIndex(final CSimulation game, final CUnit caster, final int targetIndex) {
		dropUnitByIndex(game, caster, targetIndex, caster.getX(), caster.getY());
	}

	public CUnit dropUnitByIndex(final CSimulation game, final CUnit caster, final int targetIndex, final float x,
			final float y) {
		game.unitSoundEffectEvent(caster, getAlias());
		final CUnit firstUnit = removeUnitAtIndex(caster, targetIndex);
		firstUnit.setPointAndCheckUnstuck(x, y, game);
		firstUnit.setHidden(false);
		firstUnit.setPaused(false);
		return firstUnit;
	}

	/**
	 * Where the unit comes off: ground it can stand on within the cargo hold area of the transport, nearest to
	 * (towardX, towardY). Measured on 1.29 with a transport ship: unloading one unit puts it on the shore nearest
	 * to the ship, and a ship with no shore that near refuses the order. A transport standing on such ground drops
	 * the unit where it stands.
	 */
	public float[] findDropSpot(final CSimulation game, final CUnit transport, final CUnit passenger,
			final float towardX, final float towardY) {
		final PathingGrid grid = game.getPathingGrid();
		MovementType movementType = passenger.getMovementType();
		if (movementType == null) {
			movementType = MovementType.FOOT;
		}
		final float collisionSize = passenger.getUnitType().getCollisionSize();
		final float transportX = transport.getX();
		final float transportY = transport.getY();
		if (grid.isPathable(transportX, transportY, movementType, collisionSize)) {
			return new float[] { transportX, transportY };
		}
		final float radius = getDropRadius();
		final int minX = Math.max(0, grid.getCellX(transportX - radius));
		final int maxX = Math.min(grid.getWidth() - 1, grid.getCellX(transportX + radius));
		final int minY = Math.max(0, grid.getCellY(transportY - radius));
		final int maxY = Math.min(grid.getHeight() - 1, grid.getCellY(transportY + radius));
		float[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int cellY = minY; cellY <= maxY; cellY++) {
			for (int cellX = minX; cellX <= maxX; cellX++) {
				final float x = grid.getWorldX(cellX);
				final float y = grid.getWorldY(cellY);
				if (StrictMath.hypot(x - transportX, y - transportY) > radius) {
					continue;
				}
				final double distance = StrictMath.hypot(x - towardX, y - towardY);
				if ((distance < bestDistance) && grid.isPathable(x, y, movementType, collisionSize)) {
					bestDistance = distance;
					best = new float[] { x, y };
				}
			}
		}
		return best;
	}

	public float getDropRadius() {
		return this.area > 0 ? this.area : this.castRange;
	}

	public float getArea() {
		return this.area;
	}

	public void setArea(final float area) {
		this.area = area;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		return null;
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (orderId == OrderIds.unload) {
			if (this.cargoUnits.contains(target)) {
				if (findDropSpot(game, unit, (CUnit) target, unit.getX(), unit.getY()) != null) {
					receiver.targetOk(target);
				}
				else {
					receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THERE);
				}
			}
			else {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_DROP_THIS_ITEM);
			}
		}
		else {
			receiver.orderIdNotAccepted();
		}
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.useOk();
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit unit) {
		unloadAllInstant(game, unit);
	}

	public int getCargoCapacity() {
		return this.cargoCapacity;
	}

	public float getDuration() {
		return this.duration;
	}

	public float getCastRange() {
		return this.castRange;
	}

	public EnumSet<CTargetType> getTargetsAllowed() {
		return this.targetsAllowed;
	}

	public CUnit getUnit(final int index) {
		return this.cargoUnits.get(index);
	}

	public void addUnit(final CUnit cargoHoldUnit, final CUnit target) {
		this.cargoUnits.add(target);
		this.cargoVersion++;
		target.setLoadedIn(cargoHoldUnit);
	}

	public CUnit removeUnitAtIndex(final CUnit cargoHoldUnit, final int index) {
		final CUnit removed = this.cargoUnits.remove(index);
		this.cargoVersion++;
		removed.setLoadedIn(null);
		return removed;
	}

	public boolean contains(final CUnit unit) {
		return this.cargoUnits.contains(unit);
	}

	public boolean isEmpty() {
		return this.cargoUnits.isEmpty();
	}

	public int getCargoVersion() {
		return this.cargoVersion;
	}

	public int getCargoCount() {
		return this.cargoUnits.size();
	}

	public void setCargoCapacity(final int cargoCapacity) {
		this.cargoCapacity = cargoCapacity;
	}

	public void setDuration(final float duration) {
		this.duration = duration;
	}

	public void setTargetsAllowed(final EnumSet<CTargetType> targetsAllowed) {
		this.targetsAllowed = targetsAllowed;
	}

	public boolean hasCapacity(final int cargoCapacityOfNewUnit) {
		if (cargoCapacityOfNewUnit == 0) {
			return false;
		}
		return (this.cargoUnits.size() + cargoCapacityOfNewUnit) <= this.cargoCapacity;
	}

	public void unloadAllInstant(final CSimulation game, final CUnit caster) {
		if (!isEmpty()) {
			game.unitSoundEffectEvent(caster, getAlias());
			final int cargoCount = getCargoCount();
			for (int i = 0; i < cargoCount; i++) {
				final CUnit droppedUnit = removeUnitAtIndex(caster, cargoCount - i - 1);
				droppedUnit.setPointAndCheckUnstuck(caster.getX(), caster.getY(), game);
				droppedUnit.setHidden(false);
				droppedUnit.setPaused(false);
			}
		}
	}
}
