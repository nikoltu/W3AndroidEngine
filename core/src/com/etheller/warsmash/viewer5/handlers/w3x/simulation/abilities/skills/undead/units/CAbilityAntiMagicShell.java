package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Anti-magic Shell: with a shield life, a barrier that soaks up spell, magic and summoned unit damage until it
 * breaks; without one, the unit cannot be targeted by spells and takes reduced magic damage.
 */
public class CAbilityAntiMagicShell extends CAbilityTargetSpellBase {
	private float magicDamageReduction;
	private float shieldLife;
	private War3ID immunityBuffId;
	private War3ID shieldBuffId;

	public CAbilityAntiMagicShell(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.antimagicshell;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.magicDamageReduction = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.shieldLife = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.immunityBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		final War3ID shieldBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
		this.shieldBuffId = War3ID.NONE.equals(shieldBuffId) ? this.immunityBuffId : shieldBuffId;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			for (final CAbility ability : targetUnit.getAbilities()) {
				if (ability instanceof CBuffAntiMagicShell) {
					targetUnit.remove(simulation, (CBuffAntiMagicShell) ability);
					break;
				}
			}
			final boolean shield = this.shieldLife > 0;
			targetUnit.add(simulation,
					new CBuffAntiMagicShell(simulation.getHandleIdAllocator().createId(),
							shield ? this.shieldBuffId : this.immunityBuffId, getDurationForTarget(targetUnit),
							this.shieldLife, this.magicDamageReduction));
		}
		return false;
	}
}
