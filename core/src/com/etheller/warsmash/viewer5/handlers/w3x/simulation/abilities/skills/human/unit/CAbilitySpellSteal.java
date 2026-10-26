package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.unit;

import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.AutocastType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Spell Steal (Spell Breaker): takes the newest positive buff from an enemy and gives it to the nearest friendly
 * unit around the target, or the newest negative buff from a friendly unit to the nearest enemy around it. Only
 * dispellable timed buffs move, and they keep their remaining time.
 */
public class CAbilitySpellSteal extends CAbilityTargetSpellBase implements CAutocastAbility {
	private float areaOfEffect;
	private boolean autoCastOn;

	public CAbilitySpellSteal(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.spellsteal;
	}

	@Override
	public int getAutoCastOnOrderId() {
		return OrderIds.spellstealon;
	}

	@Override
	public int getAutoCastOffOrderId() {
		return OrderIds.spellstealoff;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	private static boolean isFriendly(final CSimulation game, final CUnit caster, final CUnit unit) {
		return unit.isUnitAlly(game.getPlayer(caster.getPlayerIndex()));
	}

	private static CBuff stealableBuff(final CSimulation game, final CUnit caster, final CUnit target) {
		final boolean wantPositive = !isFriendly(game, caster, target);
		final List<CAbility> abilities = target.getAbilities();
		for (int i = abilities.size() - 1; i >= 0; i--) {
			final CAbility ability = abilities.get(i);
			if (ability instanceof CBuff) {
				final CBuff buff = (CBuff) ability;
				if ((buff.isPositive() == wantPositive) && buff.isDispellable() && !buff.isAura()
						&& !buff.isTimedLifeBar() && (buff.getDurationMax() > 0)
						&& (buff.getDurationRemaining(game, target) > 0)) {
					return buff;
				}
			}
		}
		return null;
	}

	private CUnit recipient(final CSimulation game, final CUnit caster, final CUnit target) {
		final boolean wantFriendly = !isFriendly(game, caster, target);
		final CUnit[] best = { null };
		final double[] bestDistance = { Double.MAX_VALUE };
		game.getWorldCollision().enumUnitsInRange(target.getX(), target.getY(), this.areaOfEffect, (enumUnit) -> {
			if ((enumUnit != target) && !enumUnit.isDead() && !enumUnit.isBuilding()
					&& (isFriendly(game, caster, enumUnit) == wantFriendly)
					&& enumUnit.canBeTargetedBy(game, caster, getTargetsAllowed())) {
				final double distance = enumUnit.distance(target);
				if (distance < bestDistance[0]) {
					bestDistance[0] = distance;
					best[0] = enumUnit;
				}
			}
			return false;
		});
		return best[0];
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target instanceof CUnit ? (CUnit) target : null;
		if ((targetUnit == null) || targetUnit.isBuilding() || (stealableBuff(game, unit, targetUnit) == null)
				|| (recipient(game, unit, targetUnit) == null)) {
			receiver.targetCheckFailed(
					CommandStringErrorKeys.MUST_TARGET_AN_ENEMY_UNIT_WITH_POSITIVE_BUFFS_OR_A_SUMMONED_UNIT);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target instanceof CUnit ? (CUnit) target : null;
		if ((targetUnit == null) || targetUnit.isDead()) {
			return false;
		}
		final CBuff buff = stealableBuff(simulation, caster, targetUnit);
		final CUnit recipient = recipient(simulation, caster, targetUnit);
		if ((buff == null) || (recipient == null)) {
			return false;
		}
		if (buff instanceof CBuffTimed) {
			((CBuffTimed) buff).carryRemainingTime(simulation);
		}
		targetUnit.remove(simulation, buff);
		recipient.add(simulation, buff);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		simulation.createTemporarySpellEffectOnUnit(targetUnit, getAlias(), CEffectType.TARGET);
		simulation.createTemporarySpellEffectOnUnit(recipient, getAlias(), CEffectType.SPECIAL);
		return false;
	}

	@Override
	public void setAutoCastOn(final CSimulation simulation, final CUnit caster, final boolean autoCastOn,
			final boolean notify) {
		this.autoCastOn = autoCastOn;
		if (notify) {
			caster.setAutocastAbility(simulation, autoCastOn ? this : null);
		}
	}

	@Override
	public boolean isAutoCastOn() {
		return this.autoCastOn;
	}

	@Override
	public AutocastType getAutocastType() {
		return AutocastType.NEARESTVALID;
	}

	@Override
	public void checkCanAutoTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		this.checkCanTarget(game, unit, playerIndex, orderId, false, target, receiver);
	}

	@Override
	public void checkCanAutoTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityPointTarget target, final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanAutoTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
	}
}
