package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.CreepCamp;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * How a unit reacts to being attacked or targeted, guards its post, and how creeps call for help.
 */
final class CUnitAggro {
	private CUnitAggro() {
	}

	/**
	 * Measured on 1.29: a guard that fought and lost its target (removed, dead) walks back
	 * a moment later and stops 48 short of its spot, still fighting back when hit on the way. A guard that has been
	 * fighting beyond GuardDistance gives up about GuardReturnTime + 1.5 s after crossing it, unless it was hit within
	 * the last GuardReturnTime while not beyond MaxGuardDistance; it then walks to the spot itself, ignoring attacks.
	 * MaxGuardDistance does not cut a chase short. Guards that never fought (woken, moved) stay where they are.
	 */
	static void updateGuard(final CUnit self, final CSimulation game) {
		if ((self.moveBehavior == null) || self.isMovementDisabled()) {
			return;
		}
		final CBehavior behavior = self.orders.currentBehavior;
		if (self.aggro.guardReturn != CUnit.GUARD_RETURN_NONE) {
			if ((behavior == self.moveBehavior) && (self.moveBehavior.getRangedBehavior() == null)) {
				if (self.aggro.guardReturn == CUnit.GUARD_RETURN_LOST) {
					final double fromHome = distanceFromGuardSpot(self);
					if (fromHome <= CUnit.GUARD_LOST_STOP) {
						// The last step ends GUARD_LOST_STOP out, as in the original; the quarter keeps float rounding
						// from landing just inside it.
						final float keep = (float) ((CUnit.GUARD_LOST_STOP + 0.25f) / Math.max(fromHome, 1.0));
						self.setPoint(self.aggro.guardX + ((self.getX() - self.aggro.guardX) * keep),
								self.aggro.guardY + ((self.getY() - self.aggro.guardY) * keep), game.getWorldCollision(),
								game.getRegionManager());
						self.aggro.guardReturn = CUnit.GUARD_RETURN_NONE;
						self.performDefaultBehavior(game);
					}
				}
				return;
			}
			self.aggro.guardReturn = CUnit.GUARD_RETURN_NONE;
		}
		final int tick = game.getGameTurnTick();
		final CGameplayConstants constants = game.getGameplayConstants();
		final boolean fighting = (behavior instanceof CBehaviorAttack) || ((behavior == self.moveBehavior)
				&& (self.moveBehavior.getRangedBehavior() instanceof CBehaviorAttack));
		if (fighting) {
			self.aggro.guardEngaged = true;
			final double fromHome = distanceFromGuardSpot(self);
			if (self.aggro.guardAwayTick < 0) {
				if (fromHome > constants.getGuardDistance()) {
					self.aggro.guardAwayTick = tick;
				}
			}
			else if (((tick - self.aggro.guardAwayTick) >= CUnit.secondsToTicks(
					constants.getGuardReturnTime() + CUnit.GUARD_GIVE_UP_EXTRA_SECONDS))
					&& ((fromHome > constants.getMaxGuardDistance())
							|| ((tick - self.aggro.lastHurtTick) >= CUnit.secondsToTicks(constants.getGuardReturnTime())))) {
				walkToGuardSpot(self, game, CUnit.GUARD_RETURN_GIVE_UP, self.aggro.guardX, self.aggro.guardY);
			}
			return;
		}
		self.aggro.guardAwayTick = -1;
		if (!self.aggro.guardEngaged || !isIdleForAttack(self)) {
			return;
		}
		self.aggro.guardEngaged = false;
		if (distanceFromGuardSpot(self) > CUnit.GUARD_LOST_STOP) {
			walkToGuardSpot(self, game, CUnit.GUARD_RETURN_LOST, self.aggro.guardX, self.aggro.guardY);
		}
	}

