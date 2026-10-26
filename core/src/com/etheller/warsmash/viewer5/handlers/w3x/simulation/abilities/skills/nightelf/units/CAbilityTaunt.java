package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Taunt: the closest enemies around the unit are made to attack it, pulsing again after the interval as many times
 * as the data says.
 */
public class CAbilityTaunt extends CAbilityNoTargetSpellBase {
	private int maximumUnits;
	private boolean preferHostiles;
	private boolean preferFriendlies;
	private int pulseCount;
	private float pulseInterval;
	private float areaOfEffect;

	private int pulsesLeft;
	private int nextPulseTick;

	public CAbilityTaunt(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.taunt;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.maximumUnits = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.preferHostiles = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_B + level, 0);
		this.preferFriendlies = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_C + level, 0);
		this.pulseCount = Math.max(1, worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_D + level, 0));
		this.pulseInterval = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.pulsesLeft = this.pulseCount;
		pulse(simulation, caster);
		return false;
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if ((this.pulsesLeft > 0) && (game.getGameTurnTick() >= this.nextPulseTick)) {
			if (unit.isDead()) {
				this.pulsesLeft = 0;
			}
			else {
				pulse(game, unit);
			}
		}
	}

	private void pulse(final CSimulation simulation, final CUnit caster) {
		this.pulsesLeft--;
		this.nextPulseTick = simulation.getGameTurnTick()
				+ (int) (this.pulseInterval / WarsmashConstants.SIMULATION_STEP_TIME);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		final List<CUnit> candidates = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect, (enemy) -> {
			if ((enemy != caster) && !enemy.isDead() && !enemy.isHidden()
					&& enemy.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
				candidates.add(enemy);
			}
			return false;
		});
		candidates.sort(Comparator.comparingInt((final CUnit enemy) -> preferenceRank(simulation, caster, enemy))
				.thenComparingDouble((final CUnit enemy) -> caster.distance(enemy)));
		int taunted = 0;
		for (int i = 0; (i < candidates.size()) && (taunted < this.maximumUnits); i++) {
			// Units that cannot attack the caster are not counted against the limit.
			if (candidates.get(i).order(simulation, OrderIds.attack, caster)) {
				taunted++;
			}
		}	}

	private int preferenceRank(final CSimulation simulation, final CUnit caster, final CUnit enemy) {
		final boolean hostile = !simulation.getPlayer(enemy.getPlayerIndex()).hasAlliance(caster.getPlayerIndex(),
				CAllianceType.PASSIVE);
		if (this.preferHostiles && !hostile) {
			return 1;
		}
		if (this.preferFriendlies && hostile) {
			return 1;
		}
		return 0;
	}
}
