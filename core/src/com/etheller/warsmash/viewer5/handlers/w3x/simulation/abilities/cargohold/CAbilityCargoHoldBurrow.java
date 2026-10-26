package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold;

import java.util.EnumSet;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityRanged;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CAbilityCargoHoldBurrow extends CAbilityCargoHold implements CAbilityRanged {
	private float[] originalBaseAttackRates;

	public CAbilityCargoHoldBurrow(final int handleId, final War3ID code, final War3ID alias, final int cargoCapacity,
			final float duration, final float castRange, final EnumSet<CTargetType> targetsAllowed) {
		super(handleId, code, alias, cargoCapacity, duration, castRange, targetsAllowed);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		// NOTE: this might have weird interactions with heroes, which
		// also setup unit specific attack objects in memory
		final List<CUnitAttack> unitSpecificAttacks = unit.getUnitSpecificAttacks();
		final int originalAttackCount = unitSpecificAttacks.size();
		originalBaseAttackRates = new float[originalAttackCount];
		for (int i = 0; i < originalAttackCount; i++) {
			final CUnitAttack originalAttack = unitSpecificAttacks.get(i);
			originalBaseAttackRates[i] = originalAttack.getCooldownTime();
		}
		unit.setDisableAttacks(true);
		super.onAdd(game, unit);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.setDisableAttacks(false);
		super.onRemove(game, unit);
	}

	@Override
	public void addUnit(final CUnit cargoHoldUnit, final CUnit target) {
		final boolean wasEmpty = isEmpty();
		super.addUnit(cargoHoldUnit, target);
		if (wasEmpty) {
			cargoHoldUnit.setDisableAttacks(false);
		}
		updateAttackCooldowns(cargoHoldUnit);
	}

	@Override
	public CUnit removeUnitAtIndex(final CUnit cargoHoldUnit, final int index) {
		final CUnit removedUnit = super.removeUnitAtIndex(cargoHoldUnit, index);
		if (isEmpty()) {
			cargoHoldUnit.setDisableAttacks(true);
		}
		else {
			updateAttackCooldowns(cargoHoldUnit);
		}
		return removedUnit;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId == OrderIds.smart) {
			final CUnit worker = target == null ? null : target.visit(AbilityTargetVisitor.UNIT);
			if ((worker != null) && canBoard(game, caster, worker, false)) {
				game.unitSoundEffectEvent(caster, getAlias());
				addUnit(caster, worker);
				worker.setHidden(true);
				worker.setPaused(true);
				worker.fireLoadedEvents(game, caster);
			}
			return false;
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (orderId == OrderIds.smart) {
			final CUnit worker = target.visit(AbilityTargetVisitor.UNIT);
			if ((worker != null) && canBoard(game, unit, worker, true)) {
				if (unit.canReach(worker, getCastRange())) {
					receiver.targetOk(target);
				}
				else {
					receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_IS_OUTSIDE_RANGE);
				}
			}
			else {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_LOAD_TARGET);
			}
			return;
		}
		super.checkCanTarget(game, unit, playerIndex, orderId, autoOrder, target, receiver);
	}

	/**
	 * Only workers of the burrow's owner (the Battle Stations unit type, or any worker) can man a finished burrow
	 * that has room.
	 */
	public boolean canBoard(final CSimulation game, final CUnit burrow, final CUnit worker,
			final boolean ignoreRange) {
		if ((worker == burrow) || worker.isDead() || worker.isHidden()
				|| (worker.getPlayerIndex() != burrow.getPlayerIndex()) || burrow.isConstructingOrUpgrading()
				|| !hasCapacity(worker.getUnitType().getCargoCapacity())
				|| !worker.canBeTargetedBy(game, burrow, getTargetsAllowed())) {
			return false;
		}
		final CAbilityBattleStations battleStations = burrow.getFirstAbilityOfType(CAbilityBattleStations.class);
		if ((battleStations != null) && (battleStations.getAllowedUnitType() != null)) {
			if (!battleStations.getAllowedUnitType().equals(worker.getTypeId())) {
				return false;
			}
		}
		else if (!worker.isUnitType(CUnitTypeJass.PEON)) {
			return false;
		}
		return ignoreRange || burrow.canReach(worker, getCastRange());
	}

	public static CAbilityCargoHoldBurrow getBurrowLoad(final CSimulation game, final CUnit worker,
			final CUnit burrow, final boolean ignoreRange) {
		for (final CAbility ability : burrow.getAbilities()) {
			if ((ability instanceof CAbilityCargoHoldBurrow) && !ability.isDisabled()
					&& ((CAbilityCargoHoldBurrow) ability).canBoard(game, burrow, worker, ignoreRange)) {
				return (CAbilityCargoHoldBurrow) ability;
			}
		}
		return null;
	}

	private void updateAttackCooldowns(final CUnit cargoHoldUnit) {
		final List<CUnitAttack> unitSpecificAttacks = cargoHoldUnit.getUnitSpecificAttacks();
		for (int i = 0, l = unitSpecificAttacks.size(); i < l; i++) {
			unitSpecificAttacks.get(i)
					.setCooldownTime(originalBaseAttackRates[i] / (float) Math.pow(2, getCargoCount()));
		}

	}
}
