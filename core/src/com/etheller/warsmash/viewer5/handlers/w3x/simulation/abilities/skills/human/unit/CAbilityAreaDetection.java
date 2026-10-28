package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.unit;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CTimedCircleFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * Reveals an area anywhere on the map for the duration and detects the invisible units in it: Flare (Mortar Team,
 * after its effect delay) and Reveal (Arcane Tower, Crystal Ball).
 */
public class CAbilityAreaDetection extends CAbilityPointTargetSpellBase {
	private final int baseOrderId;
	private final boolean hasEffectDelay;
	private float areaOfEffect;
	private byte detectLevels;
	private float effectDelay;

	public CAbilityAreaDetection(final int handleId, final War3ID alias, final int baseOrderId,
			final boolean hasEffectDelay) {
		super(handleId, alias);
		this.baseOrderId = baseOrderId;
		this.hasEffectDelay = hasEffectDelay;
	}

	@Override
	public int getBaseOrderId() {
		return this.baseOrderId;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.detectLevels = detectLevels(worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0));
		this.effectDelay = this.hasEffectDelay
				? worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0)
				: 0;
	}

	public static byte detectLevels(final int detectionType) {
		switch (detectionType) {
		case 1:
			return 1;
		case 2:
			return 2;
		case 3:
			return 127;
		default:
			return 0;
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final float x = target.getX();
		final float y = target.getY();
		final int playerIndex = caster.getPlayerIndex();
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		if (this.effectDelay > 0) {
			final CTimer delay = new CTimer() {
				@Override
				public void onFire(final CSimulation game) {
					reveal(game, playerIndex, x, y);
				}
			};
			delay.setTimeoutTime(this.effectDelay);
			delay.start(simulation);
		}
		else {
			reveal(simulation, playerIndex, x, y);
		}
		return false;
	}

	private void reveal(final CSimulation simulation, final int playerIndex, final float x, final float y) {
		simulation.getPlayer(playerIndex).addFogModifer(simulation, new CTimedCircleFogModifier(CFogState.VISIBLE,
				this.areaOfEffect, x, y, getDuration(), this.detectLevels), false);
		simulation.spawnSpellEffectOnPoint(x, y, 0, getAlias(), CEffectType.EFFECT, 0).remove();
	}
}
