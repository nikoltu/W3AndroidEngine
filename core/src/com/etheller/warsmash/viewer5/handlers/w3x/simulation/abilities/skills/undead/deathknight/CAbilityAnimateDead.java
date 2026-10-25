package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.deathknight;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Animate Dead: raises corpses of any owner around the caster as its own invulnerable units for the duration.
 */
public class CAbilityAnimateDead extends CAbilityNoTargetSpellBase {
	private int numberOfCorpsesRaised;
	private float areaOfEffect;
	private War3ID buffId;

	public CAbilityAnimateDead(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.animatedead;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.numberOfCorpsesRaised = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final List<CUnit> corpses = new ArrayList<>();
		simulation.getWorldCollision().enumCorpsesInRange(caster.getX(), caster.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if ((corpses.size() < this.numberOfCorpsesRaised) && (enumUnit.getHeroData() == null)
							&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
						corpses.add(enumUnit);
					}
					return corpses.size() >= this.numberOfCorpsesRaised;
				});
		for (final CUnit corpse : corpses) {
			corpse.resurrect(simulation);
			if (corpse.getPlayerIndex() != caster.getPlayerIndex()) {
				corpse.setPlayerIndex(simulation, caster.getPlayerIndex(), true);
			}
			corpse.setInvulnerable(true);
			corpse.add(simulation, new CBuffTimedLife(simulation.getHandleIdAllocator().createId(), this.buffId,
					getDuration(), false));
			simulation.createTemporarySpellEffectOnUnit(corpse, getAlias(), CEffectType.TARGET);
		}
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return false;
	}
}
