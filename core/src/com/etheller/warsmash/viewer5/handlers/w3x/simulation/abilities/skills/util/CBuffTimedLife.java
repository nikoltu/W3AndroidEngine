package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

public class CBuffTimedLife extends CBuffTimed {

	private final boolean explode;

	private final boolean summon;
	private boolean replaced;

	public CBuffTimedLife(final int handleId, final War3ID alias, final float duration, boolean explode) {
		this(handleId, alias, duration, explode, true);
	}

	/**
	 * A non-summon timed life (UnitApplyTimedLife) leaves the unit's food alone.
	 */
	public CBuffTimedLife(final int handleId, final War3ID alias, final float duration, final boolean explode,
			final boolean summon) {
		super(handleId, alias, alias, duration);
		this.explode = explode;
		this.summon = summon;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		if (this.explode) {
			unit.setExplodesOnDeath(true);
			unit.setExplodesOnDeathBuffId(getAlias());
		}

		if (this.summon) {
			final CPlayer player = game.getPlayer(unit.getPlayerIndex());
			player.setUnitFoodMade(unit, 0);
			player.setUnitFoodUsed(unit, 0);
		}
	}

	public void markReplaced() {
		this.replaced = true;
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		if (!this.replaced) {
			unit.kill(game);
		}
	}

	@Override
	public boolean isTimedLifeBar() {
		return true;
	}
}
