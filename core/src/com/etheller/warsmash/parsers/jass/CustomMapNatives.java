package com.etheller.warsmash.parsers.jass;

import java.util.ArrayList;
import java.util.List;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * 1.29 natives custom maps call that campaigns do not: unit owner and force checks, hero revive by coordinates,
 * ability level down, item owner, destructable name and invulnerability, unit stat and weapon getters and setters,
 * timed life cancel and event damage. Each rule is measured against 1.29.
 */
final class CustomMapNatives {
	private final JassProgram program;
	private final War3MapViewer viewer;
	private final CSimulation simulation;

	private CustomMapNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
		this.simulation = viewer.simulation;
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final CustomMapNatives natives = new CustomMapNatives(program, viewer);
		natives.registerUnits();
		natives.registerWeapons();
		natives.registerItemsAndDestructables();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}

	private String mapString(final List<JassValue> arguments, final int index) {
		return this.viewer.getGameUI().getTrigStr(string(arguments, index));
	}

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	private static float real(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static boolean bool(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(BooleanJassValueVisitor.getInstance());
	}

	private void registerUnits() {
		add("IsUnitInForce", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final List<?> force = object(arguments, 1);
			if ((unit == null) || (force == null)) {
				return BooleanJassValue.FALSE;
			}
			return BooleanJassValue.of(force.contains(this.simulation.getPlayer(unit.getPlayerIndex())));
		});
		add("IsUnitOwnedByPlayer", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			return BooleanJassValue.of((unit != null) && (player != null) && (unit.getPlayerIndex() == player.getId()));
		});
		add("ReviveHero", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if ((unit == null) || (unit.getHeroData() == null) || !unit.isDead()) {
				return BooleanJassValue.FALSE;
			}
			unit.reviveHeroAtPoint(this.simulation, real(arguments, 1), real(arguments, 2), bool(arguments, 3));
			return BooleanJassValue.TRUE;
		});
		add("DecUnitAbilityLevel", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit == null) {
				return IntegerJassValue.ZERO;
			}
			final CAbility found = unit.getAbilityByRawcode(new War3ID(integer(arguments, 1)));
			if (found == null) {
				return IntegerJassValue.ZERO;
			}
			if (!(found instanceof CLevelingAbility)) {
				final int level = AbilityNatives.scriptLevel(found);
				if ((found instanceof AbstractCAbility) && (level > 1)) {
					((AbstractCAbility) found).setScriptLevel(level - 1);
					return IntegerJassValue.of(level - 1);
				}
				return IntegerJassValue.of(level);
			}
			final CLevelingAbility ability = (CLevelingAbility) found;
			if (AbilityNatives.leveledLevel(ability) > ability.getLevel()) {
				final int shown = AbilityNatives.leveledLevel(ability) - 1;
				((AbstractCAbility) ability).setScriptLevel(shown);
				return IntegerJassValue.of(shown);
			}
			final int newLevel = ability.getLevel() - 1;
			if (newLevel < 1) {
				return IntegerJassValue.of(ability.getLevel());
			}
			ability.setLevel(this.simulation, unit, newLevel);
			return IntegerJassValue.of(newLevel);
		});
		add("BlzGetUnitMaxHP", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return IntegerJassValue.of(unit == null ? 0 : unit.getMaximumLife());
		});
		add("BlzGetUnitMaxMana", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return IntegerJassValue.of(unit == null ? 0 : unit.getMaximumMana());
		});
		add("BlzSetUnitMaxMana", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				// Measured: current mana stays as it was, only clamped to the new maximum.
				final float mana = unit.getMana();
				unit.setMaximumMana(integer(arguments, 1));
				unit.setMana(Math.min(mana, unit.getMaximumMana()));
			}
			return null;
		});
		add("BlzGetUnitCollisionSize", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return RealJassValue.of(unit == null ? 0 : unit.getUnitType().getCollisionSize());
		});
		add("BlzIsUnitSelectable", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			// Measured: true for a Locust unit and a hidden one too; only a null unit is not selectable.
			return BooleanJassValue.of(unit != null);
		});
		add("BlzSetHeroProperName", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final String name = mapString(arguments, 1);
			final CAbilityHero hero = unit == null ? null : unit.getHeroData();
			if ((hero != null) && (name != null)) {
				hero.setProperName(name);
			}
			return null;
		});
		add("BlzUnitCancelTimedLife", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			// Measured on 1.29: the timed life ends at once and the unit dies, as when it runs out.
			if (unit != null) {
				for (final CAbility ability : new ArrayList<>(unit.getAbilities())) {
					if (ability instanceof CBuffTimedLife) {
						unit.remove(this.simulation, ability);
					}
				}
			}
			return null;
		});
		add("BlzSetEventDamage", (arguments, globalScope, triggerScope) -> {
			if (triggerScope instanceof CommonTriggerExecutionScope) {
				((CommonTriggerExecutionScope) triggerScope).setDamageTaken(real(arguments, 0));
			}
			return null;
		});
	}

	/**
	 * Measured on 1.29: the weapon natives count weapons from 1 (index 1 is the first weapon, 0 reads 0 and sets
	 * nothing), unlike the 0-based common.j of later patches.
	 */
	private static CUnitAttack weapon(final CUnit unit, final int weaponIndex) {
		if (unit == null) {
			return null;
		}
		final List<CUnitAttack> attacks = unit.getUnitSpecificAttacks();
		final int listIndex = weaponIndex - 1;
		if ((attacks == null) || (listIndex < 0) || (listIndex >= attacks.size())) {
			return null;
		}
		return attacks.get(listIndex);
	}

	private void registerWeapons() {
		add("BlzGetUnitBaseDamage", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0), integer(arguments, 1));
			// A hero's base damage includes its primary attribute bonus (Paladin 22 at level 1).
			return IntegerJassValue.of(attack == null ? 0
					: attack.getDamageBase() + attack.getPrimaryAttributePermanentDamageBonus());
		});
		add("BlzSetUnitBaseDamage", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, integer(arguments, 2));
			if (attack != null) {
				attack.setDamageBase(integer(arguments, 1) - attack.getPrimaryAttributePermanentDamageBonus());
				unit.notifyAttacksChanged();
			}
			return null;
		});
		add("BlzGetUnitDiceNumber", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0), integer(arguments, 1));
			return IntegerJassValue.of(attack == null ? 0 : attack.getDamageDice());
		});
		add("BlzSetUnitDiceNumber", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, integer(arguments, 2));
			if (attack != null) {
				attack.setDamageDice(integer(arguments, 1));
				unit.notifyAttacksChanged();
			}
			return null;
		});
		add("BlzGetUnitDiceSides", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0), integer(arguments, 1));
			return IntegerJassValue.of(attack == null ? 0 : attack.getDamageSidesPerDie());
		});
		add("BlzSetUnitDiceSides", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, integer(arguments, 2));
			if (attack != null) {
				attack.setDamageSidesPerDie(integer(arguments, 1));
				unit.notifyAttacksChanged();
			}
			return null;
		});
		add("BlzGetUnitAttackCooldown", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0), integer(arguments, 1));
			return RealJassValue.of(attack == null ? 0 : attack.getCooldownTimeBase());
		});
		add("BlzSetUnitAttackCooldown", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, integer(arguments, 2));
			if (attack != null) {
				attack.setCooldownTime(real(arguments, 1));
				unit.notifyAttacksChanged();
			}
			return null;
		});
	}

	private void registerItemsAndDestructables() {
		add("SetItemPlayer", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			if ((item != null) && (player != null)) {
				item.setPlayerIndex(player.getId());
			}
			return null;
		});
		add("GetDestructableName", (arguments, globalScope, triggerScope) -> {
			final CDestructable dest = object(arguments, 0);
			return StringJassValue.of(dest == null ? "" : dest.getDestType().getName());
		});
		add("IsDestructableInvulnerable", (arguments, globalScope, triggerScope) -> {
			final CDestructable dest = object(arguments, 0);
			return BooleanJassValue.of((dest != null) && dest.isInvulnerable());
		});
	}
}