	/**
	 * Measured on 1.29: every unit that stands idle remembers the spot, and a fight it started by itself (it saw an
	 * enemy or was hit) ends like a guard's: it goes back when it loses its target and gives up a chase on the same
	 * limits. A fight it was ordered into has no limit.
	 */
	static void updatePost(final CUnit self, final CSimulation game) {
		if ((self.moveBehavior == null) || self.isMovementDisabled()) {
			return;
		}
		if (!self.aggro.postFight && (self.aggro.guardReturn == CUnit.GUARD_RETURN_NONE)) {
			if ((self.orders.currentBehavior == self.orders.defaultBehavior) && (self.orders.defaultBehavior != null)
					&& (self.orders.defaultBehavior != self.holdPositionBehavior)) {
				self.aggro.postSet = true;
				self.aggro.guardX = self.getX();
				self.aggro.guardY = self.getY();
				self.aggro.guardEngaged = false;
				self.aggro.guardAwayTick = -1;
			}
			return;
		}
		if (self.aggro.postSet) {
			updateGuard(self, game);
		}
	}

	/** A fight a unit that stood idle took up by itself; it is left when the unit is ordered. */
	static void beginPostFight(final CUnit self, final boolean fromIdle) {
		if (fromIdle && self.aggro.postSet && !self.aggro.guarding
				&& (self.orders.defaultBehavior != self.holdPositionBehavior)) {
			self.aggro.postFight = true;
		}
	}

	static double distanceFromGuardSpot(final CUnit self) {
		return StrictMath.sqrt(self.distanceSquaredNoCollision(self.aggro.guardX, self.aggro.guardY));
	}

	static void walkToGuardSpot(final CUnit self, final CSimulation game, final int kind, final float x, final float y) {
		self.aggro.guardEngaged = false;
		self.aggro.guardAwayTick = -1;
		self.aggro.postFight = false;
		self.beginBehavior(game, self.moveBehavior.reset(OrderIds.move, new AbilityPointTarget(x, y)), true);
		self.aggro.guardReturn = kind;
	}

	static boolean isReturningFromLostTarget(final CUnit self) {
		return (self.aggro.guardReturn == CUnit.GUARD_RETURN_LOST) && (self.orders.currentBehavior == self.moveBehavior)
				&& (self.moveBehavior.getRangedBehavior() == null);
	}

	static boolean isIdleForAttack(final CUnit self) {
		return (self.orders.currentBehavior == null)
				|| ((self.orders.currentBehavior == self.orders.defaultBehavior) && self.orders.currentBehavior.interruptable());
	}

	/**
	 * Measured on 1.29: a unit hit by a source that the same script call removes or kills
	 * never answers it; a sleeping creep woken by the hit is asleep again at once and does not move. So the answer
	 * (attack back, alert, flee) waits for the unit's own update and is dropped if the source is gone by then.
	 */
	static final float FLEE_DISTANCE = 300f;

	static final float CRITTER_FLEE_DISTANCE = 500f;

	static final int HEAVIEST_HIT_MEMORY_TICKS = CTimer.wholeSteps(2.0f);

	static void noteHit(final CUnit self, final int tick, final CUnit source, final float damage) {
		if (((tick - self.aggro.heaviestHitTick) > HEAVIEST_HIT_MEMORY_TICKS) || (damage >= self.aggro.heaviestHitDamage)) {
			self.aggro.heaviestHitBy = source;
			self.aggro.heaviestHitDamage = damage;
			self.aggro.heaviestHitTick = tick;
		}
	}

	static void queueAttackBack(final CUnit self, final CSimulation simulation, final CUnit source, final boolean wasHit,
			final boolean help) {
		// 1.29: a unit hit after a neighbour called it for help answers its own hit (runs). A critter also leaves
		// its own hit for a call that comes after it, but keeps the first of two calls.
		if ((source != null) && ((self.aggro.pendingAttackBackSource == null)
				|| (wasHit && !self.aggro.pendingAttackBackWasHit && self.aggro.pendingAttackBackHelp)
				|| (isCritter(self) && (wasHit || !self.aggro.pendingAttackBackHelp)))) {
			self.aggro.pendingAttackBackSource = source;
			self.aggro.pendingAttackBackWasHit = wasHit;
			self.aggro.pendingAttackBackHelp = help;
		}
	}

	/**
	 * Measured on 1.29 (creeps and player units): a unit that will strike back acquires its attacker as the hit lands,
	 * before the damage triggers run, while the answer itself still waits for the unit's update.
	 */
	static void acquireAttackerOnHit(final CUnit self, final CSimulation simulation, final CUnit source, final float damage) {
		if ((source != null) && (source != self) && (damage > 0)
				&& !((self.aggro.pendingAttackBackSource != null) && self.aggro.pendingAttackBackWasHit)
				&& (isIdleForAttack(self) || isReturningFromLostTarget(self))
				&& (attackBackAttack(self, simulation, source) != null)) {
			self.fireAcquiredTargetEvents(simulation, source);
		}
	}

