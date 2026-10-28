package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.blight;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericNoIconAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityBlight extends AbstractGenericNoIconAbility {

	private boolean createsBlight;
	private float expansionAmount;
	private float areaOfEffect;
	private static final float INITIAL_RADIUS = 64;
	private static final float EXPANSION_MARGIN = 128;
	private float secondsPerBlightExpansion;
	private int startTick;
	private float currentRadius;

	public CAbilityBlight(final int handleId, final War3ID code, final War3ID alias, final boolean createsBlight,
			final float expansionAmount, final float areaOfEffect, final float gameSecondsPerBlightExpansion) {
		super(handleId, code, alias);
		this.createsBlight = createsBlight;
		this.expansionAmount = expansionAmount;
		this.areaOfEffect = areaOfEffect;
		setGameSecondsPerBlightExpansion(gameSecondsPerBlightExpansion);
	}

	public void setGameSecondsPerBlightExpansion(final float gameSecondsPerBlightExpansion) {
		this.secondsPerBlightExpansion = gameSecondsPerBlightExpansion;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.startTick = game.getGameTurnTick();
		this.currentRadius = 0;
		if (!isDisabled()) {
			// Measured: buildings that exist when the map starts (melee start, preplaced) have their whole blight at
			// once; only ones created later grow it.
			if (this.startTick == 0) {
				this.currentRadius = this.areaOfEffect + EXPANSION_MARGIN;
				game.setBlight(unit.getX(), unit.getY(), this.currentRadius, this.createsBlight);
			}
			else {
				// Measured on a Necropolis: the first blight is the square under the building, not a circle.
				this.currentRadius = INITIAL_RADIUS;
				game.setBlightSquare(unit.getX(), unit.getY(), INITIAL_RADIUS, this.createsBlight);
			}
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
	}

	/**
	 * Measured on the original game (Necropolis): the blight starts at once 64 around the building, then grows by the
	 * expansion amount every expansion interval, reaching 128 beyond the ability's area.
	 */
	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (isDisabled()) {
			return;
		}
		final float elapsed = (game.getGameTurnTick() - this.startTick) * WarsmashConstants.SIMULATION_STEP_TIME;
		final int expansions = this.secondsPerBlightExpansion > 0
				? (int) Math.floor(elapsed / this.secondsPerBlightExpansion) + 1
				: Integer.MAX_VALUE;
		final float area = Math.min(this.areaOfEffect, expansions * this.expansionAmount);
		final float radius = area > 0 ? area + EXPANSION_MARGIN : INITIAL_RADIUS;
		if (radius > this.currentRadius) {
			this.currentRadius = radius;
			game.setBlight(unit.getX(), unit.getY(), radius, this.createsBlight);
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return null;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		return null;
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.notAnActiveAbility();
	}

	public void setCreatesBlight(final boolean createsBlight) {
		this.createsBlight = createsBlight;
	}

	public void setExpansionAmount(final float expansionAmount) {
		this.expansionAmount = expansionAmount;
	}

	public void setAreaOfEffect(final float areaOfEffect) {
		this.areaOfEffect = areaOfEffect;
	}

}
