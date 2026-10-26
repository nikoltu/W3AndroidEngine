package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop;

import com.etheller.warsmash.util.WarsmashConstants;

/**
 * Stock of one item or unit type in a shop: nothing until the start delay (game time) passes, then the initial
 * stock, then one more each replenish interval up to the maximum.
 */
public class SellStockState {
	private int stock;
	private int maxStock;
	private final int replenishIntervalTicks;
	private final int initialStock;
	private boolean started;
	private int nextStockReplenishGameTurnTick = -1;
	private int currentIntervalTicks;

	public SellStockState(final int maxStock, final int stockReplenishIntervalTicks, final int initialStock) {
		this.maxStock = maxStock;
		this.replenishIntervalTicks = stockReplenishIntervalTicks;
		this.initialStock = Math.max(1, initialStock);
	}

	public static SellStockState fromData(final int gameTurnTick, final int maxStock,
			final float replenishIntervalSeconds, final float startDelaySeconds, final int initialStock) {
		final SellStockState state = new SellStockState(maxStock,
				(int) (replenishIntervalSeconds / WarsmashConstants.SIMULATION_STEP_TIME), initialStock);
		state.init(gameTurnTick, (int) (startDelaySeconds / WarsmashConstants.SIMULATION_STEP_TIME));
		return state;
	}

	public void init(final int gameTurnTick, final int stockStartGameTurnTick) {
		this.nextStockReplenishGameTurnTick = stockStartGameTurnTick;
		this.currentIntervalTicks = stockStartGameTurnTick;
		update(gameTurnTick);
	}

	public void update(final int gameTurnTick) {
		if (!this.started) {
			if (gameTurnTick < this.nextStockReplenishGameTurnTick) {
				return;
			}
			this.started = true;
			this.stock = Math.max(this.stock, Math.min(this.initialStock, this.maxStock));
			this.nextStockReplenishGameTurnTick = -1;
		}
		if (this.stock < this.maxStock) {
			if (this.nextStockReplenishGameTurnTick == -1) {
				this.nextStockReplenishGameTurnTick = gameTurnTick + this.replenishIntervalTicks;
				this.currentIntervalTicks = this.replenishIntervalTicks;
			}
			if (gameTurnTick >= this.nextStockReplenishGameTurnTick) {
				this.stock++;
				if (this.stock < this.maxStock) {
					this.nextStockReplenishGameTurnTick = gameTurnTick + this.replenishIntervalTicks;
					this.currentIntervalTicks = this.replenishIntervalTicks;
				}
				else {
					this.nextStockReplenishGameTurnTick = -1;
				}
			}
		}
	}

	/**
	 * Stock set by a script (AddItemToStock, AddUnitToStock) is available at once.
	 */
	public void setStock(final int stock, final int maxStock) {
		this.started = true;
		this.maxStock = maxStock;
		this.stock = Math.min(stock, maxStock);
		this.nextStockReplenishGameTurnTick = -1;
	}

	public int getStock() {
		return this.stock;
	}

	public int getMaxStock() {
		return this.maxStock;
	}

	public int getNextStockReplenishGameTurnTick() {
		return this.nextStockReplenishGameTurnTick;
	}

	public int getCurrentIntervalTicks() {
		return this.currentIntervalTicks;
	}

	public float getCooldownRemaining(final int gameTurnTick) {
		if (this.nextStockReplenishGameTurnTick == -1) {
			return 0;
		}
		return Math.max(0, this.nextStockReplenishGameTurnTick - gameTurnTick) * WarsmashConstants.SIMULATION_STEP_TIME;
	}

	public float getCooldownMax() {
		return this.currentIntervalTicks * WarsmashConstants.SIMULATION_STEP_TIME;
	}

	public boolean chargeStock(final int count) {
		if (this.stock >= count) {
			this.stock -= count;
			return true;
		}
		return false;
	}
}