	static void resolveAttackBack(final CUnit self, final CSimulation simulation) {
		CUnit source = self.aggro.pendingAttackBackSource;
		final boolean wasHit = self.aggro.pendingAttackBackWasHit;
		self.aggro.pendingAttackBackSource = null;
		final boolean critter = isCritter(self);
		if (source.isDead() || source.isHidden() || source.isRemovedFromGame()
				|| !(isIdleForAttack(self) || isReturningFromLostTarget(self) || (critter && isFleeing(self)))) {
			return;
		}
		final CUnit attacker = source;
		final boolean help = self.aggro.pendingAttackBackHelp;
		if (help) {
			source = nearestHelpTarget(self, simulation, source);
		}
		final boolean foundMatchingReturnFireAttack = attackBack(self, simulation, source);
		if (wasHit && foundMatchingReturnFireAttack) {
			alertCreeps(self, simulation, source);
		}
		// Measured on 1.29 (creeps and player units): a unit called for help acquires its target like one that found
		// it itself.
		else if (help && foundMatchingReturnFireAttack) {
			self.fireAcquiredTargetEvents(simulation, source);
		}
		// Measured on 1.29: a critter called for help by another runs too, away from the caller's attacker.
		if (!foundMatchingReturnFireAttack && (wasHit || critter)) {
			flee(self, simulation, attacker, critter ? CRITTER_FLEE_DISTANCE : FLEE_DISTANCE);
		}
	}

	static void flee(final CUnit self, final CSimulation simulation, final CUnit source, final float distance) {
		if (self.unitType.isCanFlee() && !self.isMovementDisabled()
				&& (self.moveBehavior != null) && (self.playerIndex != source.getPlayerIndex())
				&& !holdsBackOnlyForPassive(self, simulation, source)
				// Measured on 1.29: a unit holding position stays when it cannot hit back.
				&& (self.orders.defaultBehavior != self.holdPositionBehavior)) {
			// Measured on 1.29: the run is a real move order (order events fire, the current order is move) to a
			// point 300 (a critter's 500) straight away from the source whatever the unit's speed; like any move it
			// ends a little short of the point.
			final double angleTo = source.angleTo(self);
			final double cos = StrictMath.cos(angleTo);
			final double sin = StrictMath.sin(angleTo);
			final AbilityPointTarget fleePoint = new AbilityPointTarget((float) (self.getX() + (distance * cos)),
					(float) (self.getY() + (distance * sin)));
			if (!self.order(simulation, OrderIds.move, fleePoint)) {
				self.beginBehavior(simulation, self.moveBehavior.reset(OrderIds.move, fleePoint), true);
			} else if (self.orders.currentBehavior == self.moveBehavior) {
				self.moveBehavior.reset(OrderIds.move, fleePoint);
			}
			if (self.orders.currentBehavior == self.moveBehavior) {
				self.aggro.fleeMoveResetCount = self.moveBehavior.getResetCount();
			}
		}
	}

	static boolean isFleeing(final CUnit self) {
		return (self.orders.currentBehavior == self.moveBehavior) && (self.moveBehavior.getRangedBehavior() == null)
				&& (self.moveBehavior.getResetCount() == self.aggro.fleeMoveResetCount);
	}

	/**
	 * Measured on 1.29: a critter (a Sheep, of any owner) runs 500 instead of 300, runs when it hears a call for
	 * help, and is the only unit that runs from a source its owner is passive toward.
	 */
	static boolean isCritter(final CUnit self) {
		return self.unitType.getRace() == CUnitRace.CRITTERS;
	}

	/**
	 * 1.29: a unit whose owner is passive toward the source (a Neutral Passive Footman, Peasant or Wisp) stands
	 * still; only a critter (a Sheep) runs.
	 */
	static boolean holdsBackOnlyForPassive(final CUnit self, final CSimulation simulation, final CUnit source) {
		return !isCritter(self)
				&& simulation.getPlayer(self.getPlayerIndex()).hasAlliance(source.getPlayerIndex(), CAllianceType.PASSIVE);
	}

