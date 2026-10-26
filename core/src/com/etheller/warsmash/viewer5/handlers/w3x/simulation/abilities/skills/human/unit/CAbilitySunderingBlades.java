package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.unit;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackEffectListenerStacking;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListenerDamageModResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPreDamageListenerPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * The attack upgrade passives (Sundering Blades, Moon Glaive): the icon shows once the research is done and the
 * caster art is held on the weapon. Attacks on the listed defense type deal a flat and a percent bonus (only
 * Sundering Blades lists one; the Moon Glaive bounces come from its research).
 */
public class CAbilitySunderingBlades extends CAbilityPassiveSpellBase implements CUnitAttackPreDamageListener {
	private float flatBonus;
	private float percentBonus;
	private CDefenseType defenseType;
	private SimulationRenderComponentModel casterArt;

	public CAbilitySunderingBlades(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.flatBonus = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.percentBonus = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		final int defenseTypeIndex = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
		this.defenseType = (defenseTypeIndex >= 0) && (defenseTypeIndex < CDefenseType.VALUES.length)
				? CDefenseType.VALUES[defenseTypeIndex]
				: null;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.addPreDamageListener(CUnitAttackPreDamageListenerPriority.STACKING, this);
		if (this.casterArt == null) {
			this.casterArt = game.createPersistentSpellEffectOnUnit(unit, getAlias(), CEffectType.CASTER);
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.removePreDamageListener(CUnitAttackPreDamageListenerPriority.STACKING, this);
		if (this.casterArt != null) {
			this.casterArt.remove();
			this.casterArt = null;
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
		if (this.casterArt != null) {
			this.casterArt.remove();
			this.casterArt = null;
		}
	}

	@Override
	public CUnitAttackEffectListenerStacking onAttack(final CSimulation simulation, final CUnit attacker,
			final AbilityTarget target, final AbilityPointTarget attackImpactLocation, final CUnitAttack attack,
			final CUnitAttackSettings settings, final CUnitAttackPreDamageListenerDamageModResult damageResult) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit != null) && (targetUnit.getDefenseType() == this.defenseType)
				&& targetUnit.canBeTargetedBy(simulation, attacker, getTargetsAllowed())) {
			if (this.percentBonus != 0) {
				damageResult.addDamageMultiplier(1 + this.percentBonus);
			}
			if (this.flatBonus != 0) {
				damageResult.addBonusDamage(this.flatBonus);
			}
		}
		return new CUnitAttackEffectListenerStacking();
	}
}
