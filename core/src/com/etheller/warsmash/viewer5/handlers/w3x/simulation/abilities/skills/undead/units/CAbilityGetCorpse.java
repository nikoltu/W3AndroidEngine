package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.AutocastType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHoldCorpses;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Get Corpse: the Meat Wagon picks up a corpse into its cargo hold. With autocast on it goes for the nearest
 * corpse within the ability's area whenever it is idle and has room.
 */
public class CAbilityGetCorpse extends CAbilityTargetSpellBase implements CAutocastAbility {
	private float searchArea;
	private boolean autoCastOn;
	private CBehaviorTargetSpellBase corpseBehavior;

	public CAbilityGetCorpse(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.loadcorpse;
	}

	@Override
	public int getAutoCastOnOrderId() {
		return OrderIds.loadcorpseon;
	}

	@Override
	public int getAutoCastOffOrderId() {
		return OrderIds.loadcorpseoff;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.searchArea = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.corpseBehavior = new CBehaviorTargetSpellBase(unit, this) {
			@Override
			protected boolean checkTargetStillValid(final CSimulation simulation) {
				final CUnit corpse = this.target.visit(AbilityTargetVisitor.UNIT);
				return (corpse != null) && corpse.isDead() && !corpse.isHidden();
			}
		};
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return this.corpseBehavior.reset(game, target);
	}

	private static CAbilityCargoHoldCorpses getHold(final CUnit unit) {
		return unit.getFirstAbilityOfType(CAbilityCargoHoldCorpses.class);
	}

	@Override
	protected void innerCheckCanUseSpell(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityActivationReceiver receiver) {
		final CAbilityCargoHoldCorpses hold = getHold(unit);
		if ((hold == null) || hold.isFull()) {
			receiver.activationCheckFailed(CommandStringErrorKeys.CARGO_CAPACITY_UNAVAILABLE);
		}
		else {
			receiver.useOk();
		}
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CAbilityCargoHoldCorpses hold = getHold(unit);
		if ((hold == null) || hold.isFull()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.CARGO_CAPACITY_UNAVAILABLE);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	protected void innerCheckCanTargetNoTarget(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityTargetCheckReceiver<Void> receiver) {
		final CAbilityCargoHoldCorpses hold = getHold(unit);
		if ((hold == null) || hold.isFull()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.CARGO_CAPACITY_UNAVAILABLE);
		}
		else if (findNearestCorpse(game, unit) == null) {
			receiver.targetCheckFailed(CommandStringErrorKeys.THERE_ARE_NO_USABLE_CORPSES_NEARBY);
		}
		else {
			receiver.targetOk(null);
		}
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder) {
		final CUnit corpse = findNearestCorpse(game, caster);
		if (corpse == null) {
			return caster.pollNextOrderBehavior(game);
		}
		return begin(game, caster, playerIndex, orderId, autoOrder, corpse);
	}

	private CUnit findNearestCorpse(final CSimulation game, final CUnit caster) {
		final CUnit[] nearest = { null };
		final double[] nearestDistance = { Double.MAX_VALUE };
		game.getWorldCollision().enumCorpsesInRange(caster.getX(), caster.getY(), this.searchArea, (corpse) -> {
			if (corpse.canBeTargetedBy(game, caster, getTargetsAllowed())) {
				final double distance = caster.distance(corpse);
				if (distance < nearestDistance[0]) {
					nearestDistance[0] = distance;
					nearest[0] = corpse;
				}
			}
			return false;
		});
		return nearest[0];
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit corpse = target.visit(AbilityTargetVisitor.UNIT);
		final CAbilityCargoHoldCorpses hold = getHold(caster);
		if ((corpse != null) && (hold != null) && !hold.isFull() && corpse.isDead() && !corpse.isHidden()) {
			simulation.unitSoundEffectEvent(caster, hold.getAlias());
			hold.addCorpse(simulation, caster, corpse);
		}
		return false;
	}

	@Override
	public AutocastType getAutocastType() {
		return AutocastType.NOTARGET;
	}

	@Override
	public boolean isAutoCastOn() {
		return this.autoCastOn;
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
		innerCheckCanTargetNoTarget(game, unit, orderId, receiver);
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
