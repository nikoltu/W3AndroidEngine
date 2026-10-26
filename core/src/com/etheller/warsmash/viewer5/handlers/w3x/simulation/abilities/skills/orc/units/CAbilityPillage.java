package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitPriorityLoopData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;

/**
 * Pillage: attacks that damage an enemy building pay out the damaged share of the building cost times the salvage
 * cost ratio, in whole accumulation steps.
 */
public class CAbilityPillage extends CAbilityPassiveSpellBase implements CUnitAttackPostDamageListener {
	private float salvageCostRatio;
	private float accumulationStep;
	private float goldAccumulated;
	private float lumberAccumulated;

	public CAbilityPillage(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.salvageCostRatio = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.accumulationStep = Math.max(1, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0));
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.addPostDamageListener(this);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.removePostDamageListener(this);
	}

	@Override
	public void onHit(final CSimulation simulation, final CUnit attacker, final AbilityTarget target,
			final CUnitAttack cUnitAttack, final float damage, final CUnitPriorityLoopData postListenerLoop) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || !targetUnit.isBuilding() || (damage <= 0) || (targetUnit.getMaximumLife() <= 0)
				|| !targetUnit.canBeTargetedBy(simulation, attacker, getTargetsAllowed())) {
			return;
		}
		final float share = Math.min(damage, targetUnit.getMaximumLife()) / targetUnit.getMaximumLife()
				* this.salvageCostRatio;
		this.goldAccumulated += share * targetUnit.getUnitType().getGoldCost();
		this.lumberAccumulated += share * targetUnit.getUnitType().getLumberCost();
		final CPlayer player = simulation.getPlayer(attacker.getPlayerIndex());
		final int gold = (int) (Math.floor(this.goldAccumulated / this.accumulationStep) * this.accumulationStep);
		if (gold > 0) {
			this.goldAccumulated -= gold;
			player.addGold(gold);
			simulation.unitGainResourceEvent(attacker, attacker.getPlayerIndex(), ResourceType.GOLD, gold);
		}
		final int lumber = (int) (Math.floor(this.lumberAccumulated / this.accumulationStep)
				* this.accumulationStep);
		if (lumber > 0) {
			this.lumberAccumulated -= lumber;
			player.addLumber(lumber);
			simulation.unitGainResourceEvent(attacker, attacker.getPlayerIndex(), ResourceType.LUMBER, lumber);
		}
	}

	@Override
	public int getPriority(final CSimulation simulation, final CUnit attacker, final AbilityTarget target,
			final CUnitAttack cUnitAttack) {
		return 0;
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}
}
