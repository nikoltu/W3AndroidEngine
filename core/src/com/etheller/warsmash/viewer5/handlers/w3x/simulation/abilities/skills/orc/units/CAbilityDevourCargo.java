package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericNoIconAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitVisionFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

/**
 * Cargo Hold (Devour): holds the devoured unit, digests it every second, frees it when the holder dies, and lets
 * its owner see through the holder's eyes meanwhile.
 */
public class CAbilityDevourCargo extends AbstractGenericNoIconAbility implements CAbilitySpell {
	private static final CSpellDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();
	private static final int TICKS_PER_SECOND = (int) (1.0f / WarsmashConstants.SIMULATION_STEP_TIME);

	private float damagePerSecond;
	private int maximumCreepLevel;
	private CUnit devouredUnit;
	private CBuffDigesting digestingBuff;
	private CUnitVisionFogModifier devouredOwnerVision;
	private int nextDigestTick;
	private boolean devouredWhilePaused;

	public CAbilityDevourCargo(final int handleId, final War3ID alias) {
		super(handleId, alias, alias);
	}

	@Override
	public void populate(final GameObject worldEditorAbility, final int level) {
		this.damagePerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.maximumCreepLevel = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
	}

	public boolean isEmpty() {
		return this.devouredUnit == null;
	}

	public CUnit getDevouredUnit() {
		return this.devouredUnit;
	}

	public int getMaximumCreepLevel() {
		return this.maximumCreepLevel;
	}

	public void devour(final CSimulation game, final CUnit holder, final CUnit target, final War3ID digestingBuffId) {
		this.devouredUnit = target;
		this.devouredWhilePaused = target.isPaused();
		target.setHidden(true);
		target.setPaused(true);
		this.nextDigestTick = game.getGameTurnTick() + TICKS_PER_SECOND;
		if ((digestingBuffId != null) && !War3ID.NONE.equals(digestingBuffId)) {
			this.digestingBuff = new CBuffDigesting(game.getHandleIdAllocator().createId(), digestingBuffId);
			holder.add(game, this.digestingBuff);
		}
		if (target.getPlayerIndex() != holder.getPlayerIndex()) {
			this.devouredOwnerVision = new CUnitVisionFogModifier(holder, false);
			game.getPlayer(target.getPlayerIndex()).addFogModifer(game, this.devouredOwnerVision, true);
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		// A unit that was already paused when devoured is held but not digested, as in the original game.
		if ((this.devouredUnit != null) && !this.devouredWhilePaused
				&& (game.getGameTurnTick() >= this.nextDigestTick)) {
			this.nextDigestTick += TICKS_PER_SECOND;
			final CUnit victim = this.devouredUnit;
			victim.setPointAndCheckUnstuck(unit.getX(), unit.getY(), game);
			victim.damage(game, unit, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
					CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerSecond);
			if (victim.isDead()) {
				finishDigesting(game, unit);
				game.removeUnit(victim);
			}
		}
	}

	private void finishDigesting(final CSimulation game, final CUnit unit) {
		if (this.devouredOwnerVision != null) {
			game.getPlayer(this.devouredUnit.getPlayerIndex()).removeFogModifer(game, this.devouredOwnerVision);
			this.devouredOwnerVision = null;
		}
		this.devouredUnit = null;
		if (this.digestingBuff != null) {
			unit.remove(game, this.digestingBuff);
			this.digestingBuff = null;
		}
	}

	public void release(final CSimulation game, final CUnit unit) {
		if (this.devouredUnit != null) {
			final CUnit freed = this.devouredUnit;
			finishDigesting(game, unit);
			freed.setPointAndCheckUnstuck(unit.getX(), unit.getY(), game);
			freed.setHidden(false);
			freed.setPaused(this.devouredWhilePaused);
		}
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		release(game, unit);
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit unit) {
		release(game, unit);
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return null;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder) {
		return null;
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
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
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final AbilityActivationReceiver receiver) {
		receiver.notAnActiveAbility();
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId) {
	}

	@Override
	public boolean isPhysical() {
		return false;
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
