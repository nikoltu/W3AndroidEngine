package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.creeps;

import java.util.EnumSet;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.War3ID;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.demonhunter.CAbilityImmolation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.blademaster.CBuffWhirlWindCaster;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderGenericActive;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * A spell that Neutral Hostile creeps cast by themselves while they fight, like the original game's creeps do.
 * Spells without a class of their own are cast by the rules of {@link #tryCreepCastRule}.
 */
public interface CCreepCastAbility {
	// Measured on 1.29: War Stomp and Slam wait until the creep fights and three enemies have their edge within the
	// area around its center; one, two, or a Footman with a Paladin get none.
	int AREA_CAST_MINIMUM_TARGETS = 3;
	// Measured on 1.29 (Frost Revenant): Blizzard falls on a group of at least two enemies with no hero among them
	// while the creep stays out of the area, like Rain of Fire.
	int GROUP_CAST_MINIMUM_TARGETS = 2;
	// Measured on 1.29: a hero 160 to 250 from the group, out of the area, also keeps the Frost Revenant from casting.
	float GROUP_CAST_HERO_AREA_FACTOR = 2;
	float ATTACKER_SEARCH_RANGE = 1000;
	int HIT_MEMORY_TICKS = CTimer.wholeSteps(2.0f);
	float HEAL_EXTRA_REACH = 300;
	float SELF_HEAL_LIFE_FRACTION = 0.55f;
	// Attacks up to this range count as melee (Murloc Mutant 100, Furbolg 100, Footman 90).
	int MELEE_RANGE = 128;
	// Measured on 1.29: a creep buffs itself (Bloodlust, Inner Fire) only from its third thought in a fight on, other
	// fighting allies already from the second.
	int SELF_BUFF_THINKS = 2;
	// Thinks after which a hero stops waiting for a summon he cannot cast yet.
	int SUMMON_WAIT_THINKS = 2;

	/**
	 * @param attackTarget the unit the creep is attacking
	 * @param inAttackRange whether that unit is within the creep's attack range
	 * @return true when the creep was ordered to cast
	 */
	boolean tryCreepCast(CSimulation game, CUnit creep, CUnit attackTarget, boolean inAttackRange);

	/** Whether a creep casts this spell by itself in a fight. */
	static boolean isCreepSpell(final CAbility ability) {
		return (ability instanceof CCreepCastAbility) || ((ability instanceof GenericSingleIconActiveAbility)
				&& (Rule.of(((GenericSingleIconActiveAbility) ability).getBaseOrderId()) != null));
	}

	/**
	 * Measured on 1.29: a hero waits for his summon (Feral Spirit, Summon Phoenix, Force of Nature) before his
	 * blasts and curses while it is ready, but only for the first seconds of the fight (Chain Lightning came 3 s after
	 * the wolves, Drain only after the Phoenix).
	 */
	static boolean isSummonPending(final CSimulation game, final CUnit hero, final int thinks) {
		if (thinks > SUMMON_WAIT_THINKS) {
			return false;
		}
		for (final CAbility ability : hero.getAbilities()) {
			if (ability.isDisabled() || !(ability instanceof GenericSingleIconActiveAbility)) {
				continue;
			}
			final int orderId = ((GenericSingleIconActiveAbility) ability).getBaseOrderId();
			final Rule rule = Rule.of(orderId);
			if ((rule == Rule.FIGHT) || (rule == Rule.TREE)) {
				final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE.reset();
				ability.checkCanUse(game, hero, hero.getPlayerIndex(), orderId, true, activation);
				if (activation.isOk() && ((rule == Rule.FIGHT) || (findTree(game, hero, ability, orderId) != null))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Measured on 1.29: a hero who was in a fight when Bladestorm began casts his fight spell (Mirror Image) in the
	 * very tick it ends, whether or not an enemy is still near.
	 */
	static boolean tryCastAfterBladestorm(final CSimulation game, final CUnit hero) {
		if (hero.isDead() || hero.isHidden()) {
			return false;
		}
		for (final CAbility ability : hero.getAbilities()) {
			if (ability.isDisabled() || !(ability instanceof GenericSingleIconActiveAbility)) {
				continue;
			}
			final int orderId = ((GenericSingleIconActiveAbility) ability).getBaseOrderId();
			if (Rule.of(orderId) == Rule.FIGHT) {
				final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE.reset();
				ability.checkCanUse(game, hero, hero.getPlayerIndex(), orderId, true, activation);
				if (activation.isOk() && hero.order(game, orderId, null, true)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Measured on 1.29: a hero casts summons and armour spells before blasts and curses. */
	static boolean castsFirst(final CAbility ability) {
		if (!(ability instanceof GenericSingleIconActiveAbility)) {
			return false;
		}
		final Rule rule = Rule.of(((GenericSingleIconActiveAbility) ability).getBaseOrderId());
		return (rule == Rule.FIGHT) || (rule == Rule.TREE) || (rule == Rule.ARMOR);
	}

	/**
	 * Measured on 1.29: buffs (Bloodlust, Inner Fire) go to a fighting ally without them before the creep itself, Heal
	 * to the most hurt ally, Slow and Curse to an enemy attacking the creep's side, and other curses and blasts
	 * (Purge, Firebolt, Frost Nova, Chain Lightning, Shadow Strike, Life Drain) to the nearest enemy in reach; both
	 * kinds of enemy spells pick another enemy than the creep's own attack target when there is one.
	 *
	 * @return true when the creep was ordered to cast
	 */
	static boolean tryCreepCastRule(final CSimulation game, final CUnit creep, final CAbility ability,
			final CUnit attackTarget, final boolean think, final int thinks) {
		if (!(ability instanceof GenericSingleIconActiveAbility)) {
			return false;
		}
		final GenericSingleIconActiveAbility spell = (GenericSingleIconActiveAbility) ability;
		final int orderId = spell.getBaseOrderId();
		Rule rule = Rule.of(orderId);
		// Measured on 1.29: a hero rains Blizzard on an enemy sent against him at once, hero or not, like Inferno, but
		// only once; he did not rain it again in 14 more seconds.
		boolean once = false;
		if ((rule == Rule.GROUP) && (creep.getHeroData() != null)) {
			if (creep.hasCastOnce(orderId)) {
				return false;
			}
			once = true;
			rule = Rule.ENEMY_FIRST;
		}
		// Measured on 1.29: a hero does not burn with Immolation or swarm with Carrion Swarm by himself, and he casts
		// nothing during Bladestorm.
		if ((creep.getHeroData() != null)
				&& ((rule == Rule.TOGGLE_ON) || (rule == Rule.ENEMY_LATE) || isWhirlingAround(creep))) {
			return false;
		}
		if ((rule == null) || (!rule.everyTick && !think) || (thinks < rule.minimumThinks)) {
			return false;
		}
		final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE.reset();
		ability.checkCanUse(game, creep, creep.getPlayerIndex(), orderId, true, activation);
		if (!activation.isOk()) {
			return false;
		}
		final boolean cast = tryRuleCast(game, creep, ability, orderId, rule, attackTarget, thinks);
		if (cast && once) {
			creep.markCastOnce(orderId);
		}
		return cast;
	}

	private static boolean tryRuleCast(final CSimulation game, final CUnit creep, final CAbility ability,
			final int orderId, final Rule rule, final CUnit attackTarget, final int thinks) {
		switch (rule) {
		case AREA: {
			if (!isFighting(game, creep)) {
				return false;
			}
			final float area = getArea(ability);
			final EnumSet<CTargetType> targetsAllowed = getTargetsAllowed(ability);
			final CPlayer player = game.getPlayer(creep.getPlayerIndex());
			final int[] count = { 0 };
			game.getWorldCollision().enumUnitsInRange(creep.getX(), creep.getY(), area + 128, (enumUnit) -> {
				if ((enumUnit != creep) && !enumUnit.isDead() && !enumUnit.isHidden() && !enumUnit.isUnitAlly(player)
						&& ((StrictMath.hypot(enumUnit.getX() - creep.getX(), enumUnit.getY() - creep.getY())
								- enumUnit.getUnitType().getCollisionSize()) <= area)
						&& ((targetsAllowed == null) || enumUnit.canBeTargetedBy(game, creep, targetsAllowed))) {
					count[0]++;
				}
				return false;
			});
			if (count[0] < AREA_CAST_MINIMUM_TARGETS) {
				return false;
			}
			return creep.order(game, orderId, null, true);
		}
		case GROUP:
			return tryGroupCast(game, creep, ability, orderId, attackTarget, getArea(ability));
		case ALLY_BUFF:
		case ALLY_HEAL:
			return tryAllyCast(game, creep, ability, orderId, rule == Rule.ALLY_HEAL, false,
					thinks >= SELF_BUFF_THINKS);
		case TOGGLE_ON: {
			final float area = getArea(ability);
			final CPlayer player = game.getPlayer(creep.getPlayerIndex());
			final boolean[] enemy = { false };
			game.getWorldCollision().enumUnitsInRange(creep.getX(), creep.getY(), area + 128, (enumUnit) -> {
				if (!enumUnit.isDead() && !enumUnit.isHidden() && !enumUnit.isUnitAlly(player)
						&& creep.canReach(enumUnit, area)) {
					enemy[0] = true;
					return true;
				}
				return false;
			});
			return enemy[0] && creep.order(game, orderId, null, true);
		}
		case ENEMY:
		case ENEMY_ATTACKING:
			return tryEnemyCast(game, creep, ability, orderId, attackTarget, rule == Rule.ENEMY_ATTACKING);
		case ENEMY_AIR:
		case ENEMY_RANGED:
		case ENEMY_FAERIE:
		case ENEMY_FIRST:
		case ENEMY_LATE:
			return tryEnemyCast(game, creep, ability, orderId, attackTarget, false, rule);
		case ENEMY_HERO:
			return tryEnemyCast(game, creep, ability, orderId, attackTarget, false, Rule.ENEMY_HERO)
					|| tryEnemyCast(game, creep, ability, orderId, attackTarget, false, Rule.ENEMY);
		case ARMOR:
			return tryAllyCast(game, creep, ability, orderId, false, false, true);
		case SELF_BUFF:
			return canTarget(game, creep, ability, orderId, creep) && creep.order(game, orderId, creep, true);
		case BLADESTORM:
			if (isReadyToCast(game, creep, OrderIds.mirrorimage)) {
				return false;
			}
			// fall through
		case FIGHT:
			// Measured on 1.29: the spell comes when an enemy reaches the caster, not when a ranged caster is already
			// in reach of its own target (Feral Spirit, Summon Phoenix).
			return isFighting(game, creep) && isHitInReach(game, creep) && creep.order(game, orderId, null, true);
		case SLEEP:
			return tryEnemyCast(game, creep, ability, orderId, attackTarget, true, Rule.ENEMY);
		case TREE:
			return isFighting(game, creep) && isHitInReach(game, creep) && tryTreeCast(game, creep, ability, orderId);
		case CORPSE_TARGET:
			return tryCorpseCast(game, creep, ability, orderId);
		case CORPSES:
			creep.order(game, new COrderNoTarget(creep.getPlayerIndex(), ability.getHandleId(), orderId, false, true),
					false, true);
			return true;
		default:
			return false;
		}
	}

	/**
	 * Measured on 1.29: a healer heals a hurt ally as soon as an enemy is sent against that ally, before the fight
	 * reaches the healer itself (Draenei Disciple), and keeps healing without attacking.
	 */
	static boolean tryIdleCreepCast(final CSimulation game, final CUnit creep, final CAbility ability) {
		if (!(ability instanceof GenericSingleIconActiveAbility)) {
			return false;
		}
		final int orderId = ((GenericSingleIconActiveAbility) ability).getBaseOrderId();
		final Rule rule = Rule.of(orderId);
		if ((rule != Rule.ALLY_HEAL) && (rule != Rule.ARMOR) && (rule != Rule.SELF_HEAL)) {
			return false;
		}
		final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE.reset();
		ability.checkCanUse(game, creep, creep.getPlayerIndex(), orderId, true, activation);
		if (!activation.isOk()) {
			return false;
		}
		if (rule == Rule.SELF_HEAL) {
			return trySelfHeal(game, creep, ability, orderId);
		}
		return tryAllyCast(game, creep, ability, orderId, rule == Rule.ALLY_HEAL, true, false);
	}

	/**
	 * Measured on 1.29 (Sasquatch Oracle): Rejuvenation goes on the creep itself at about half its life when an
	 * enemy is sent against it; once the creep fights it is not cast (Blue Dragonspawn Apprentice).
	 */
	static boolean trySelfHeal(final CSimulation game, final CUnit creep, final CAbility ability, final int orderId) {
		if ((creep.getLife() > (creep.getMaximumLife() * SELF_HEAL_LIFE_FRACTION))
				|| !isAttacked(game, creep, game.getPlayer(creep.getPlayerIndex()))) {
			return false;
		}
		return canTarget(game, creep, ability, orderId, creep) && creep.order(game, orderId, creep, true);
	}


	static boolean tryGroupCast(final CSimulation game, final CUnit creep, final CAbility ability, final int orderId,
			final CUnit attackTarget, final float area) {
		if ((attackTarget.getHeroData() != null) || creep.canReach(attackTarget, area)
				|| !creep.canReach(attackTarget, getCastRange(ability))) {
			return false;
		}
		final CPlayer player = game.getPlayer(creep.getPlayerIndex());
		final EnumSet<CTargetType> targetsAllowed = getTargetsAllowed(ability);
		final int[] targets = { 0 };
		final boolean[] hero = { false };
		final float heroArea = area * GROUP_CAST_HERO_AREA_FACTOR;
		game.getWorldCollision().enumUnitsInRange(attackTarget.getX(), attackTarget.getY(), heroArea, (enumUnit) -> {
			if (!enumUnit.isDead() && !enumUnit.isHidden() && !enumUnit.isUnitAlly(player)) {
				if (enumUnit.getHeroData() != null) {
					hero[0] |= enumUnit.canReach(attackTarget, heroArea);
				}
				else if (enumUnit.canReach(attackTarget, area)
						&& ((targetsAllowed == null) || enumUnit.canBeTargetedBy(game, creep, targetsAllowed))) {
					targets[0]++;
				}
			}
			return false;
		});
		if (hero[0] || (targets[0] < GROUP_CAST_MINIMUM_TARGETS)) {
			return false;
		}
		return creep.order(game, orderId, attackTarget, true);
	}

	/**
	 * Measured on 1.29: Force of Nature is aimed at the tree nearest to the caster, like the other summons once an
	 * enemy reaches him.
	 */
	static boolean tryTreeCast(final CSimulation game, final CUnit creep, final CAbility ability, final int orderId) {
		final CDestructable tree = findTree(game, creep, ability, orderId);
		return (tree != null)
				&& creep.order(game, orderId, new AbilityPointTarget(tree.getX(), tree.getY()), true);
	}

	static CDestructable findTree(final CSimulation game, final CUnit creep, final CAbility ability,
			final int orderId) {
		final float range = getCastRange(ability);
		final CDestructable[] best = { null };
		final double[] bestDistance = { 0 };
		game.getWorldCollision().enumDestructablesInRange(creep.getX(), creep.getY(), range, (tree) -> {
			final double distance = tree.distance(creep.getX(), creep.getY());
			if (((best[0] == null) || (distance < bestDistance[0])) && !tree.isDead()) {
				final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> receiver = BooleanAbilityTargetCheckReceiver
						.<AbilityPointTarget>getInstance().reset();
				ability.checkCanTarget(game, creep, creep.getPlayerIndex(), orderId, true,
						new AbilityPointTarget(tree.getX(), tree.getY()), receiver);
				if (receiver.isTargetable()) {
					best[0] = tree;
					bestDistance[0] = distance;
				}
			}
			return false;
		});
		return best[0];
	}

	/**
	 * Measured on 1.29: Carrion Beetles go to the nearest corpse in reach, so they come once something has died in
	 * the fight.
	 */
	static boolean tryCorpseCast(final CSimulation game, final CUnit creep, final CAbility ability,
			final int orderId) {
		final float range = getCastRange(ability);
		final CUnit[] best = { null };
		final double[] bestDistance = { 0 };
		game.getWorldCollision().enumCorpsesInRect(
				new Rectangle(creep.getX() - range, creep.getY() - range, range * 2, range * 2), (corpse) -> {
			if (creep.canReach(corpse, range)) {
				final double distance = creep.distance(corpse);
				if (((best[0] == null) || (distance < bestDistance[0]))
						&& canTarget(game, creep, ability, orderId, corpse)) {
					best[0] = corpse;
					bestDistance[0] = distance;
				}
			}
			return false;
		});
		return (best[0] != null) && creep.order(game, orderId, best[0], true);
	}

	static boolean tryAllyCast(final CSimulation game, final CUnit creep, final CAbility ability, final int orderId,
			final boolean heal, final boolean onlyAttacked, final boolean self) {
		// a healer also walks to a hurt ally a little out of its reach
		final float range = getCastRange(ability) + (heal ? HEAL_EXTRA_REACH : 0);
		final CPlayer player = game.getPlayer(creep.getPlayerIndex());
		final CUnit[] best = { null };
		final double[] bestValue = { 0 };
		game.getWorldCollision().enumUnitsInRange(creep.getX(), creep.getY(), range + 128, (enumUnit) -> {
			if ((enumUnit == creep) || enumUnit.isDead() || enumUnit.isHidden() || !enumUnit.isUnitAlly(player)
					|| !creep.canReach(enumUnit, range)) {
				return false;
			}
			final double value;
			if (heal) {
				if ((enumUnit.getLife() >= enumUnit.getMaximumLife())
						|| (onlyAttacked && !isAttacked(game, enumUnit, player))) {
					return false;
				}
				value = enumUnit.getLife() / enumUnit.getMaximumLife();
			}
			else {
				if (onlyAttacked ? !isAttacked(game, enumUnit, player) : !isFighting(game, enumUnit)) {
					return false;
				}
				value = creep.distance(enumUnit);
			}
			if (((best[0] == null) || (value < bestValue[0])) && canTarget(game, creep, ability, orderId, enumUnit)) {
				best[0] = enumUnit;
				bestValue[0] = value;
			}
			return false;
		});
		CUnit target = best[0];
		if ((target == null) && self
				&& (heal ? (creep.getLife() < creep.getMaximumLife())
						: (isFighting(game, creep) || (Rule.of(orderId) == Rule.ARMOR)))
				&& canTarget(game, creep, ability, orderId, creep)) {
			target = creep;
		}
		return (target != null) && creep.order(game, orderId, target, true);
	}

	static boolean tryEnemyCast(final CSimulation game, final CUnit creep, final CAbility ability, final int orderId,
			final CUnit attackTarget, final boolean onlyAttackers) {
		return tryEnemyCast(game, creep, ability, orderId, attackTarget, onlyAttackers, null);
	}

	static boolean tryEnemyCast(final CSimulation game, final CUnit creep, final CAbility ability, final int orderId,
			final CUnit attackTarget, final boolean onlyAttackers, final Rule only) {
		final float range = getCastRange(ability);
		// Measured on 1.29: a creep that shoots from afar casts Faerie Fire only on flyers, one that fights in melee
		// on anyone.
		final Rule rule = ((only == Rule.ENEMY_FAERIE) && !isMelee(creep)) ? Rule.ENEMY_AIR : only;
		final CPlayer player = game.getPlayer(creep.getPlayerIndex());
		final CUnit[] best = { null };
		final double[] bestDistance = { 0 };
		game.getWorldCollision().enumUnitsInRange(creep.getX(), creep.getY(), range + 128, (enumUnit) -> {
			if ((enumUnit == attackTarget) || enumUnit.isDead() || enumUnit.isHidden() || enumUnit.isUnitAlly(player)
					|| !creep.canReach(enumUnit, range) || (onlyAttackers && !isAttacking(game, enumUnit, player))
					|| !isRuleTarget(rule, enumUnit)) {
				return false;
			}
			final double distance = creep.distance(enumUnit);
			if (((best[0] == null) || (distance < bestDistance[0]))
					&& canTarget(game, creep, ability, orderId, enumUnit)) {
				best[0] = enumUnit;
				bestDistance[0] = distance;
			}
			return false;
		});
		CUnit target = best[0];
		// Measured on 1.29: Flame Strike and Shadow Strike come only once an enemy has hurt the caster while at least
		// two enemies are around, and go to the enemy behind his hardest recent hit (a hero before a footman), not to
		// the nearest or to the one he attacks.
		if ((orderId == OrderIds.flamestrike) || (orderId == OrderIds.shadowstrike)) {
			final CUnit hurtBy = creep.getHeaviestHitBy(game.getGameTurnTick(), HIT_MEMORY_TICKS);
			if ((hurtBy == null) || hurtBy.isDead() || hurtBy.isHidden() || hurtBy.isUnitAlly(player)
					|| !creep.canReach(hurtBy, range) || !canTarget(game, creep, ability, orderId, hurtBy)
					|| (countEnemiesAround(game, creep, player) < 2)) {
				return false;
			}
			target = hurtBy;
		}
		if ((target == null) && creep.canReach(attackTarget, range)
				&& (!onlyAttackers || isAttacking(game, attackTarget, player)) && isRuleTarget(rule, attackTarget)
				&& canTarget(game, creep, ability, orderId, attackTarget)) {
			target = attackTarget;
		}
		if (target == null) {
			return false;
		}
		// Carrion Swarm and Crushing Wave are aimed at a point here; 1.29 creeps aim them at the unit.
		return creep.order(game, orderId, target, true)
				|| creep.order(game, orderId, new AbilityPointTarget(target.getX(), target.getY()), true);
	}

	static int countEnemiesAround(final CSimulation game, final CUnit unit, final CPlayer player) {
		final int[] count = { 0 };
		game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), ATTACKER_SEARCH_RANGE, (enumUnit) -> {
			if (!enumUnit.isDead() && !enumUnit.isHidden() && !enumUnit.isUnitAlly(player)) {
				count[0]++;
			}
			return false;
		});
		return count[0];
	}

	static boolean isRuleTarget(final Rule rule, final CUnit target) {
		if (rule == Rule.ENEMY_AIR) {
			return target.getMovementType() == MovementType.FLY;
		}
		if (rule == Rule.ENEMY_RANGED) {
			return !isMelee(target);
		}
		if (rule == Rule.ENEMY_HERO) {
			return target.getHeroData() != null;
		}
		return true;
	}

	static boolean isMelee(final CUnit unit) {
		for (final CUnitAttack attack : unit.getCurrentAttacks()) {
			if (attack.getRange() > MELEE_RANGE) {
				return false;
			}
		}
		return !unit.getCurrentAttacks().isEmpty();
	}

	/**
	 * Whether the unit is attacking a unit within its reach (walking to it does not count).
	 */
	static boolean isFighting(final CSimulation game, final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior instanceof CBehaviorAttack)
				&& (((CBehaviorAttack) behavior).getTarget().visit(AbilityTargetVisitor.UNIT) != null)
				&& ((CBehaviorAttack) behavior).isWithinRange(game);
	}

	/**
	 * Whether the unit is attacking one of the player's units within its reach.
	 */
	static boolean isAttacking(final CSimulation game, final CUnit unit, final CPlayer player) {
		if (!isFighting(game, unit)) {
			return false;
		}
		final CUnit target = ((CBehaviorAttack) unit.getCurrentBehavior()).getTarget()
				.visit(AbilityTargetVisitor.UNIT);
		return target.isUnitAlly(player);
	}

	/**
	 * Whether an enemy of the player is on its way to attack the unit or attacking it.
	 */
	static boolean isAttacked(final CSimulation game, final CUnit unit, final CPlayer player) {
		final boolean[] attacked = { false };
		game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), ATTACKER_SEARCH_RANGE, (enumUnit) -> {
			if (!enumUnit.isDead() && !enumUnit.isUnitAlly(player)) {
				CBehavior behavior = enumUnit.getCurrentBehavior();
				if (behavior instanceof CBehaviorMove) {
					behavior = ((CBehaviorMove) behavior).getRangedBehavior();
				}
				if ((behavior instanceof CBehaviorAttack)
						&& (((CBehaviorAttack) behavior).getTarget().visit(AbilityTargetVisitor.UNIT) == unit)) {
					attacked[0] = true;
					return true;
				}
			}
			return false;
		});
		return attacked[0];
	}

	static boolean isReadyToCast(final CSimulation game, final CUnit unit, final int orderId) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof GenericSingleIconActiveAbility) && !ability.isDisabled()
					&& (((GenericSingleIconActiveAbility) ability).getBaseOrderId() == orderId)) {
				final BooleanAbilityActivationReceiver activation = BooleanAbilityActivationReceiver.INSTANCE.reset();
				ability.checkCanUse(game, unit, unit.getPlayerIndex(), orderId, true, activation);
				return activation.isOk();
			}
		}
		return false;
	}

	static boolean isWhirlingAround(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CBuffWhirlWindCaster) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether an enemy of the unit is within the reach of its attack on the unit.
	 */
	static boolean isHitInReach(final CSimulation game, final CUnit unit) {
		final CPlayer player = game.getPlayer(unit.getPlayerIndex());
		final boolean[] hit = { false };
		game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), ATTACKER_SEARCH_RANGE, (enumUnit) -> {
			if (!enumUnit.isDead() && !enumUnit.isUnitAlly(player)) {
				final CBehavior behavior = enumUnit.getCurrentBehavior();
				if ((behavior instanceof CBehaviorAttack)
						&& (((CBehaviorAttack) behavior).getTarget().visit(AbilityTargetVisitor.UNIT) == unit)
						&& ((CBehaviorAttack) behavior).isWithinRange(game)) {
					hit[0] = true;
					return true;
				}
			}
			return false;
		});
		return hit[0];
	}

	static boolean canTarget(final CSimulation game, final CUnit creep, final CAbility ability, final int orderId,
			final CUnit target) {
		final BooleanAbilityTargetCheckReceiver<CWidget> receiver = BooleanAbilityTargetCheckReceiver
				.<CWidget>getInstance().reset();
		if (ability instanceof CAutocastAbility) {
			// the autocast check also skips units that already have the spell's buff
			((CAutocastAbility) ability).checkCanAutoTarget(game, creep, creep.getPlayerIndex(), orderId, target,
					receiver);
		}
		else {
			ability.checkCanTarget(game, creep, creep.getPlayerIndex(), orderId, true, target, receiver);
		}
		if (!receiver.isTargetable()) {
			if ((Rule.of(orderId) != Rule.ENEMY_LATE) && (orderId != OrderIds.dreadlordinferno)
					&& (orderId != OrderIds.flamestrike)) {
				return false;
			}
			final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> pointReceiver = BooleanAbilityTargetCheckReceiver
					.<AbilityPointTarget>getInstance().reset();
			ability.checkCanTarget(game, creep, creep.getPlayerIndex(), orderId, true,
					new AbilityPointTarget(target.getX(), target.getY()), pointReceiver);
			return pointReceiver.isTargetable();
		}
		return (Rule.of(orderId) == Rule.ALLY_HEAL) || !hasSpellBuff(ability, target);
	}

	/**
	 * Measured on 1.29: a creep does not cast a curse or buff again on a unit that still has it (Cripple, Unholy
	 * Frenzy).
	 */
	static boolean hasSpellBuff(final CAbility ability, final CUnit target) {
		if (!(ability instanceof CAbilityAbilityBuilderGenericActive)) {
			return false;
		}
		final List<War3ID> buffIds = ((CAbilityAbilityBuilderGenericActive) ability).getBuffIds();
		if ((buffIds == null) || buffIds.isEmpty()) {
			return false;
		}
		for (final CAbility targetAbility : target.getAbilities()) {
			if ((targetAbility instanceof CBuff) && buffIds.contains(((CBuff) targetAbility).getAlias())) {
				return true;
			}
		}
		return false;
	}

	static float getArea(final CAbility ability) {
		if (ability instanceof AbilityBuilderAbility) {
			return ((AbilityBuilderAbility) ability).getArea();
		}
		if (ability instanceof CAbilityImmolation) {
			return ((CAbilityImmolation) ability).getAreaOfEffect();
		}
		final float area = ((GenericSingleIconActiveAbility) ability).getUIAreaOfEffect();
		return Float.isNaN(area) ? 0 : area;
	}

	static float getCastRange(final CAbility ability) {
		if (ability instanceof CAbilitySpellBase) {
			return ((CAbilitySpellBase) ability).getCastRange();
		}
		if (ability instanceof AbilityBuilderAbility) {
			return ((AbilityBuilderAbility) ability).getCastRange();
		}
		return 0;
	}

	static EnumSet<CTargetType> getTargetsAllowed(final CAbility ability) {
		if (ability instanceof CAbilitySpellBase) {
			return ((CAbilitySpellBase) ability).getTargetsAllowed();
		}
		if (ability instanceof AbilityBuilderActiveAbility) {
			return ((AbilityBuilderActiveAbility) ability).getTargetsAllowed();
		}
		return null;
	}

	/**
	 * Measured on 1.29: the creep thinks when it starts to fight and then once a second. Rain of Fire and Blizzard
	 * are cast already at the first thought; Purge, Chain Lightning, Frost Nova, Firebolt and Immolation at a later
	 * one. War Stomp, Slam and Heal are cast as soon as they are ready, and so are buffs on allies from the second
	 * thought on and Slow, Curse and buffs on the creep itself from the third (again right when the cooldown ends).
	 * Cyclone and Cripple come at the first thought, Cripple only on enemies that shoot from afar; Ensnare only on
	 * flyers; Sleep (on an enemy attacking the creep's side), Death Coil (heroes first), Frost Armor and Unholy Frenzy (on the creep itself) at the second;
	 * Faerie Fire and Raise Dead (with corpses near) at the third; Carrion Swarm and Crushing Wave at the fourth.
	 * Frost Armor also goes on an ally an enemy is sent against before the fight, like Heal. Polymorph, Hex, Roar,
	 * Abolish Magic and Rejuvenation on allies were never seen cast. Heroes of such a player also cast Mirror Image,
	 * Bladestorm, Divine Shield, Feral Spirit, Summon Phoenix and Spirit of Vengeance once they fight, Blizzard and
	 * Inferno at once (Bladestorm from the second thought on, Mirror Image goes first even when learned later), Storm
	 * Bolt, Entangling Roots and Mana Burn like the blasts, Force of Nature on the nearest tree once an enemy hits them
	 * (summons and Frost Armor come before blasts), and Healing Wave on themselves; Carrion Beetles go on the nearest
	 * corpse; Wind Walk, Holy Light, Summon Water Elemental, Thunder Clap, Shockwave, Hex, Serpent Ward, Starfall, Immolation and
	 * Fan of Knives were never seen cast.
	 */
	enum Rule {
		AREA(true, 0), GROUP(false, 0), ALLY_BUFF(true, 1), ALLY_HEAL(true, 0), ENEMY_ATTACKING(true, 2),
		ENEMY(false, 1), TOGGLE_ON(false, 1), ENEMY_AIR(false, 1), ENEMY_RANGED(false, 0), ENEMY_FAERIE(false, 2),
		ENEMY_FIRST(false, 0), ENEMY_LATE(false, 3), ENEMY_HERO(false, 1), ARMOR(false, 1), SELF_BUFF(false, 1),
		SELF_HEAL(true, 0), CORPSES(false, 2), SLEEP(false, 1), FIGHT(false, 0), BLADESTORM(false, 1), TREE(false, 0),
		CORPSE_TARGET(false, 0);

		private final boolean everyTick;
		private final int minimumThinks;

		Rule(final boolean everyTick, final int minimumThinks) {
			this.everyTick = everyTick;
			this.minimumThinks = minimumThinks;
		}

		static Rule of(final int orderId) {
			switch (orderId) {
			case OrderIds.stomp:
			case OrderIds.creepthunderclap:
				return AREA;
			case OrderIds.blizzard:
				return GROUP;
			case OrderIds.bloodlust:
			case OrderIds.innerfire:
				return ALLY_BUFF;
			case OrderIds.immolation:
				return TOGGLE_ON;
			case OrderIds.creepheal:
				return ALLY_HEAL;
			case OrderIds.slow:
			case OrderIds.curse:
				return ENEMY_ATTACKING;
			case OrderIds.ensnare:
				return ENEMY_AIR;
			case OrderIds.cyclone:
				return ENEMY_FIRST;
			case OrderIds.carrionswarm:
				return ENEMY_LATE;
			case OrderIds.deathcoil:
				return ENEMY_HERO;
			case OrderIds.frostarmor:
				return ARMOR;
			case OrderIds.unholyfrenzy:
				return SELF_BUFF;
			case OrderIds.rejuvination:
				return SELF_HEAL;
			case OrderIds.raisedead:
				return CORPSES;
			case OrderIds.cripple:
				return ENEMY_RANGED;
			case OrderIds.faeriefire:
				return ENEMY_FAERIE;
			case OrderIds.sleep:
				return SLEEP;
			case OrderIds.mirrorimage:
			case OrderIds.divineshield:
			case OrderIds.spiritwolf:
			case OrderIds.summonphoenix:
			case OrderIds.spiritofvengeance:
				return FIGHT;
			case OrderIds.whirlwind:
				return BLADESTORM;
			case OrderIds.forceofnature:
				return TREE;
			case OrderIds.carrionscarabs:
				return CORPSE_TARGET;
			case OrderIds.dreadlordinferno:
				return ENEMY_FIRST;
			case OrderIds.healingwave:
				return ALLY_HEAL;
			case OrderIds.thunderbolt:
			case OrderIds.entanglingroots:
			case OrderIds.manaburn:
				return ENEMY;
			case OrderIds.purge:
			case OrderIds.firebolt:
			case OrderIds.frostnova:
			case OrderIds.chainlightning:
			case OrderIds.shadowstrike:
			case OrderIds.flamestrike:
			case OrderIds.drain:
				return ENEMY;
			default:
				return null;
			}
		}
	}
}
