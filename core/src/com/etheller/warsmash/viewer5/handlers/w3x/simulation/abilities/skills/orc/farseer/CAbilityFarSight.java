package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.farseer;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CTimedCircleFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * Far Sight: reveals the area around the target point to the caster's player for the duration.
 */
public class CAbilityFarSight extends CAbilityPointTargetSpellBase {
	private float areaOfEffect;

	public CAbilityFarSight(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.farsight;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		simulation.getPlayer(caster.getPlayerIndex()).addFogModifer(simulation, new CTimedCircleFogModifier(
				CFogState.VISIBLE, this.areaOfEffect, target.getX(), target.getY(), getDuration()), false);
		simulation.spawnSpellEffectOnPoint(target.getX(), target.getY(), 0, getAlias(), CEffectType.TARGET, 0)
				.remove();
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return false;
	}
}