	/**
	 * Measured on 1.29: an idle unit that an enemy's unit-target spell takes effect on (Slow,
	 * Curse, Faerie Fire) answers the caster as if hit, and calls its allies for help; the caster itself does not
	 * attack after its cast. Point and no-target spells draw no answer.
	 */
	static void onTargetedBySpell(final CUnit self, final CSimulation simulation, final CUnit caster) {
		if ((caster == null) || (caster == self) || self.isDead() || simulation.getPlayer(self.playerIndex)
				.hasAlliance(caster.getPlayerIndex(), CAllianceType.PASSIVE)) {
			return;
		}
		self.aggro.pendingSpellAnswerSource = caster;
	}

	/**
	 * A spell that flies to its target is answered when it lands (1.29: an Ensnared Footman never turns to the
	 * Raider). Hardcoded spells launch their missile after the effect event and builder spells before it, so the
	 * launch both withdraws an answer already asked for and blocks the effect event that follows it before the
	 * target's next update.
	 */
	static void onSpellMissileLaunched(final CUnit self, final CUnit caster) {
		if (self.aggro.pendingSpellAnswerSource == caster) {
			self.aggro.pendingSpellAnswerSource = null;
		}
		self.aggro.spellMissileSource = caster;
	}

	static void onSpellEffect(final CUnit self, final CSimulation simulation, final CUnit caster) {
		if (self.aggro.spellMissileSource == caster) {
			return;
		}
		onTargetedBySpell(self, simulation, caster);
	}

	static void resolveSpellAnswer(final CUnit self, final CSimulation simulation) {
		final CUnit caster = self.aggro.pendingSpellAnswerSource;
		self.aggro.pendingSpellAnswerSource = null;
		if (caster.isDead() || caster.isHidden() || caster.isRemovedFromGame()) {
			return;
		}
		if (isIdleForAttack(self) || isReturningFromLostTarget(self)) {
			queueAttackBack(self, simulation, caster, false, false);
		}
		callForHelp(self, simulation, caster);
	}

	static boolean attackBack(final CUnit self, final CSimulation simulation, final CUnit source) {
		final CUnitAttack attack = attackBackAttack(self, simulation, source);
		if (attack == null) {
			return false;
		}
		// 1.29: a unit on hold position answers only an attacker it reaches from where it stands.
		final boolean disableMove = self.movement.moveDisabled || (self.orders.defaultBehavior == self.holdPositionBehavior);
		final CBehaviorAttack attackBehaviorForAtk = self.getAttackBehavior();
		final CBehavior chase = attackBehaviorForAtk.reset(simulation, OrderIds.attack, attack, source, disableMove,
				CBehaviorAttackListener.DO_NOTHING);
		attackBehaviorForAtk.setThroughFog(true);
		final boolean fromIdle = isIdleForAttack(self) || isReturningFromLostTarget(self);
		self.beginBehavior(simulation, chase, true);
		beginPostFight(self, fromIdle);
		return true;
	}

	static CUnitAttack attackBackAttack(final CUnit self, final CSimulation simulation, final CUnit source) {
		if ((source == null)
				|| simulation.getPlayer(self.getPlayerIndex()).hasAlliance(source.getPlayerIndex(), CAllianceType.PASSIVE)
				|| self.classifications.contains(CUnitClassification.PEON)) {
			return null;
		}
		final CAbilityAttack attackAbility = self.getFirstAbilityOfType(CAbilityAttack.class);
		if ((self.getAttackBehavior() == null) || ((attackAbility != null) && attackAbility.isDisabled())) {
			return null;
		}
		for (final CUnitAttack attack : self.getCurrentAttacks()) {
			// 1.29 (creep-wake-sleep r1): an attacker out of sight in the fog is still chased.
			if (source.canBeTargetedBy(simulation, self, true, attack.getTargetsAllowed(),
					BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset(), true)) {
				return attack;
			}
		}
		return null;
	}

