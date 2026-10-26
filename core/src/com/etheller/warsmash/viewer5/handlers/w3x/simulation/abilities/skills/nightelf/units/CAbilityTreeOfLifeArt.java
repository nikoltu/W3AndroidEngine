package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Tree of Life art: while the tree upgrades to the next tier its target art glows at its roots and hands.
 */
public class CAbilityTreeOfLifeArt extends CAbilityPassiveSpellBase {
	private CSimulation game;
	private CUnit unit;
	private SimulationRenderComponentModel upgradeArt;

	public CAbilityTreeOfLifeArt(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
		setIconShowing(false);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
	}

	@Override
	public boolean isUniversal() {
		return false;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.game = game;
		this.unit = unit;
		removeArt();
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		// Finishing an upgrade shows every ability icon again; this one has no button.
		if (isIconShowing()) {
			setIconShowing(false);
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
		removeArt();
	}

	@Override
	protected void onSetDisabled(final boolean disabled, final boolean wasDisabled, final CAbilityDisableType type) {
		if (type != CAbilityDisableType.CONSTRUCTION) {
			return;
		}
		if (disabled && (this.unit != null) && this.unit.isUpgrading()) {
			if (this.upgradeArt == null) {
				this.upgradeArt = this.game.createPersistentSpellEffectOnUnit(this.unit, getAlias(),
						CEffectType.TARGET);
			}
		}
		else if (!disabled) {
			removeArt();
		}
	}

	private void removeArt() {
		if (this.upgradeArt != null) {
			this.upgradeArt.remove();
			this.upgradeArt = null;
		}
	}
}
