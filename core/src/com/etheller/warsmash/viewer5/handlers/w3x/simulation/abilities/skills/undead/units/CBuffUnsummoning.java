package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.CPausedTickingBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;

/**
 * A building being unsummoned: it cannot act, loses its life a step each second and refunds the matching share of
 * its cost, then vanishes.
 */
public class CBuffUnsummoning extends CBuffTimed implements CPausedTickingBuff {
	private static final int TICKS_PER_SECOND = CTimer.wholeSteps(1.0f);

	private final float salvageCostRatio;
	private final float lifePerSecond;
	private float goldOwed;
	private float lumberOwed;
	private final boolean firstStepAtOnce;
	private int nextStepTick;

	public CBuffUnsummoning(final int handleId, final War3ID alias, final float salvageCostRatio,
			final float lifePerSecond) {
		this(handleId, alias, salvageCostRatio, lifePerSecond, false);
	}

	public CBuffUnsummoning(final int handleId, final War3ID alias, final float salvageCostRatio,
			final float lifePerSecond, final boolean firstStepAtOnce) {
		super(handleId, alias, alias, 0);
		this.salvageCostRatio = salvageCostRatio;
		this.lifePerSecond = lifePerSecond;
		this.firstStepAtOnce = firstStepAtOnce;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.setPaused(true);
		this.nextStepTick = game.getGameTurnTick() + (this.firstStepAtOnce ? 0 : TICKS_PER_SECOND);
		game.unitLoopSoundEffectEvent(unit, getAlias());
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		game.unitStopSoundEffectEvent(unit, getAlias());
		if (!unit.isDead()) {
			unit.setPaused(false);
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (game.getGameTurnTick() < this.nextStepTick) {
			return;
		}
		this.nextStepTick += TICKS_PER_SECOND;
		final float maximumLife = unit.getMaximumLife();
		final float drained = Math.min(this.lifePerSecond, unit.getLife());
		final float share = (maximumLife > 0 ? drained / maximumLife : 1) * this.salvageCostRatio;
		this.goldOwed += share * unit.getUnitType().getGoldCost();
		this.lumberOwed += share * unit.getUnitType().getLumberCost();
		final boolean finished = drained >= unit.getLife();
		if (finished) {
			this.goldOwed = Math.round(this.goldOwed);
			this.lumberOwed = Math.round(this.lumberOwed);
		}
		final int gold = (int) this.goldOwed;
		final int lumber = (int) this.lumberOwed;
		this.goldOwed -= gold;
		this.lumberOwed -= lumber;
		final CPlayer player = game.getPlayer(unit.getPlayerIndex());
		if ((gold > 0) || (lumber > 0)) {
			player.refund(gold, lumber);
			if (gold > 0) {
				game.unitGainResourceEvent(unit, unit.getPlayerIndex(), ResourceType.GOLD, gold);
			}
			if (lumber > 0) {
				game.unitGainResourceEvent(unit, unit.getPlayerIndex(), ResourceType.LUMBER, lumber);
			}
		}
		if (finished && this.firstStepAtOnce) {
			// Measured: a Haunted Gold Mine that ran out of gold dies and plays its death before it is gone.
			unit.remove(game, this);
			unit.kill(game);
		}
		else if (finished) {
			unit.remove(game, this);
			unit.killPathingInstance();
			game.removeUnit(unit);
		}
		else {
			unit.setLife(game, unit.getLife() - drained);
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
		cUnit.remove(game, this);
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return false;
	}
}
