package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.creeps;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffSpellArt;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Permanent Immolation (infernals, the Fire spirit of Storm, Earth and Fire): always burning, it hurts nearby
 * enemies by Data A every Duration seconds without any mana.
 */
public class CAbilityPermanentImmolation extends CAbilityPassiveSpellBase {
	private static final CDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();

	private float damagePerInterval;
	private War3ID buffId;
	private CBuffSpellArt buff;
	private int nextPulseTick;
	private final boolean marksTargets;

	public CAbilityPermanentImmolation(final int handleId, final War3ID alias) {
		this(handleId, alias, false);
	}

	/**
	 * @param marksTargets Cloak of Flames: the units in its area carry its buff for a pulse, paused ones too.
	 */
	public CAbilityPermanentImmolation(final int handleId, final War3ID alias, final boolean marksTargets) {
		super(handleId, alias, alias);
		this.marksTargets = marksTargets;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damagePerInterval = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		// Measured on 1.29: the burning unit itself carries the buff (level 1), the units it hurts do not.
		this.buff = new CBuffSpellArt(game.getHandleIdAllocator().createId(), this.buffId, true, true);
		unit.add(game, this.buff);
		this.nextPulseTick = game.getGameTurnTick();
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		if (this.buff != null) {
			unit.remove(game, this.buff);
			this.buff = null;
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit unit) {
		this.buff = null;
	}

	@Override
	public void onTick(final CSimulation game, final CUnit source) {
		if (isDisabled() || source.isDead() || source.isHidden() || source.isPaused()) {
			return;
		}
		final int tick = game.getGameTurnTick();
		if (tick < this.nextPulseTick) {
			return;
		}
		this.nextPulseTick = tick + Math.max(1,
				CTimer.wholeSteps(getDuration()));
		final float area = getAreaOfEffect();
		game.getWorldCollision().enumUnitsInRange(source.getX(), source.getY(), area, (enumUnit) -> {
			if ((enumUnit == source) || !source.canReach(enumUnit, area)
					|| !enumUnit.canBeAreaTargetedBy(game, source, CAreaTargetVisibility.ANY, getTargetsAllowed(),
							BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
				return false;
			}
			if (this.marksTargets) {
				markTarget(game, enumUnit);
			}
			if (!enumUnit.isPaused()) {
				enumUnit.damage(game, source, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.FIRE,
						CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerInterval);
				game.createPersistentSpellEffectOnUnit(enumUnit, this.buffId, CEffectType.SPECIAL, 0).remove();
			}
			return false;
		});
	}

	private void markTarget(final CSimulation game, final CUnit target) {
		final CAbility previous = target.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(this.buffId));
		if (previous instanceof CBuffTargetMark) {
			target.remove(game, (CBuffTargetMark) previous);
		}
		target.add(game, new CBuffTargetMark(game.getHandleIdAllocator().createId(), this.buffId, getDuration()));
	}

	private static final class CBuffTargetMark extends CBuffTimed {
		private CBuffTargetMark(final int handleId, final War3ID alias, final float duration) {
			super(handleId, alias, alias, duration);
		}

		@Override
		protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		}

		@Override
		protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		}

		@Override
		public boolean isTimedLifeBar() {
			return false;
		}

		@Override
		public boolean isPositive() {
			return false;
		}

		@Override
		public boolean isDispellable() {
			return true;
		}

		@Override
		public boolean isMagic() {
			return true;
		}
	}
}
