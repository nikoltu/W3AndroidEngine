package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.warden;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CAbilityBlink extends CAbilityPointTargetSpellBase {
	private static final float BEYOND_RANGE_FACTOR = 0.8f;

	private float minimumRange;
	private float maximumRange;

	public CAbilityBlink(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.blink;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		maximumRange = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		minimumRange = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityPointTarget target, final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		if (unit.distance(target) < minimumRange) {
			receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_IS_INSIDE_MINIMUM_RANGE);
		}
		else {
			final AbilityPointTarget point = blinkPoint(game, unit, target);
			if (point == null) {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THERE);
			}
			else {
				super.innerCheckCanTarget(game, unit, orderId, point, receiver);
			}
		}
	}

	/**
	 * Measured (1.29): a point beyond the maximum range is taken at 4/5 of that range in its direction, and a point
	 * on ground the caster cannot stand on (deep water, cliffs) is taken at the nearest place it can; the spell events
	 * see that point. A point blocked only by a destructable (trees) cannot be targeted: null.
	 */
	private AbilityPointTarget blinkPoint(final CSimulation game, final CUnit unit, final AbilityPointTarget target) {
		float x = target.getX();
		float y = target.getY();
		if (StrictMath.hypot(x - unit.getX(), y - unit.getY()) > this.maximumRange) {
			final double angleTo = unit.angleTo(target);
			final float distance = this.maximumRange * BEYOND_RANGE_FACTOR;
			x = unit.getX() + (float) (StrictMath.cos(angleTo) * distance);
			y = unit.getY() + (float) (StrictMath.sin(angleTo) * distance);
		}
		final PathingGrid pathingGrid = game.getPathingGrid();
		if (!pathingGrid.isPathable(x, y, unit.getMovementType())) {
			if (unit.getMovementType().isPathable(pathingGrid.getTerrainPathing(x, y))) {
				return null;
			}
			final float[] free = unit.findFreePoint(x, y, game);
			x = free[0];
			y = free[1];
		}
		if ((x == target.getX()) && (y == target.getY())) {
			return target;
		}
		return new AbilityPointTarget(x, y);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final float casterX = caster.getX();
		final float casterY = caster.getY();
		simulation.spawnSpellEffectOnPoint(casterX, casterY, 0, getAlias(), CEffectType.AREA_EFFECT, 0).remove();
		final AbilityPointTarget point = blinkPoint(simulation, caster,
				new AbilityPointTarget(target.getX(), target.getY()));
		if (point != null) {
			caster.setPointAndCheckUnstuck(point.getX(), point.getY(), simulation);
		}
		else {
			caster.setPointAndCheckUnstuck(target.getX(), target.getY(), simulation);
		}
		simulation.spawnSpellEffectOnPoint(caster.getX(), caster.getY(), 0, getAlias(), CEffectType.SPECIAL, 0)
				.remove();
		return false;
	}
}
