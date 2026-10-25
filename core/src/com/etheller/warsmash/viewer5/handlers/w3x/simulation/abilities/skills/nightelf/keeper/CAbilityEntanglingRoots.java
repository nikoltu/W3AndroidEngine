package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.keeper;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffStun;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Entangling Roots: holds the target in place and damages it every second for the duration.
 */
public class CAbilityEntanglingRoots extends CAbilityTargetSpellBase {
	private float damagePerSecond;
	private War3ID buffId;

	public CAbilityEntanglingRoots(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.entanglingroots;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damagePerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			targetUnit.add(simulation, new CBuffRoots(simulation.getHandleIdAllocator().createId(), this.buffId,
					getDurationForTarget(targetUnit), caster, this.damagePerSecond));
			simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		}
		return false;
	}

	private static final class CBuffRoots extends CBuffStun {
		private static final int TICKS_PER_SECOND = (int) StrictMath
				.round(1 / WarsmashConstants.SIMULATION_STEP_TIME);
		private final CUnit source;
		private final float damagePerSecond;
		private int nextDamageTick = -1;

		private CBuffRoots(final int handleId, final War3ID alias, final float duration, final CUnit source,
				final float damagePerSecond) {
			super(handleId, alias, duration);
			this.source = source;
			this.damagePerSecond = damagePerSecond;
		}

		@Override
		public void onTick(final CSimulation game, final CUnit unit) {
			final int tick = game.getGameTurnTick();
			if (this.nextDamageTick < 0) {
				this.nextDamageTick = tick + TICKS_PER_SECOND;
			}
			else if (tick >= this.nextDamageTick) {
				this.nextDamageTick = tick + TICKS_PER_SECOND;
				unit.damage(game, this.source, CAbilitySpellBase.DAMAGE_FLAGS, CAttackType.SPELLS,
						CDamageType.UNIVERSAL, CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerSecond);
			}
			super.onTick(game, unit);
		}
	}
}