	/**
	 * Measured on 1.29: a unit called for help does not go for the attacker that called it but
	 * for the enemy nearest to itself, even one that has not attacked; the attacker when nothing is nearer.
	 */
	static CUnit nearestHelpTarget(final CUnit self, final CSimulation simulation, final CUnit attacker) {
		final float reach = (float) self.distance(attacker);
		final CPlayer owner = simulation.getPlayer(self.playerIndex);
		final CUnit[] nearest = { attacker };
		final double[] nearestDistance = { reach };
		CUnit.callForHelpRect.set(self.getX() - reach, self.getY() - reach, reach * 2, reach * 2);
		simulation.getWorldCollision().enumUnitsInRect(CUnit.callForHelpRect, (unit) -> {
			if ((unit == self) || (unit == attacker) || unit.isDead() || unit.isHidden() || !unit.isAutoTargetable()
					|| unit.isInvulnerable() || unit.isUnitType(CUnitTypeJass.SLEEPING)
					|| owner.hasAlliance(unit.getPlayerIndex(), CAllianceType.PASSIVE)) {
				return false;
			}
			final double unitDistance = self.distance(unit);
			if (unitDistance >= nearestDistance[0]) {
				return false;
			}
			for (final CUnitAttack attack : self.getCurrentAttacks()) {
				if (unit.canBeTargetedBy(simulation, self, attack.getTargetsAllowed())) {
					nearest[0] = unit;
					nearestDistance[0] = unitDistance;
					break;
				}
			}
			return false;
		});
		return nearest[0];
	}

	/**
	 * An attacked unit calls idle helpers within the CallForHelp range (CreepCallForHelp for creeps) to
	 * attack the attacker, at most once a second so a long fight does not search every hit.
	 */
	static void callForHelp(final CUnit self, final CSimulation simulation, final CUnit attacker) {
		// Measured on 1.29: a critter calls the others even when its owner is passive toward the attacker.
		if ((attacker == null) || (simulation.getGameTurnTick() < self.aggro.nextCallForHelpTick)
				|| (!isCritter(self) && simulation.getPlayer(self.playerIndex).hasAlliance(attacker.getPlayerIndex(),
						CAllianceType.PASSIVE))) {
			return;
		}
		self.aggro.nextCallForHelpTick = simulation.getGameTurnTick()
				+ CTimer.wholeSteps(1.0f);
		final CGameplayConstants constants = simulation.getGameplayConstants();
		float radius;
		if (simulation.isNeutralAggressive(self.playerIndex)) {
			radius = constants.getCreepCallForHelp();
		}
		else if (simulation.getPlayer(self.playerIndex).getController() == CMapControl.COMPUTER) {
			radius = constants.getAICallForHelp();
		}
		else {
			radius = constants.getCallForHelp();
		}
		if (radius <= 0) {
			// 1.29 data has no AICallForHelp entry; its computer players are helped like users.
			radius = constants.getCallForHelp();
		}
		summonHelpers(self, simulation, attacker, radius);
	}

	/**
	 * Measured on 1.29: a creep that starts attacking (before any damage) brings its camp along at the same instant,
	 * each one acquiring the same target; every creep so brought in alerts in turn the idle creeps within
	 * {@link #CREEP_ALERT_RADIUS} of itself, and those call nobody. The caller alerts no creep outside its camp: of two
	 * idle creeps 600 to either side of it only the camp mate joins.
	 */
	static void alertCreeps(final CUnit self, final CSimulation simulation, final CUnit target) {
		if ((target == null) || !simulation.isNeutralAggressive(self.playerIndex)) {
			return;
		}
		if (self.aggro.creepCamp == null) {
			// Given to Neutral Hostile after it was made.
			joinCreepCamp(self, simulation);
		}
		final List<CUnit> joined = new ArrayList<>();
		final List<CUnit> members = self.aggro.creepCamp.members;
		for (int i = 0; i < members.size(); i++) {
			final CUnit mate = members.get(i);
			if (mate.isDead() || mate.isRemovedFromGame() || (mate.aggro.creepCamp != self.aggro.creepCamp)) {
				members.remove(i--);
			}
			else if ((mate != self) && (mate.playerIndex == self.playerIndex)) {
				mate.wakeUp(simulation);
				if (mate.answerAlert(simulation, target)) {
					joined.add(mate);
				}
			}
		}
		for (final CUnit mate : joined) {
			for (final CUnit neighbour : mate.helpersInRange(simulation, CUnit.CREEP_ALERT_RADIUS)) {
				neighbour.wakeUp(simulation);
				neighbour.answerAlert(simulation, target);
			}
		}
	}

