package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassTypeConstructor;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.jass.CAbilityJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.jass.CBuffJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.GetABAbilityByRawcodeVisitor;
import java.util.List;

/**
 * Unit abilities: adding, levels, flags, cooldowns and activation.
 */
final class AbilityNatives extends CommonNatives {
	private static final War3ID INVENTORY_CODE = War3ID.fromString("AInv");
	/** The attack and move the engine gives units itself; the original does not add them by script. */
	private static final War3ID ATTACK_CODE = War3ID.fromString("Aatk");
	private static final War3ID MOVE_CODE = War3ID.fromString("Amov");

	AbilityNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerUnitAbilities();
		registerAbilityState();
		registerCooldowns();
	}

	private void registerUnitAbilities() {
		final HandleJassType abilityType = type("ability");
		final HandleJassType buffType = type("buff");

		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAbilityLevel",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichWidget == null) {
						return IntegerJassValue.ZERO;
					}
					final War3ID abilityId = new War3ID(rawcode);
					final CAbility ability = whichWidget.getAbilityByRawcode(abilityId);
					if (ability == null) {
						// Measured: the original reports the inventory under its code too (a unit with a plain
						// inventory alias).
						if (abilityId.equals(INVENTORY_CODE)
								&& (whichWidget.getFirstAbilityOfType(CAbilityInventory.class) != null)) {
							return IntegerJassValue.of(1);
						}
						final int illusionLevel = whichWidget.getIllusionAbilityLevel(rawcode);
						if (illusionLevel > 0) {
							return IntegerJassValue.of(illusionLevel);
						}
						return IntegerJassValue.ZERO;
					}
					// Measured: the original reads level 1 for a buff, whatever level of spell placed it.
					if (ability instanceof CBuff) {
						return IntegerJassValue.of(1);
					}
					if (ability instanceof CLevelingAbility) {
						return IntegerJassValue.of(Math.max(1, ((CLevelingAbility) ability).getLevel()));
					}
					return IntegerJassValue.of(scriptLevel(ability));
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitMakeAbilityPermanent",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean permanent = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final int rawcode = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return BooleanJassValue.FALSE;
					}
					final CAbility ability = whichUnit.getAbilityByRawcode(new War3ID(rawcode));
					if (ability == null) {
						return BooleanJassValue.FALSE;
					}
					ability.setPermanent(permanent);
					return BooleanJassValue.TRUE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IncUnitAbilityLevel",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichWidget = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final War3ID war3id = new War3ID(rawcode);
					final CAbility found = whichWidget.getAbilityByRawcode(war3id);
					if ((found == null) && war3id.equals(CUnitData.ALARM_ABILITY_ID)) {
						return IntegerJassValue.ZERO;
					}
					if (found == null) {
						final CAbility created = this.simulation.getAbilityData().createAbility(war3id,
								this.simulation.getHandleIdAllocator().createId());
						if (created == null) {
							return IntegerJassValue.ZERO;
						}
						whichWidget.add(this.simulation, created);
						// TODO below code is very stupid!!
						return IntegerJassValue.of(1);
					}
					final int levelCount = this.simulation.getAbilityData().getLevelCount(war3id);
					if (!(found instanceof CLevelingAbility)) {
						// Measured: a built-in ability goes up only below the data's level count (0 for most).
						final int level = scriptLevel(found);
						if ((found instanceof AbstractCAbility) && (level < levelCount)) {
							((AbstractCAbility) found).setScriptLevel(level + 1);
							return IntegerJassValue.of(level + 1);
						}
						return IntegerJassValue.of(level);
					}
					final CLevelingAbility ability = (CLevelingAbility) found;
					if ((levelCount > 0) && (ability.getLevel() >= levelCount)) {
						return IntegerJassValue.of(ability.getLevel());
					}
					final int newLevel = ability.getLevel() + 1;
					ability.setLevel(this.simulation, whichWidget, newLevel);
					return IntegerJassValue.of(newLevel);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAbilityLevel",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int newLevel = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final War3ID war3id = new War3ID(rawcode);
					final CAbility found = whichUnit.getAbilityByRawcode(war3id);
					if (found == null) {
						// Measured: a missing alarm reads 0, it cannot be added by script.
						return IntegerJassValue.of(war3id.equals(CUnitData.ALARM_ABILITY_ID) ? 0 : newLevel);
					}
					// Measured: the level is capped at the data's level count, so a built-in ability whose data has
					// no levels (attack, move) reads 0 afterwards.
					final int levelCount = this.simulation.getAbilityData().getLevelCount(war3id);
					final int level = levelCount >= 0 ? Math.min(newLevel, levelCount) : newLevel;
					if (found instanceof CLevelingAbility) {
						final int leveled = Math.max(1, level);
						((CLevelingAbility) found).setLevel(this.simulation, whichUnit, leveled);
						// A spell also loads the new level's mana cost, cooldown and effect numbers.
						if ((found instanceof CAbilitySpell) || (found instanceof AbilityBuilderAbility)) {
							final CAbilityType<?> leveledType = this.simulation.getAbilityData().getAbilityType(war3id);
							if (leveledType != null) {
								leveledType.setLevel(this.simulation, whichUnit, (CLevelingAbility) found, leveled);
							}
						}
						return IntegerJassValue.of(leveled);
					}
					if (found instanceof AbstractCAbility) {
						((AbstractCAbility) found).setScriptLevel(Math.max(0, level));
					}
					return IntegerJassValue.of(scriptLevel(found));
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitAddAbility",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int abilityId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return BooleanJassValue.FALSE;
					}
					final War3ID rawcode = new War3ID(abilityId);
					final CAbilityType<?> abilityTypeTmp = this.simulation.getAbilityData()
							.getAbilityType(rawcode);
					if (rawcode.equals(CUnitData.ALARM_ABILITY_ID)) {
						// Measured: the alarm cannot be added by script, not even back to a building it was removed from.
						return BooleanJassValue.FALSE;
					}
					if ((abilityTypeTmp == null) && (rawcode.equals(ATTACK_CODE) || rawcode.equals(MOVE_CODE)
							|| (this.simulation.getAbilityData().getLevelCount(rawcode) < 0))) {
						return BooleanJassValue.FALSE;
					}
					final CAbility existingAbility = whichUnit.getAbilityByRawcode(rawcode);
					if (existingAbility != null) {
						return BooleanJassValue.FALSE;
					}
					// Measured: an ability the data knows is added even where the engine has no behaviour for it
					// (level 1, removable), as units made with such abilities already carry them.
					final int abilityHandleId = this.simulation.getHandleIdAllocator().createId();
					final CAbility ability = abilityTypeTmp != null ? abilityTypeTmp.createAbility(abilityHandleId)
							: this.simulation.getAbilityData().createAbility(rawcode, abilityHandleId);
					whichUnit.add(this.simulation, ability);
					return BooleanJassValue.TRUE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitRemoveAbility",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int abilityId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return BooleanJassValue.FALSE;
					}
					final War3ID rawcode = new War3ID(abilityId);

					final CAbility abil = whichUnit.getAbilityByRawcode(rawcode);
					if (abil != null) {
						whichUnit.remove(this.simulation, abil);
						return BooleanJassValue.TRUE;
					}
					return BooleanJassValue.FALSE;
				});
		final JassFunction createAbilityFxn = (arguments, globalScope, triggerScope) -> {
			final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			final CAbility ability = this.simulation.getAbilityData().getAbilityType(new War3ID(rawcode))
					.createAbility(this.simulation.getHandleIdAllocator().createId());
			return new HandleJassValue(abilityType, ability);
		};
		jassProgramVisitor.getJassNativeManager().createNative("CreateAbility", createAbilityFxn);
		final JassFunction createJassAbilityFxn = (arguments, globalScope, triggerScope) -> {
			final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			final CAbility ability = new CAbilityJass(this.simulation.getHandleIdAllocator().createId(),
					new War3ID(rawcode), globalScope);
			return new HandleJassValue(abilityType, ability);
		};
		jassProgramVisitor.getJassNativeManager().createNative("CreateJassAbility", createJassAbilityFxn);
		abilityType.setConstructorNative(new HandleJassTypeConstructor("CreateJassAbility"));
		final JassFunction createJassBuffFxn = (arguments, globalScope, triggerScope) -> {
			final int codeId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			final int aliasId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			final CBuffJass ability = new CBuffJass(this.simulation.getHandleIdAllocator().createId(),
					new War3ID(codeId), new War3ID(aliasId), globalScope);
			return new HandleJassValue(buffType, ability);
		};
		jassProgramVisitor.getJassNativeManager().createNative("CreateJassBuff", createJassBuffFxn);
		buffType.setConstructorNative(new HandleJassTypeConstructor("CreateJassBuff"));
		final JassFunction getUnitAbilityByIndex = (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			final int whichAbilityIndex = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());

			final List<CAbility> abilities = whichUnit.getAbilities();
			return new HandleJassValue(abilityType,
					(whichAbilityIndex < abilities.size()) ? abilities.get(whichAbilityIndex) : null);
		};
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAbilityByIndex", getUnitAbilityByIndex);
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetUnitAbilityByIndex", getUnitAbilityByIndex);
		final JassFunction getUnitAbilityById = (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			final int whichAbilityId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());

			return new HandleJassValue(abilityType, whichUnit.getAbilityByRawcode(new War3ID(whichAbilityId)));
		};
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAbility", getUnitAbilityById);
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetUnitAbility", getUnitAbilityById);
		jassProgramVisitor.getJassNativeManager().createNative("AddUnitAbility",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CAbility ability = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					unit.add(this.simulation, ability);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveUnitAbility",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CAbility ability = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (ability instanceof CBuff) {
						// NOTE: Retera writing this native, but I was not author of
						// the remove(CBuff) function being independent from remove(CAbility).
						// The difference is probably dumb.
						unit.remove(this.simulation, (CBuff) ability);
					}
					else {
						unit.remove(this.simulation, ability);
					}
					return null;
				});
		final JassFunction getAbilityAliasId = (arguments, globalScope, triggerScope) -> {
			final CAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			return IntegerJassValue.of(ability.getAlias().getValue());
		};
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityAliasId", getAbilityAliasId);
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetAbilityId", getAbilityAliasId);
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityCodeId",
				(arguments, globalScope, triggerScope) -> {
					final CAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(ability.getCode().getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityLevel",
				(arguments, globalScope, triggerScope) -> {
					final CLevelingAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(ability.getLevel());
				});
	}

	private void registerAbilityState() {
		final HandleJassType itemType = type("item");
		final HandleJassType abilitydisabletypeType = type("abilitydisabletype");

		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityLevel",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CLevelingAbility whichAbility = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					final int newLevel = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					if (whichAbility != null) {
						whichAbility.setLevel(this.simulation, whichUnit, newLevel);
						return IntegerJassValue.of(newLevel);
					}
					return IntegerJassValue.of(newLevel);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityIconShowing",
				(arguments, globalScope, triggerScope) -> {
					final CAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					ability.setIconShowing(flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityPermanent",
				(arguments, globalScope, triggerScope) -> {
					final CAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					ability.setPermanent(flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityPhysical",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					ability.setPhysical(flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityUniversal",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					ability.setUniversal(flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsAbilityPermanent",
				(arguments, globalScope, triggerScope) -> {
					final CAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(ability.isPermanent());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsAbilityPhysical",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(ability.isPhysical());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsAbilityUniversal",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(ability.isUniversal());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityEnabledWhileUpgrading",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					ability.setEnabledWhileUpgrading(flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityEnabledWhileUnderConstruction",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					ability.setEnabledWhileUnderConstruction(flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ConvertAbilityDisableType",
				(arguments, globalScope, triggerScope) -> {
					final int i = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return enumValue(abilitydisabletypeType, CAbilityDisableType.VALUES, i);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityDisabled",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CAbility ability = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					CAbilityDisableType disableType = nullable(arguments, 3, ObjectJassValueVisitor.getInstance());
					if (disableType == null) {
						disableType = CAbilityDisableType.TRIGGER;
					}
					ability.setDisabled(flag, disableType);
					unit.checkDisabledAbilities(this.simulation, flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityItem",
				(arguments, globalScope, triggerScope) -> {
					final CAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return new HandleJassValue(itemType, ability.getItem());
				});
		jassProgramVisitor.getJassNativeManager().createNative("AbilityActivate",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final AbilityBuilderActiveAbility ability = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					ability.activate(this.simulation, unit);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AbilityDeactivate",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final AbilityBuilderActiveAbility ability = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					ability.deactivate(this.simulation, unit);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsToggleAbilityActive",
				(arguments, globalScope, triggerScope) -> {
					final GenericSingleIconActiveAbility ability = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(ability.isToggleOn());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAbilityCastRange",
				(arguments, globalScope, triggerScope) -> {
					final AbilityBuilderActiveAbility ability = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final float range = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					ability.setCastRange(range);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("WarsmashGetAbilityClassName",
				(arguments, globalScope, triggerScope) -> {
					final CAbility ability = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return new StringJassValue(ability.getClass().getSimpleName());
				});
	}

	static int scriptLevel(final CAbility ability) {
		return ability instanceof AbstractCAbility ? ((AbstractCAbility) ability).getScriptLevel() : 1;
	}

	private void registerCooldowns() {
		final JassFunction endUnitAbilityCooldown = (arguments, globalScope, triggerScope) -> {
			final CUnit theUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			final int aliasId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			final AbilityBuilderAbility abil = theUnit
					.getAbility(GetABAbilityByRawcodeVisitor.getInstance().reset(new War3ID(aliasId)));
			if (abil != null) {
				abil.resetCooldown(this.simulation, theUnit);
			}
			else {
				theUnit.beginCooldown(this.simulation, new War3ID(aliasId), 0);
			}
			return null;
		};
		jassProgramVisitor.getJassNativeManager().createNative("EndUnitAbilityCooldown", endUnitAbilityCooldown);
		jassProgramVisitor.getJassNativeManager().createNative("BlzEndUnitAbilityCooldown", endUnitAbilityCooldown);
		jassProgramVisitor.getJassNativeManager().createNative("EndAbilityCooldown",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final AbilityBuilderAbility ability = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					ability.resetCooldown(this.simulation, unit);
					return null;
				});
		final JassFunction startUnitAbilityCooldown = (arguments, globalScope, triggerScope) -> {
			final CUnit theUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			final int aliasIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			final float cooldown = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
			final War3ID aliasId = new War3ID(aliasIdRawcode);
			theUnit.beginCooldown(this.simulation, aliasId, cooldown);
			return null;
		};
		jassProgramVisitor.getJassNativeManager().createNative("StartUnitAbilityCooldown",
				startUnitAbilityCooldown);
		jassProgramVisitor.getJassNativeManager().createNative("BlzStartUnitAbilityCooldown",
				startUnitAbilityCooldown);
		jassProgramVisitor.getJassNativeManager().createNative("StartUnitAbilityDefaultCooldown",
				(arguments, globalScope, triggerScope) -> {
					final CUnit theUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int aliasIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final War3ID aliasId = new War3ID(aliasIdRawcode);
					final AbilityBuilderAbility abil = theUnit
							.getAbility(GetABAbilityByRawcodeVisitor.getInstance().reset(aliasId));
					if (abil != null) {
						abil.startCooldown(this.simulation, theUnit);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("StartAbilityDefaultCooldown",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CAbility ability = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (ability instanceof AbilityBuilderAbility) {
						final AbilityBuilderAbility abilityBuilderAbility = (AbilityBuilderAbility) ability;
						abilityBuilderAbility.startCooldown(this.simulation, unit);
					}
					return null;
				});
		final JassFunction getUnitAbilityCooldownRemaining = (arguments, globalScope, triggerScope) -> {
			final CUnit theUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			final int aliasIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			final War3ID aliasId = new War3ID(aliasIdRawcode);
			return RealJassValue.of(Math.max(theUnit.getCooldownRemainingTicks(this.simulation, aliasId),
					theUnit.getCooldownRemainingTicks(this.simulation, cooldownKey(theUnit, aliasId)))
					* WarsmashConstants.SIMULATION_STEP_TIME);
		};
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAbilityCooldownRemaining",
				getUnitAbilityCooldownRemaining);
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetUnitAbilityCooldownRemaining",
				getUnitAbilityCooldownRemaining);
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAbilityCooldownLengthDisplay",
				(arguments, globalScope, triggerScope) -> {
					final CUnit theUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int aliasIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final War3ID aliasId = new War3ID(aliasIdRawcode);
					return RealJassValue.of(Math.max(theUnit.getCooldownLengthDisplayTicks(this.simulation, aliasId),
							theUnit.getCooldownLengthDisplayTicks(this.simulation, cooldownKey(theUnit, aliasId)))
							* WarsmashConstants.SIMULATION_STEP_TIME);
				});
	}

	// Abilities run their cooldown under the code of the ability they are made from (a potion's 'AIh1' under 'AIhe').
	private static War3ID cooldownKey(final CUnit unit, final War3ID rawcode) {
		final CAbility ability = unit.getAbilityByRawcode(rawcode);
		return ability == null ? rawcode : ability.getCode();
	}
}
