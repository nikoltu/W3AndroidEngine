package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic;

import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;

public abstract class AbstractGenericAliasedAbility extends AbstractCAbility implements CLevelingAbility {
	private final War3ID alias;
	private int level = 1;
	// Per-level values a script set on this one unit's ability; they outlast level changes.
	private Map<Integer, Integer> manaCostOverrides;
	private Map<Integer, Float> cooldownOverrides;

	public AbstractGenericAliasedAbility(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code);
		this.alias = alias;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		return true;
	}

	@Override
	public War3ID getAlias() {
		return this.alias;
	}

	@Override
	public final int getLevel() {
		return this.level;
	}

	@Override
	public void setLevel(final CSimulation simulation, final CUnit unit, final int level) {
		this.level = level;
	}

	public final Integer getManaCostOverride(final int level) {
		return this.manaCostOverrides == null ? null : this.manaCostOverrides.get(level);
	}

	public final Float getCooldownOverride(final int level) {
		return this.cooldownOverrides == null ? null : this.cooldownOverrides.get(level);
	}

	public final void overrideManaCost(final int level, final int manaCost) {
		if (this.manaCostOverrides == null) {
			this.manaCostOverrides = new HashMap<>();
		}
		this.manaCostOverrides.put(level, manaCost);
		refreshCostOverrides();
	}

	public final void overrideCooldown(final int level, final float cooldown) {
		if (this.cooldownOverrides == null) {
			this.cooldownOverrides = new HashMap<>();
		}
		this.cooldownOverrides.put(level, cooldown);
		refreshCostOverrides();
	}

	/** Abilities that keep a mana cost and a cooldown per level load the overrides of their current level here. */
	protected void refreshCostOverrides() {
	}
}
