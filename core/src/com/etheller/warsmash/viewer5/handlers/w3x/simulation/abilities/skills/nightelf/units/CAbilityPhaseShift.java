package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.AutocastType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

/**
 * Phase Shift: while auto-cast is on, the first damage the unit takes shifts it out of existence for a short time,
 * so that nothing can harm it. It can also be triggered at once by its order.
 */
public class CAbilityPhaseShift extends CAbilityNoTargetSpellBase
		implements CAutocastAbility, CUnitAttackDamageTakenListener {
	private War3ID buffId;
	private boolean autoCastOn;
	private CUnit unit;

	public CAbilityPhaseShift(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.phaseshift;
	}

	@Override
	public int getAutoCastOnOrderId() {
		return OrderIds.phaseshifton;
	}

	@Override
	public int getAutoCastOffOrderId() {
		return OrderIds.phaseshiftoff;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		super.onAdd(game, unit);
		this.unit = unit;
		unit.addDamageTakenListener(this);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.removeDamageTakenListener(this);
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if ((orderId == OrderIds.phaseshift) || (orderId == OrderIds.phaseshiftinstant)) {
			tryShift(game, caster);
			return false;
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	@Override
	public void onDamage(final CSimulation simulation, final CUnit attacker, final CUnit target,
			final CDamageFlags flags, final CDamageType weaponType, final float damage, final float bonusDamage,
			final float trueDamage) {
		if (this.autoCastOn && (trueDamage > 0) && !target.isDead()) {
			tryShift(simulation, target);
		}
	}

	private void tryShift(final CSimulation game, final CUnit caster) {
		if (isDisabled() || (getCooldownRemaining(game, caster) > 0) || (caster.getMana() < getManaCost())) {
			return;
		}
		for (final CAbility ability : caster.getAbilities()) {
			if (ability instanceof CBuffPhaseShift) {
				return;
			}
		}
		caster.chargeMana(getManaCost());
		caster.beginCooldown(game, getCode(), getCooldown());
		game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.SPECIAL);
		game.unitSoundEffectEvent(caster, getAlias());
		caster.add(game, new CBuffPhaseShift(game.getHandleIdAllocator().createId(), this.buffId,
				getDurationForTarget(caster)));
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		tryShift(simulation, caster);
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
		return AutocastType.NOAUTOCASTBEHAVIOR;
	}

	@Override
	public void checkCanAutoTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
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
