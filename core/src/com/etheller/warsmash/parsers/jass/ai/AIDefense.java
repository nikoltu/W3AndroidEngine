package com.etheller.warsmash.parsers.jass.ai;

import static com.etheller.warsmash.parsers.jass.ai.AIUnits.distance;

import java.util.List;
import java.util.Map;

import com.etheller.warsmash.parsers.jass.ai.AIController.AIGuardPost;
import com.etheller.warsmash.parsers.jass.ai.AIController.AIReplacement;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory.CBehaviorGetItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Units that stay home: guard posts and guard positions, the defense captain and the threats to our towns.
 */
final class AIDefense {
	private static final float THREAT_RADIUS = 1300f;
	private static final float GUARD_RETURN_DISTANCE = 100f;
	private static final float HOME_ITEM_RANGE = 1000f;
	private static final float PATH_ITEM_RANGE = 400f;

	private AIDefense() {
	}

	private static AIGuardPost nearestFreePost(final AIController ai, final CUnit unit) {
		AIGuardPost best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final AIGuardPost post : ai.guardPosts) {
			if (post.typeId.equals(unit.getTypeId()) && ((post.unit == null) || post.unit.isDead())) {
				final float d = distance(unit.getX(), unit.getY(), post.x, post.y);
				if (d < bestDistance) {
					bestDistance = d;
					best = post;
				}
			}
		}
		return best;
	}

	private static boolean isTimedLife(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CBuffTimedLife) {
				return true;
			}
		}
		return false;
	}

	static void leavePost(final AIController ai, final CUnit unit) {
		for (final AIGuardPost post : ai.guardPosts) {
			if (post.unit == unit) {
				post.unit = null;
			}
		}
	}

	/**
	 * Measured: units the player has when the AI starts keep their place as their guard position. Later ones do the
	 * same until the script has called StopGathering (InitAI does); after that they go at once to a free guard post
	 * of their type, nearest first, and the rest join the defense captain at its home.
	 */
	static void placeNewUnits(final AIController ai) {
		ai.guardPositions.keySet().removeIf((unit) -> unit.isDead());
		updateReplacements(ai);
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!ai.isGuardCandidate(unit) || ai.guardPositions.containsKey(unit)) {
				continue;
			}
			ai.guardPositions.put(unit, new float[] { unit.getX(), unit.getY() });
			if (!ai.unitsSeen && (unit.getHeroData() != null)) {
				ai.ignoredHeroes.add(unit);
			}
			if (!ai.unitsSeen && (unit.getHeroData() == null)) {
				ai.replacements.add(new AIReplacement(unit));
			}
			if (ai.unitsSeen && takeReplacement(ai, unit)) {
				continue;
			}
			if (!ai.unitsSeen || !ai.gatheringStopped || ai.attackCaptain.getMembers().contains(unit)) {
				continue;
			}
			if (isTimedLife(unit)) {
				// Measured: a summoned unit that appears later joins the attack group on its way, or, without
				// GroupTimedLife, stays where it is and counts as ignored.
				if (!ai.groupTimedLife) {
					ai.ignoredUnits.add(unit);
					continue;
				}
				if (ai.attackCaptain.hasGoal()) {
					ai.attackCaptain.getMembers().add(unit);
					continue;
				}
			}
			final AIGuardPost post = nearestFreePost(ai, unit);
			if (post != null) {
				post.unit = unit;
				ai.guardPositions.put(unit, new float[] { post.x, post.y });
				unit.order(ai.simulation, OrderIds.attack, new AbilityPointTarget(post.x, post.y));
			}
			else if (ai.isArmyUnit(unit)) {
				joinDefense(ai, unit);
			}
		}
		ai.unitsSeen = true;
		produceReplacements(ai);
	}

	/**
	 * Measured: a unit the player had when the AI started is trained again as soon as it dies, as many times as the
	 * replacement count allows (three unless the script says otherwise), and the new one walks to its place.
	 * Structures are not rebuilt this way.
	 */
	private static void updateReplacements(final AIController ai) {
		for (final AIReplacement replacement : ai.replacements) {
			if (!replacement.pending && (replacement.unit != null) && replacement.unit.isDead()) {
				replacement.unit = null;
				if (replacement.used < ai.replacementCount) {
					replacement.used++;
					replacement.pending = true;
				}
			}
		}
	}

	private static void produceReplacements(final AIController ai) {
		for (final AIReplacement replacement : ai.replacements) {
			if (replacement.pending && !isInProduction(ai, replacement.typeId)) {
				AIProduction.setProduce(ai, 1, replacement.typeId.getValue(), -1);
			}
		}
	}

	private static boolean isInProduction(final AIController ai, final War3ID typeId) {
		return AIProduction.countUnits(ai, ai.playerIndex, typeId, false, -1) > AIProduction.countUnits(ai,
				ai.playerIndex, typeId, true, -1);
	}

	private static boolean takeReplacement(final AIController ai, final CUnit unit) {
		for (final AIReplacement replacement : ai.replacements) {
			if (replacement.pending && replacement.typeId.equals(unit.getTypeId())) {
				replacement.pending = false;
				replacement.unit = unit;
				ai.guardPositions.put(unit, new float[] { replacement.x, replacement.y });
				unit.order(ai.simulation, OrderIds.attack, new AbilityPointTarget(replacement.x, replacement.y));
				return true;
			}
		}
		return false;
	}

	/**
	 * Measured: every unit outside the captains that has wandered off walks back to its guard position, attack-moving,
	 * even if a script had ordered it away; fighting units finish first.
	 */
	static void returnGuardPosts(final AIController ai) {
		for (final Map.Entry<CUnit, float[]> entry : ai.guardPositions.entrySet()) {
			final CUnit unit = entry.getKey();
			final float[] position = entry.getValue();
			if (unit.isDead() || unit.isHidden() || ai.attackCaptain.getMembers().contains(unit)
					|| ai.defenseCaptain.getMembers().contains(unit)
					|| (unit.getCurrentBehavior() instanceof CBehaviorAttack)
					|| (distance(unit.getX(), unit.getY(), position[0], position[1]) < GUARD_RETURN_DISTANCE)) {
				continue;
			}
			unit.order(ai.simulation, OrderIds.attack, new AbilityPointTarget(position[0], position[1]));
		}
	}

	/**
	 * Measured: a defender leaves its guard post or place for the defense home of that moment; a later SetCaptainHome
	 * does not move it.
	 */
	private static void joinDefense(final AIController ai, final CUnit unit) {
		leavePost(ai, unit);
		ai.defenseCaptain.getMembers().add(unit);
		final float x = ai.defenseCaptain.getHomeX();
		final float y = ai.defenseCaptain.getHomeY();
		ai.guardPositions.put(unit, new float[] { x, y });
		unit.order(ai.simulation, OrderIds.AImove, new AbilityPointTarget(x, y));
	}

	/**
	 * Measured: AddDefenders claims units at once, also ones the player had from the start, and is false without one.
	 */
	static boolean addDefenders(final AIController ai, final int qty, final int typeIdValue) {
		final War3ID typeId = new War3ID(typeIdValue);
		final List<CUnit> members = ai.defenseCaptain.getMembers();
		int got = 0;
		for (final CUnit unit : members) {
			if (!unit.isDead() && unit.getTypeId().equals(typeId)) {
				got++;
			}
		}
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (got >= qty) {
				break;
			}
			if (unit.getTypeId().equals(typeId) && ai.isArmyUnit(unit) && !unit.isConstructing()
					&& !members.contains(unit) && !ai.attackCaptain.getMembers().contains(unit)) {
				joinDefense(ai, unit);
				got++;
			}
		}
		return got >= qty;
	}

	/**
	 * Measured: any visible hostile unit near a town is a threat, creeps too, idle or fighting our allies; paused
	 * units are not. Fights further out are left alone.
	 */
	private static CUnit findThreat(final AIController ai) {
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final AITown town : ai.towns) {
			if (town.getHall() == null) {
				continue;
			}
			final float x = town.getCenterX();
			final float y = town.getCenterY();
			final CUnit[] found = new CUnit[1];
			final float[] foundDistance = { THREAT_RADIUS };
			ai.simulation.getWorldCollision().enumUnitsInRange(x, y, THREAT_RADIUS, (unit) -> {
				if (ai.isEnemy(unit) && !unit.isPaused() && unit.isVisible(ai.simulation, ai.playerIndex)) {
					final float d = distance(x, y, unit.getX(), unit.getY());
					if (d < foundDistance[0]) {
						foundDistance[0] = d;
						found[0] = unit;
					}
				}
				return false;
			});
			if ((found[0] != null) && (foundDistance[0] < bestDistance)) {
				bestDistance = foundDistance[0];
				best = found[0];
			}
		}
		return best;
	}

	static void updateDefense(final AIController ai) {
		ai.threat = findThreat(ai);
		final boolean attackIdle = !ai.attackCaptain.hasGoal() && !ai.attackCaptain.isRetreating();
		final List<CUnit> defenders = ai.defenseCaptain.getMembers();
		defenders.removeIf((unit) -> !ai.isArmyUnit(unit));
		if (ai.threat != null) {
			// Measured: the defenders attack-move halfway from their home to the threat, so they take on what comes
			// within reach there and leave idle creeps further out alone. Ones still on their way to the defense
			// home go to that point with the AI move instead and attack-move once they stand.
			final AbilityPointTarget target = new AbilityPointTarget(
					(ai.defenseCaptain.getHomeX() + ai.threat.getX()) / 2,
					(ai.defenseCaptain.getHomeY() + ai.threat.getY()) / 2);
			for (final CUnit unit : defenders) {
				if (AIUnits.isAIMoving(unit)) {
					redirectAIMove(ai, unit, target);
				}
				else if (!(unit.getCurrentBehavior() instanceof CBehaviorAttack)) {
					unit.order(ai.simulation, OrderIds.attack, target);
				}
			}
			if (attackIdle) {
				for (final CUnit unit : ai.attackCaptain.getMembers()) {
					if (!(unit.getCurrentBehavior() instanceof CBehaviorAttack)) {
						unit.order(ai.simulation, OrderIds.attack, target);
					}
				}
			}
			return;
		}
		for (final CUnit unit : defenders) {
			final float[] place = ai.guardPositions.get(unit);
			if ((place != null) && AIUnits.isIdle(unit)
					&& (distance(unit.getX(), unit.getY(), place[0], place[1]) > GUARD_RETURN_DISTANCE)) {
				// Measured: back home with the AI move, walking past enemies.
				unit.order(ai.simulation, OrderIds.AImove, new AbilityPointTarget(place[0], place[1]));
			}
		}
	}

	private static void redirectAIMove(final AIController ai, final CUnit unit, final AbilityPointTarget target) {
		final AbilityTarget current = unit.getCurrentOrder().getTarget(ai.simulation);
		if ((current == null) || (distance(current.getX(), current.getY(), target.x, target.y) > 1f)) {
			unit.order(ai.simulation, OrderIds.AImove, target);
		}
	}

	/**
	 * Measured: with SetHeroesTakeItems a hero at home fetches the items lying around its place, up to about a
	 * thousand away, and on the march picks up those close to its way; without it items are left alone.
	 */
	static void takeItems(final AIController ai) {
		for (final CUnit hero : ai.simulation.getUnits()) {
			if (!ai.isOurs(hero) || hero.isHidden() || (hero.getHeroData() == null)
					|| (hero.getCurrentBehavior() instanceof CBehaviorAttack)
					|| (hero.getCurrentBehavior() instanceof CBehaviorGetItem) || !hasFreeSlot(hero)) {
				continue;
			}
			final boolean marching = ai.attackCaptain.getMembers().contains(hero) && ai.attackCaptain.hasGoal();
			final float[] place = ai.guardPositions.get(hero);
			final float x = marching || (place == null) ? hero.getX() : place[0];
			final float y = marching || (place == null) ? hero.getY() : place[1];
			final float range = marching ? PATH_ITEM_RANGE : HOME_ITEM_RANGE;
			CItem best = null;
			float bestDistance = range;
			for (final CItem item : ai.simulation.getItems()) {
				if (item.isDead() || item.isHidden() || (item.getContainedUnit() != null)) {
					continue;
				}
				final float d = distance(x, y, item.getX(), item.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = item;
				}
			}
			if (best != null) {
				hero.order(ai.simulation, OrderIds.getitem, best);
			}
		}
	}

	private static boolean hasFreeSlot(final CUnit hero) {
		final CAbilityInventory inventory = hero.getInventoryData();
		if (inventory == null) {
			return false;
		}
		for (int i = 0; i < inventory.getItemCapacity(); i++) {
			if (inventory.getItemInSlot(i) == null) {
				return true;
			}
		}
		return false;
	}
}
