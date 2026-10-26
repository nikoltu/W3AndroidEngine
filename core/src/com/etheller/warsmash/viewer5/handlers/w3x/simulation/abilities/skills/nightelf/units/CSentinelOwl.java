package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CDestructableBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * The Sentinel owl perched above a tree. It sees from above, so terrain does not block its sight, and it detects
 * invisible units within its sight radius. It is a magic buff on the tree, so Abolish Magic and other dispels drive
 * it away.
 */
public class CSentinelOwl extends CFogModifier implements CDestructableBuff {
	private static final byte ALL_DETECTION_LEVELS = (byte) ~0;

	private final int handleId;
	private final War3ID alias;
	private final CDestructable tree;
	private final CPlayer player;
	private final float sightRadius;
	private final float duration;
	private int endTick;
	private SimulationRenderComponentModel model;
	private boolean gone;

	public CSentinelOwl(final int handleId, final War3ID alias, final CDestructable tree, final CPlayer player,
			final float sightRadius, final float duration) {
		this.handleId = handleId;
		this.alias = alias;
		this.tree = tree;
		this.player = player;
		this.sightRadius = sightRadius;
		this.duration = duration;
	}

	public void perch(final CSimulation game, final War3ID effectId, final float facing, final float height) {
		this.model = game.spawnSpellEffectOnPoint(this.tree.getX(), this.tree.getY(), facing, effectId,
				CEffectType.TARGET, 0);
		this.model.setHeight(height);
		this.player.addFogModifer(game, this, false);
		this.tree.add(game, this);
	}

	@Override
	public void onAdd(final CSimulation game, final CPlayer player) {
		this.endTick = game.getGameTurnTick() + (int) (this.duration / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	public void leave(final CSimulation game) {
		if (!this.gone) {
			this.gone = true;
			this.model.remove();
			this.player.removeFogModifer(game, this);
			if ((this.tree.getBuffs() != null) && this.tree.getBuffs().contains(this)) {
				this.tree.remove(game, this);
			}
		}
	}

	public boolean isGone() {
		return this.gone;
	}

	@Override
	public void update(final CSimulation game, final CPlayer player, final PathingGrid pathingGrid,
			final CPlayerFogOfWar fogOfWar) {
		if (this.gone) {
			return;
		}
		if (((this.duration > 0) && (game.getGameTurnTick() >= this.endTick)) || this.tree.isDead()) {
			leave(game);
			return;
		}
		final float x = this.tree.getX();
		final float y = this.tree.getY();
		final int centerX = pathingGrid.getFogOfWarIndexX(x);
		final int centerY = pathingGrid.getFogOfWarIndexY(y);
		final int cells = (int) (this.sightRadius / CPlayerFogOfWar.GRID_STEP);
		final float radiusSquared = (this.sightRadius * this.sightRadius)
				/ (CPlayerFogOfWar.GRID_STEP * CPlayerFogOfWar.GRID_STEP);
		for (int dy = -cells; dy <= cells; dy++) {
			for (int dx = -cells; dx <= cells; dx++) {
				if (((dx * dx) + (dy * dy)) <= radiusSquared) {
					final int indexX = centerX + dx;
					final int indexY = centerY + dy;
					if ((indexX >= 0) && (indexY >= 0) && (indexX < fogOfWar.getWidth())
							&& (indexY < fogOfWar.getHeight())) {
						fogOfWar.setDetecting(indexX, indexY, CFogState.VISIBLE, ALL_DETECTION_LEVELS);
					}
				}
			}
		}
	}

	@Override
	public int getHandleId() {
		return this.handleId;
	}

	@Override
	public War3ID getAlias() {
		return this.alias;
	}

	@Override
	public void onAdd(final CSimulation game, final CDestructable dest) {
	}

	@Override
	public void onRemove(final CSimulation game, final CDestructable dest) {
		leave(game);
	}

	@Override
	public void onDeath(final CSimulation game, final CDestructable dest) {
		leave(game);
	}

	@Override
	public int getLevel() {
		return 1;
	}

	@Override
	public void setLevel(final int level) {
	}

	@Override
	public boolean isDispellable() {
		return true;
	}

	@Override
	public boolean isTimedLife() {
		return this.duration > 0;
	}

	@Override
	public boolean isPositive() {
		return true;
	}

	@Override
	public boolean isHero() {
		return false;
	}

	@Override
	public boolean isMagic() {
		return true;
	}

	@Override
	public boolean isPhysical() {
		return false;
	}

	@Override
	public boolean isAura() {
		return false;
	}
}
