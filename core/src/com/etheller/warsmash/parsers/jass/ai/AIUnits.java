package com.etheller.warsmash.parsers.jass.ai;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitTypeRequirement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityRepair;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityAcolyteHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * What the computer player asks of a single unit regardless of whose it is: its abilities, what it is doing, its
 * production queue; and the plane geometry the AI places things with.
 */
final class AIUnits {
	private AIUnits() {
	}

	static boolean isWorkerType(final CUnit unit) {
		return unit.getClassifications().contains(CUnitClassification.PEON);
	}

	static boolean canHarvestGold(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CAbilityAcolyteHarvest) || (ability instanceof CAbilityWispHarvest)) {
				return true;
			}
			if ((ability instanceof CAbilityHarvest) && (((CAbilityHarvest) ability).getGoldCapacity() > 0)) {
				return true;
			}
		}
		return false;
	}

	static boolean canHarvestLumber(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CAbilityWispHarvest) {
				return true;
			}
			if ((ability instanceof CAbilityHarvest) && (((CAbilityHarvest) ability).getLumberCapacity() > 0)) {
				return true;
			}
		}
		return false;
	}

	static int repairOrderId(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CAbilityRepair) {
				return ((CAbilityRepair) ability).getBaseOrderId();
			}
		}
		return OrderIds.repair;
	}

	static boolean minesFromOverlay(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CAbilityAcolyteHarvest) || (ability instanceof CAbilityWispHarvest)) {
				return true;
			}
		}
		return false;
	}

	static AbstractCAbilityBuild getBuildAbility(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof AbstractCAbilityBuild) {
				return (AbstractCAbilityBuild) ability;
			}
		}
		return null;
	}

	static boolean isIdle(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior == null) || (behavior == unit.getStopBehavior()) || (behavior instanceof CBehaviorHoldPosition);
	}

	/**
	 * On its way with the AI's own move order, which walks past enemies without taking them on.
	 */
	static boolean isAIMoving(final CUnit unit) {
		return !isIdle(unit) && (unit.getCurrentOrder() != null)
				&& (unit.getCurrentOrder().getOrderId() == OrderIds.AImove);
	}

	static boolean isBuilding(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior != null) && (behavior.getBehaviorCategory() == CBehaviorCategory.BUILD);
	}

	static boolean isReady(final CUnit unit) {
		return !unit.isConstructing() && !unit.isUpgrading() && !unit.isHidden();
	}

	static int queueLength(final CUnit unit) {
		int count = 0;
		for (final War3ID item : unit.getBuildQueue()) {
			if (item != null) {
				count++;
			}
		}
		return count;
	}

	static boolean requirementsUnlocked(final CPlayer player, final List<CUnitTypeRequirement> requirements) {
		for (final CUnitTypeRequirement requirement : requirements) {
			if (!player.isRequirementMet(requirement.getRequirement(), requirement.getRequiredLevel())) {
				return false;
			}
		}
		return true;
	}

	static float distance(final float x1, final float y1, final float x2, final float y2) {
		final float dx = x1 - x2;
		final float dy = y1 - y2;
		return (float) Math.sqrt((dx * dx) + (dy * dy));
	}

	static float distanceToSegment(final float px, final float py, final float ax, final float ay, final float bx,
			final float by) {
		final float dx = bx - ax;
		final float dy = by - ay;
		final float lengthSquared = (dx * dx) + (dy * dy);
		float t = lengthSquared == 0 ? 0 : (((px - ax) * dx) + ((py - ay) * dy)) / lengthSquared;
		t = Math.max(0, Math.min(1, t));
		return distance(px, py, ax + (t * dx), ay + (t * dy));
	}
}
