package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorBoardTransport;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Battle Stations: the nearest workers in the area run into this burrow until it is full.
 */
public class CAbilityBattleStations extends CAbilityNoTargetSpellBase {
	private War3ID allowedUnitType;
	private float area;
	private boolean summonBusyUnits;

	public CAbilityBattleStations(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		final String unitId = worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0);
		this.allowedUnitType = (unitId != null) && (unitId.length() == 4) ? War3ID.fromString(unitId) : null;
		this.area = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.summonBusyUnits = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_B + level, 0) != 0;
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.battlestations;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId == OrderIds.battlestations) {
			doEffect(game, caster, null);
			return false;
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CAbilityCargoHoldBurrow burrow = caster.getFirstAbilityOfType(CAbilityCargoHoldBurrow.class);
		if ((burrow == null) || caster.isConstructingOrUpgrading()) {
			return false;
		}
		simulation.unitSoundEffectEvent(caster, getAlias());
		final List<CUnit> candidates = new ArrayList<>();
		final int[] slots = { burrow.getCargoCapacity() - burrow.getCargoCount() };
		simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.area, (worker) -> {
			if (isRunningTo(worker, caster)) {
				slots[0]--;
			}
			else if (burrow.canBoard(simulation, caster, worker, true) && !isRunningToAnyBurrow(worker)
					&& (this.summonBusyUnits || isIdle(worker))) {
				candidates.add(worker);
			}
			return false;
		});
		candidates.sort(Comparator.comparingDouble((worker) -> worker.distance(caster)));
		for (final CUnit worker : candidates) {
			if (slots[0] <= 0) {
				break;
			}
			final CAbilityMove move = worker.getFirstAbilityOfType(CAbilityMove.class);
			if (move != null) {
				worker.order(simulation, new COrderTargetWidget(worker.getPlayerIndex(), move.getHandleId(),
						OrderIds.smart, caster.getHandleId(), false), false);
				slots[0]--;
			}
		}
		return false;
	}

	public War3ID getAllowedUnitType() {
		return this.allowedUnitType;
	}

	private static boolean isIdle(final CUnit worker) {
		final CBehavior behavior = worker.getCurrentBehavior();
		return (behavior == null) || (behavior == worker.getStopBehavior());
	}

	private static boolean isRunningToAnyBurrow(final CUnit worker) {
		final CBehaviorBoardTransport board = worker.getBoardTransportBehavior();
		return (board != null) && (worker.getCurrentBehavior() == board) && (board.getTarget() instanceof CUnit)
				&& (((CUnit) board.getTarget()).getFirstAbilityOfType(CAbilityCargoHoldBurrow.class) != null);
	}

	private static boolean isRunningTo(final CUnit worker, final CUnit burrow) {
		final CBehaviorBoardTransport board = worker.getBoardTransportBehavior();
		return (board != null) && (worker.getCurrentBehavior() == board) && (board.getTarget() == burrow);
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean isUniversal() {
		return true;
	}
}
