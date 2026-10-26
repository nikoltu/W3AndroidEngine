package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.unit;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Cloud (Dragonhawk Rider): a cloud stays over the target area for the duration and the enemy buildings under it
 * cannot use the attacks it prevents.
 */
public class CAbilityCloud extends CAbilityPointTargetSpellBase {
	private float areaOfEffect;
	private int attacksPrevented;
	private War3ID buffId;

	public CAbilityCloud(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.cloudoffog;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.attacksPrevented = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final float duration = getDuration();
		final SimulationRenderComponentModel cloud = simulation.spawnSpellEffectOnPoint(target.getX(),
				target.getY(), 0, getAlias(), CEffectType.EFFECT, 0);
		final CTimer fade = new CTimer() {
			@Override
			public void onFire(final CSimulation game) {
				cloud.remove();
			}
		};
		fade.setTimeoutTime(duration);
		fade.start(simulation);
		simulation.getWorldCollision().enumUnitsInRange(target.getX(), target.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if (!enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
							&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
						final CBuffCloud previous = enumUnit.getFirstAbilityOfType(CBuffCloud.class);
						if (previous != null) {
							enumUnit.remove(simulation, previous);
						}
						enumUnit.add(simulation, new CBuffCloud(simulation.getHandleIdAllocator().createId(),
								this.buffId, duration, this.attacksPrevented));
					}
					return false;
				});
		return false;
	}
}
