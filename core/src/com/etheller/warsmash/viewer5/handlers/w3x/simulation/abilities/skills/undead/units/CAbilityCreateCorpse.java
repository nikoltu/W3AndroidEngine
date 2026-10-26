package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;

/**
 * Create Corpse (Graveyard): once built, the graveyard raises its gravestones in a ring around itself and lays a
 * corpse beyond an empty one after every interval, up to one corpse per gravestone.
 */
public class CAbilityCreateCorpse extends CAbilityPassiveSpellBase {
	private int maximumCorpses;
	private float gravestoneRadius;
	private float corpseRadius;
	private float interval;
	private War3ID corpseUnitType;

	private SimulationRenderComponent[] gravestones;
	private CUnit[] corpses;
	private int ticksTowardsCorpse;

	public CAbilityCreateCorpse(final int handleId, final War3ID alias) {
		super(handleId, alias, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.maximumCorpses = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.gravestoneRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.corpseRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.interval = worldEditorAbility.getFieldAsFloat(AbilityFields.COOLDOWN + level, 0);
		final String unitType = worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0);
		this.corpseUnitType = (unitType == null) || (unitType.length() != 4) ? null : War3ID.fromString(unitType);
	}

	private double slotAngle(final CUnit unit, final int slot) {
		return Math.toRadians(unit.getFacing()) + (((Math.PI * 2) * slot) / this.maximumCorpses);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (isDisabled() || unit.isDead() || unit.isConstructing() || (this.corpseUnitType == null)
				|| (this.maximumCorpses <= 0)) {
			return;
		}
		if (this.gravestones == null) {
			this.gravestones = new SimulationRenderComponent[this.maximumCorpses];
			this.corpses = new CUnit[this.maximumCorpses];
			for (int i = 0; i < this.maximumCorpses; i++) {
				final double angle = slotAngle(unit, i);
				this.gravestones[i] = game.spawnSpellEffectOnPoint(
						unit.getX() + (float) (Math.cos(angle) * this.gravestoneRadius),
						unit.getY() + (float) (Math.sin(angle) * this.gravestoneRadius),
						(float) Math.toDegrees(angle), getAlias(), CEffectType.SPECIAL, 0);
			}
		}
		int emptySlot = -1;
		for (int i = 0; i < this.maximumCorpses; i++) {
			final CUnit corpse = this.corpses[i];
			if ((corpse == null) || !corpse.isDead() || corpse.isHidden()) {
				this.corpses[i] = null;
				if (emptySlot == -1) {
					emptySlot = i;
				}
			}
		}
		if (emptySlot == -1) {
			this.ticksTowardsCorpse = 0;
			return;
		}
		this.ticksTowardsCorpse++;
		if ((this.ticksTowardsCorpse * WarsmashConstants.SIMULATION_STEP_TIME) >= this.interval) {
			this.ticksTowardsCorpse = 0;
			final double angle = slotAngle(unit, emptySlot);
			final CUnit corpse = game.createUnit(this.corpseUnitType, unit.getPlayerIndex(),
					unit.getX() + (float) (Math.cos(angle) * this.corpseRadius),
					unit.getY() + (float) (Math.sin(angle) * this.corpseRadius), (float) Math.toDegrees(angle));
			if (corpse != null) {
				corpse.becomeCorpse(game);
				this.corpses[emptySlot] = corpse;
			}
		}
	}

	private void removeGravestones() {
		if (this.gravestones != null) {
			for (final SimulationRenderComponent gravestone : this.gravestones) {
				if (gravestone != null) {
					gravestone.remove();
				}
			}
			this.gravestones = null;
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		removeGravestones();
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit unit) {
		removeGravestones();
	}

	public int getCorpseCount() {
		int count = 0;
		if (this.corpses != null) {
			for (final CUnit corpse : this.corpses) {
				if ((corpse != null) && corpse.isDead() && !corpse.isHidden()) {
					count++;
				}
			}
		}
		return count;
	}
}
