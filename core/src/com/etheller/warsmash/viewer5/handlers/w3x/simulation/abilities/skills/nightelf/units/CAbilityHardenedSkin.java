package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackFinalDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;

/**
 * Hardened Skin: attacks on the unit lose a flat amount of damage after armor, but are never reduced below the
 * minimum damage.
 */
public class CAbilityHardenedSkin extends CAbilityPassiveSpellBase
		implements CUnitAttackFinalDamageTakenModificationListener {
	private float chanceToReduce;
	private float minimumDamage;
	private float ignoredDamage;
	private boolean includeRanged;
	private boolean includeMelee;

	public CAbilityHardenedSkin(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.chanceToReduce = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.minimumDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.ignoredDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.includeRanged = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_D + level, 0);
		this.includeMelee = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_E + level, 0);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.addFinalDamageTakenModificationListener(this);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.removeFinalDamageTakenModificationListener(this);
	}

	@Override
	public float onDamage(final CSimulation simulation, final CUnit attacker, final CUnit target,
			final CDamageFlags flags, final CAttackType attackType, final CDamageType damageType,
			final float previousDamage) {
		if ((flags == null) || !flags.isAttack() || (previousDamage <= 0)) {
			return previousDamage;
		}
		if (!(flags.isRanged() ? this.includeRanged : this.includeMelee)) {
			return previousDamage;
		}
		if ((this.chanceToReduce < 100)
				&& ((simulation.getSeededRandom().nextFloat() * 100) >= this.chanceToReduce)) {
			return previousDamage;
		}
		return Math.max(previousDamage - this.ignoredDamage, Math.min(previousDamage, this.minimumDamage));
	}
}
