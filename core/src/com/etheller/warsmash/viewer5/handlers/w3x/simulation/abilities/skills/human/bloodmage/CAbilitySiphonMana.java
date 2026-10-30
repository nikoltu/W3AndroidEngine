package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.bloodmage;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;

/**
 * Channeled drain shared by the drain abilities: every interval it takes life and mana from an enemy target and
 * gives the caster its share, or gives the caster's mana to a friendly target. The channel breaks when the target
 * dies or moves beyond the break distance.
 */
public class CAbilitySiphonMana extends CAbilityTargetSpellBase {
	private float lifeDrainedPerSecond;
	private float manaDrainedPerSecond;
	private float interval;
	private float lifeTransferredPerSecond;
	private float manaTransferredPerSecond;
	private float breakDistance;

	private int channelEndTick;
	private SimulationRenderComponentLightning beam;
	private int nextDrainTick;

	public CAbilitySiphonMana(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.drain;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.lifeDrainedPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.manaDrainedPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.interval = Math.max(0.1f, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0));
		this.lifeTransferredPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.manaTransferredPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.breakDistance = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit == null) {
			return false;
		}
		final int tick = simulation.getGameTurnTick();
		this.channelEndTick = tick + ticks(getDurationForTarget(targetUnit));
		this.nextDrainTick = tick + ticks(this.interval);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		simulation.createTemporarySpellEffectOnUnit(targetUnit, getAlias(), CEffectType.TARGET);
		removeBeam();
		// A negative duration keeps the beam until the channel ends instead of the lightning type's own duration.
		this.beam = simulation.createAbilityLightning(caster, getAlias(), beamIndex(simulation, caster, targetUnit),
				targetUnit, -1f);
		return true;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		removeBeam();
	}

	private void removeBeam() {
		if (this.beam != null) {
			this.beam.remove();
			this.beam = null;
		}
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || targetUnit.isDead() || caster.isDead()
				|| ((this.breakDistance > 0) && (caster.distance(targetUnit) > this.breakDistance))) {
			return false;
		}
		final int tick = simulation.getGameTurnTick();
		if (tick >= this.nextDrainTick) {
			this.nextDrainTick = tick + ticks(this.interval);
			if (isFriendly(simulation, caster, targetUnit)) {
				final float given = Math.min(caster.getMana(), this.manaTransferredPerSecond * this.interval);
				caster.setMana(caster.getMana() - given);
				targetUnit.setMana(Math.min(targetUnit.getMaximumMana(), targetUnit.getMana() + given));
			}
			else {
				final float mana = Math.min(targetUnit.getMana(), this.manaDrainedPerSecond * this.interval);
				targetUnit.setMana(targetUnit.getMana() - mana);
				caster.setMana(Math.min(caster.getMaximumMana(), caster.getMana() + mana));
				if (this.lifeDrainedPerSecond > 0) {
					// Measured: the drain is spell damage, so heroes lose 70% of it and armor does not count.
					targetUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
							null, this.lifeDrainedPerSecond * this.interval);
					caster.setLife(simulation, Math.min(caster.getMaximumLife(),
							caster.getLife() + (this.lifeTransferredPerSecond * this.interval)));
				}
			}
		}
		return tick < this.channelEndTick;
	}

	/**
	 * The drain abilities list three lightning types: drain (life and mana), life drain and mana drain.
	 */
	private int beamIndex(final CSimulation simulation, final CUnit caster, final CUnit target) {
		if (isFriendly(simulation, caster, target)) {
			return 2;
		}
		final boolean life = this.lifeDrainedPerSecond > 0;
		final boolean mana = this.manaDrainedPerSecond > 0;
		if (life && mana) {
			return 0;
		}
		return life ? 1 : 2;
	}

	private static boolean isFriendly(final CSimulation simulation, final CUnit caster, final CUnit target) {
		return simulation.getPlayer(caster.getPlayerIndex()).hasAlliance(target.getPlayerIndex(),
				CAllianceType.SHARED_SPELLS);
	}

	private static int ticks(final float seconds) {
		return (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}
}
