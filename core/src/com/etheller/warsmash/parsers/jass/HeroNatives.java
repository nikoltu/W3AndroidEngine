package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Hero experience, levels, attributes and skills.
 */
final class HeroNatives extends CommonNatives {
	HeroNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		jassProgramVisitor.getJassNativeManager().createNative("AddHeroXP",
				(arguments, globalScope, triggerScope) -> {
					// Todo add showEyeCandy boolean and update addXp fn and upstream fns to make
					// hero level up fx suppressable
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int xp = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean showEyeCandy = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = scriptHeroData(whichUnit);
					if (heroData != null) {
						heroData.addXp(this.simulation, whichUnit, xp, showEyeCandy);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetHeroXP",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : scriptHeroData(whichUnit);
					return IntegerJassValue.of(heroData == null ? 0 : heroData.getXp());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetHeroLevel",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					final CAbilityHero heroData = whichUnit == null ? null : scriptHeroData(whichUnit);
					return IntegerJassValue.of(heroData == null ? 0 : heroData.getHeroLevel());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitLevel",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					if (whichUnit == null) {
						return IntegerJassValue.ZERO;
					}
					final CAbilityHero heroData = scriptHeroData(whichUnit);
					return IntegerJassValue.of(
							heroData != null ? heroData.getHeroLevel() : whichUnit.getUnitType().getLevel());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetHeroSkillPoints",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : scriptHeroData(whichUnit);
					return IntegerJassValue.of(heroData == null ? 0 : heroData.getSkillPoints());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetHeroStr",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean includeBonuses = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : scriptHeroData(whichUnit);
					if (heroData == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(includeBonuses ? heroData.getStrength().getCurrent()
							: heroData.getStrength().getCurrentBase());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetHeroAgi",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean includeBonuses = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : scriptHeroData(whichUnit);
					if (heroData == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(includeBonuses ? heroData.getAgility().getCurrent()
							: heroData.getAgility().getCurrentBase());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetHeroInt",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean includeBonuses = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : scriptHeroData(whichUnit);
					if (heroData == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(includeBonuses ? heroData.getIntelligence().getCurrent()
							: heroData.getIntelligence().getCurrentBase());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetHeroXP",
				(arguments, globalScope, triggerScope) -> {
					// Todo add showEyeCandy boolean and update addXp fn and upstream fns to make
					// hero level up fx suppressable
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int xp = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean showEyeCandy = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : whichUnit.getHeroData();
					if (heroData != null) {
						heroData.setXp(this.simulation, whichUnit, xp, showEyeCandy);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetHeroLevel",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int level = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean fx = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : whichUnit.getHeroData();
					if (heroData != null) {
						// Measured: the original treats level 1 as level 2, so a new hero set to level 1 gains a level.
						heroData.setHeroLevel(this.simulation, whichUnit, Math.max(2, level), fx);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SelectHeroSkill",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int skill = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final CAbilityHero heroData = scriptHeroData(whichUnit);
					if (heroData != null) {
						heroData.selectHeroSkill(this.simulation, whichUnit, new War3ID(skill));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetHeroStr",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int str = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean permanent = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : whichUnit.getHeroData();
					if (heroData != null) {
						if (permanent) {
							heroData.setStrengthBase(this.simulation, whichUnit,
									str - (heroData.getStrength().getCurrentBase() - heroData.getStrength().getBase()));
						} // Todo add else case to handle non-permanent
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetHeroAgi",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int agi = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean permanent = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : whichUnit.getHeroData();
					if (heroData != null) {
						if (permanent) {
							heroData.setAgilityBase(this.simulation, whichUnit,
									agi - (heroData.getAgility().getCurrentBase() - heroData.getAgility().getBase()));
						} // Todo add else case to handle non-permanent
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetHeroInt",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int intelligence = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean permanent = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CAbilityHero heroData = whichUnit == null ? null : whichUnit.getHeroData();
					if (heroData != null) {
						if (permanent) {
							heroData.setIntelligenceBase(this.simulation, whichUnit,
									intelligence - (heroData.getIntelligence().getCurrentBase() - heroData.getIntelligence().getBase()));
						} // Todo add else case to handle non-permanent
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SuspendHeroXP",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichHero = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if ((whichHero != null) && (whichHero.getHeroData() != null)) {
						whichHero.getHeroData().setXpSuspended(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsSuspendedXP",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichHero = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					// Measured on 1.29: a null unit or a unit that is not a hero counts as suspended.
					return BooleanJassValue.of((whichHero == null) || (whichHero.getHeroData() == null)
							|| whichHero.getHeroData().isXpSuspended());
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitModifySkillPoints",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichHero = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int delta = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if ((whichHero == null) || (whichHero.getHeroData() == null)) {
						return BooleanJassValue.FALSE;
					}
					final CAbilityHero heroData = whichHero.getHeroData();
					final int newPoints = Math.max(0, heroData.getSkillPoints() + delta);
					final boolean changed = newPoints != heroData.getSkillPoints();
					heroData.setSkillPoints(newPoints);
					whichHero.internalPublishHeroStatsChanged();
					return BooleanJassValue.of(changed);
				});
		jassProgramVisitor.getJassNativeManager().createNative("CachePlayerHeroData",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (player != null) {
						player.cacheHeroData(this.simulation);
					}
					return null;
				});
	}

	// Measured on 1.29: to scripts an illusion is no hero, whatever stats it copied.
	private static CAbilityHero scriptHeroData(final CUnit unit) {
		return ((unit == null) || unit.isIllusion()) ? null : unit.getHeroData();
	}
}