	static boolean answerAlert(final CUnit self, final CSimulation simulation, final CUnit target) {
		if (self.isDead() || self.isHidden() || target.isDead() || target.isHidden() || target.isRemovedFromGame()
				|| !(isIdleForAttack(self) || isReturningFromLostTarget(self))) {
			return false;
		}
		if (attackBack(self, simulation, target)) {
			self.fireAcquiredTargetEvents(simulation, target);
			return true;
		}
		return false;
	}

	/**
	 * Measured on 1.29: a new creep joins the camp of the nearest camp founder within {@link #CREEP_ALERT_RADIUS}
	 * (counted to its own edge, as for an alert), not of the nearest member, so of three creeps made in a row 600
	 * apart the third starts a camp of its own. A founder that dies or is removed hands the camp to its oldest
	 * living member, and the camp still answers together.
	 */
	static void joinCreepCamp(final CUnit self, final CSimulation simulation) {
		final float collisionSize = self.unitType.getCollisionSize();
		final float reach = CUnit.CREEP_ALERT_RADIUS + collisionSize;
		final CUnit[] founder = { null };
		final double[] founderDistance = { Double.MAX_VALUE };
		CUnit.callForHelpRect.set(self.getX() - reach, self.getY() - reach, reach * 2, reach * 2);
		simulation.getWorldCollision().enumUnitsInRect(CUnit.callForHelpRect, (unit) -> {
			if ((unit != self) && (unit.playerIndex == self.playerIndex) && (unit.aggro.creepCamp != null)
					&& (unit.aggro.creepCamp.founder() == unit)) {
				final double unitDistance = self.distance(unit.getX(), unit.getY());
				if ((unitDistance <= CUnit.CREEP_ALERT_RADIUS) && ((unitDistance < founderDistance[0])
						|| ((unitDistance == founderDistance[0]) && (unit.getHandleId() < founder[0].getHandleId())))) {
					founder[0] = unit;
					founderDistance[0] = unitDistance;
				}
			}
			return false;
		});
		self.aggro.creepCamp = (founder[0] != null) ? founder[0].aggro.creepCamp : new CreepCamp();
		self.aggro.creepCamp.members.add(self);
	}

	/**
	 * Idle units of this owner, and of players this owner asks for help who answer it, join against the attacker.
	 */
	static void summonHelpers(final CUnit self, final CSimulation simulation, final CUnit attacker, final float radius) {
		for (final CUnit helper : helpersInRange(self, simulation, radius)) {
			helper.wakeUp(simulation);
			if (helper.isIdleForAttack() || (helper.isFleeing() && helper.isCritter())) {
				helper.queueAttackBack(simulation, attacker, false, true);
			}
		}
	}

	/**
	 * Living, shown units of this owner, and of players this owner asks for help who answer it, within the radius. The
	 * reach counts to the helper's edge, as the original's thresholds sit one Footman radius past the constant.
	 */
	static List<CUnit> helpersInRange(final CUnit self, final CSimulation simulation, final float radius) {
		final CPlayer owner = simulation.getPlayer(self.playerIndex);
		final List<CUnit> helpers = new ArrayList<>();
		CUnit.callForHelpRect.set(self.getX() - radius, self.getY() - radius, radius * 2, radius * 2);
		simulation.getWorldCollision().enumUnitsInRect(CUnit.callForHelpRect, (helper) -> {
			if ((helper != self) && !helper.isDead() && !helper.isHidden()
					&& ((helper.playerIndex == self.playerIndex)
							|| (owner.hasAlliance(helper.playerIndex, CAllianceType.HELP_REQUEST)
									&& simulation.getPlayer(helper.playerIndex).hasAlliance(self.playerIndex,
											CAllianceType.HELP_RESPONSE)))
					&& ((helper.distance(self.getX(), self.getY()) - helper.unitType.getCollisionSize()) <= radius)) {
				helpers.add(helper);
			}
			return false;
		});
		return helpers;
	}
}
