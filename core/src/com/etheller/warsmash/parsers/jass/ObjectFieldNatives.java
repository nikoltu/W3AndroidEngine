package com.etheller.warsmash.parsers.jass;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
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
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CPrimaryAttribute;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CUnitState;

/**
 * The 1.31+ object field natives custom maps use to read and change units and items at run time
 * (BlzGetUnitIntegerField and the other Get/Set pairs). The field constants are the four letter object editor ids
 * common.j hands to the Convert*Field natives; a field the engine keeps no value for reads 0, false or an empty
 * string and a set of it returns false, as the original does for an id it does not know.
 */
final class ObjectFieldNatives {
	private interface IntGet<T> {
		int get(ObjectFieldNatives n, T target);
	}

	private interface IntSet<T> {
		boolean set(ObjectFieldNatives n, T target, int value);
	}

	private interface RealGet<T> {
		float get(ObjectFieldNatives n, T target);
	}

	private interface RealSet<T> {
		boolean set(ObjectFieldNatives n, T target, float value);
	}

	private interface BoolGet<T> {
		boolean get(ObjectFieldNatives n, T target);
	}

	private interface BoolSet<T> {
		boolean set(ObjectFieldNatives n, T target, boolean value);
	}

	private interface StringGet<T> {
		String get(ObjectFieldNatives n, T target);
	}

	private interface StringSet<T> {
		boolean set(ObjectFieldNatives n, T target, String value);
	}

	private static final Map<Integer, IntGet<CUnit>> UNIT_INT_GET = new HashMap<>();
	private static final Map<Integer, IntSet<CUnit>> UNIT_INT_SET = new HashMap<>();
	private static final Map<Integer, RealGet<CUnit>> UNIT_REAL_GET = new HashMap<>();
	private static final Map<Integer, RealSet<CUnit>> UNIT_REAL_SET = new HashMap<>();
	private static final Map<Integer, BoolGet<CUnit>> UNIT_BOOL_GET = new HashMap<>();
	private static final Map<Integer, BoolSet<CUnit>> UNIT_BOOL_SET = new HashMap<>();
	private static final Map<Integer, StringGet<CUnit>> UNIT_STRING_GET = new HashMap<>();
	private static final Map<Integer, StringSet<CUnit>> UNIT_STRING_SET = new HashMap<>();
	private static final Map<Integer, IntGet<CItem>> ITEM_INT_GET = new HashMap<>();
	private static final Map<Integer, IntSet<CItem>> ITEM_INT_SET = new HashMap<>();
	private static final Map<Integer, RealGet<CItem>> ITEM_REAL_GET = new HashMap<>();
	private static final Map<Integer, RealSet<CItem>> ITEM_REAL_SET = new HashMap<>();
	private static final Map<Integer, BoolGet<CItem>> ITEM_BOOL_GET = new HashMap<>();
	private static final Map<Integer, BoolSet<CItem>> ITEM_BOOL_SET = new HashMap<>();
	private static final Map<Integer, StringGet<CItem>> ITEM_STRING_GET = new HashMap<>();
	private static final Map<Integer, StringSet<CItem>> ITEM_STRING_SET = new HashMap<>();

	/** HERO_ATTRIBUTE_STR, _INT and _AGI of common.j. */
	private static int heroAttribute(final CPrimaryAttribute attribute) {
		switch (attribute) {
		case STRENGTH:
			return 1;
		case INTELLIGENCE:
			return 2;
		default:
			return 3;
		}
	}

	private static int id(final String fourChars) {
		return (fourChars.charAt(0) << 24) | (fourChars.charAt(1) << 16) | (fourChars.charAt(2) << 8)
				| fourChars.charAt(3);
	}

	private static final List<String> ITEM_ARMOR_TYPES = Arrays.asList("flesh", "metal", "wood", "ethereal", "stone");

	private static void tintItemField(final String code, final int channel) {
		ITEM_INT_GET.put(id(code), (n, i) -> i.getTint(channel));
		ITEM_INT_SET.put(id(code), (n, i, v) -> {
			i.setTint(channel, v);
			final float red = i.getTint(0) / 255f;
			final float green = i.getTint(1) / 255f;
			final float blue = i.getTint(2) / 255f;
			final float alpha = i.getTint(3) / 255f;
			n.render.post(RenderSide.WORLD, () -> {
				final RenderItem peer = n.viewer.getRenderPeer(i);
				if (peer != null) {
					peer.getInstance().setVertexColor(red, green, blue, alpha);
				}
			});
			return true;
		});
	}

	private static void tintFields(final String code, final int channel) {
		UNIT_INT_GET.put(id(code), (n, u) -> {
			final float[] color = n.simulation.getUnitVertexColor(u);
			return color == null ? 255 : Math.round(color[channel] * 255f);
		});
		UNIT_INT_SET.put(id(code), (n, u, v) -> {
			final float[] color = n.simulation.getUnitVertexColor(u);
			if (color == null) {
				return false;
			}
			color[channel] = Math.max(0, Math.min(255, v)) / 255f;
			n.simulation.changeUnitVertexColor(u, color[0], color[1], color[2], color[3]);
			return true;
		});
	}

	private static CAbilityHero.HeroStatValue stat(final CAbilityHero hero, final int attribute) {
		switch (attribute) {
		case 0:
			return hero.getStrength();
		case 1:
			return hero.getAgility();
		default:
			return hero.getIntelligence();
		}
	}

	/**
	 * The three attribute fields of a hero: the first reads the base the hero has without item and aura bonuses, the
	 * second the permanent part (read and set alike), the third the shown total.
	 */
	private static void heroAttributeFields(final String base, final String permanent, final String total,
			final int attribute) {
		final IntGet<CUnit> baseGet = (n, u) -> {
			final CAbilityHero hero = u.getHeroData();
			return hero == null ? 0 : stat(hero, attribute).getCurrentBase();
		};
		final IntSet<CUnit> baseSet = (n, u, v) -> {
			final CAbilityHero hero = u.getHeroData();
			if (hero == null) {
				return false;
			}
			final CAbilityHero.HeroStatValue stat = stat(hero, attribute);
			final int raise = v - (stat.getCurrentBase() - stat.getBase());
			switch (attribute) {
			case 0:
				hero.setStrengthBaseRaisingMaximums(n.simulation, u, raise);
				break;
			case 1:
				hero.setAgilityBaseRaisingMaximums(n.simulation, u, raise);
				break;
			default:
				hero.setIntelligenceBaseRaisingMaximums(n.simulation, u, raise);
				break;
			}
			return true;
		};
		UNIT_INT_GET.put(id(base), baseGet);
		UNIT_INT_SET.put(id(base), baseSet);
		UNIT_INT_GET.put(id(permanent), baseGet);
		UNIT_INT_SET.put(id(permanent), baseSet);
		UNIT_INT_GET.put(id(total), (n, u) -> {
			final CAbilityHero hero = u.getHeroData();
			return hero == null ? 0 : stat(hero, attribute).getCurrent();
		});
	}

	static {
		UNIT_INT_GET.put(id("udty"), (n, u) -> u.getDefenseType().ordinal());
		UNIT_INT_SET.put(id("udty"), (n, u, v) -> {
			if ((v < 0) || (v >= CDefenseType.VALUES.length)) {
				return false;
			}
			u.setDefenseType(CDefenseType.VALUES[v]);
			return true;
		});
		tintFields("uclr", 0);
		tintFields("uclg", 1);
		tintFields("uclb", 2);
		tintFields("ucal", 3);
		UNIT_INT_GET.put(id("ulev"), (n, u) -> {
			final CAbilityHero hero = u.getHeroData();
			return hero != null ? hero.getHeroLevel() : u.getUnitType().getLevel();
		});
		UNIT_INT_SET.put(id("ulev"), (n, u, v) -> {
			final CAbilityHero hero = u.getHeroData();
			if ((hero == null) || (v < 1)) {
				return false;
			}
			hero.setHeroLevel(n.simulation, u, v, false);
			return true;
		});
		UNIT_INT_GET.put(id("upra"), (n, u) -> heroAttribute(u.getUnitType().getPrimaryAttribute()));
		UNIT_INT_GET.put(id("uori"), (n, u) -> u.getUnitType().getOrientationInterpolation());
		UNIT_INT_GET.put(id("ubba"), (n, u) -> u.getUnitType().getGoldBountyAwardedBase());
		UNIT_INT_GET.put(id("ubdi"), (n, u) -> u.getUnitType().getGoldBountyAwardedDice());
		UNIT_INT_GET.put(id("ubsi"), (n, u) -> u.getUnitType().getGoldBountyAwardedSides());
		UNIT_INT_GET.put(id("ulba"), (n, u) -> u.getUnitType().getLumberBountyAwardedBase());
		UNIT_INT_GET.put(id("ulbd"), (n, u) -> u.getUnitType().getLumberBountyAwardedDice());
		UNIT_INT_GET.put(id("ulbs"), (n, u) -> u.getUnitType().getLumberBountyAwardedSides());
		heroAttributeFields("ustc", "ustm", "ustb", 0);
		heroAttributeFields("uagc", "uagm", "uagb", 1);
		heroAttributeFields("uinc", "uinm", "uinb", 2);

		UNIT_REAL_GET.put(id("uhpc"), (n, u) -> u.getScriptUnitState(n.simulation, CUnitState.LIFE));
		UNIT_REAL_SET.put(id("uhpc"), (n, u, v) -> {
			u.setUnitState(n.simulation, CUnitState.LIFE, v);
			return true;
		});
		UNIT_REAL_GET.put(id("umpc"), (n, u) -> u.getScriptUnitState(n.simulation, CUnitState.MANA));
		UNIT_REAL_SET.put(id("umpc"), (n, u, v) -> {
			u.setUnitState(n.simulation, CUnitState.MANA, v);
			return true;
		});
		UNIT_REAL_GET.put(id("uhpr"), (n, u) -> u.getUnitType().getLifeRegen() + u.getLifeRegenBonus());
		UNIT_REAL_SET.put(id("uhpr"), (n, u, v) -> {
			u.setLifeRegenBonus(v - u.getUnitType().getLifeRegen());
			return true;
		});
		UNIT_REAL_GET.put(id("umpr"), (n, u) -> u.getManaRegen() + u.getManaRegenBonus());
		UNIT_REAL_SET.put(id("umpr"), (n, u, v) -> {
			u.setManaRegenBonus(v - u.getManaRegen());
			return true;
		});
		UNIT_REAL_GET.put(id("umvc"), (n, u) -> u.getSpeed());
		UNIT_REAL_SET.put(id("umvc"), (n, u, v) -> {
			u.setSpeed((int) v);
			return true;
		});
		UNIT_REAL_GET.put(id("umvr"), (n, u) -> u.getTurnRate());
		UNIT_REAL_SET.put(id("umvr"), (n, u, v) -> {
			u.setTurnRate(v);
			return true;
		});
		UNIT_REAL_GET.put(id("uacq"), (n, u) -> u.getAcquisitionRange());
		UNIT_REAL_SET.put(id("uacq"), (n, u, v) -> {
			// SetUnitAcquireRange ignores a range of 50 or less (measured on 1.29); the field setter shares its rule.
			if (v <= 50) {
				return false;
			}
			u.setAcquisitionRange(v);
			return true;
		});
		UNIT_REAL_GET.put(id("ufyh"), (n, u) -> u.getFlyHeight());
		UNIT_REAL_SET.put(id("ufyh"), (n, u, v) -> {
			n.simulation.setUnitFlyHeight(u, v, 0f);
			return true;
		});
		UNIT_REAL_GET.put(id("udfc"), (n, u) -> u.getDefense());
		UNIT_REAL_SET.put(id("udfc"), (n, u, v) -> {
			u.setTotalDefense(v);
			return true;
		});
		UNIT_REAL_GET.put(id("usir"), (n, u) -> u.getCurrentSightRadius(n.simulation));
		UNIT_REAL_GET.put(id("upri"), (n, u) -> u.getUnitType().getPriority());
		UNIT_REAL_GET.put(id("udtm"), (n, u) -> u.getUnitType().getDeathTime());
		UNIT_REAL_GET.put(id("ucpt"), (n, u) -> u.getUnitType().getCastPoint());
		UNIT_REAL_GET.put(id("ucbs"), (n, u) -> u.getUnitType().getCastBackswingPoint());
		UNIT_REAL_GET.put(id("uamn"), (n, u) -> u.getUnitType().getMinimumAttackRange());
		UNIT_REAL_GET.put(id("ustp"), (n, u) -> u.getUnitType().getStrengthPerLevel());
		UNIT_REAL_GET.put(id("uagp"), (n, u) -> u.getUnitType().getAgilityPerLevel());
		UNIT_REAL_GET.put(id("uinp"), (n, u) -> u.getUnitType().getIntelligencePerLevel());

		UNIT_BOOL_GET.put(id("urai"), (n, u) -> u.isRaisable());
		UNIT_BOOL_SET.put(id("urai"), (n, u, v) -> {
			u.setRaisable(v);
			return true;
		});
		UNIT_BOOL_GET.put(id("udec"), (n, u) -> u.isDecays());
		UNIT_BOOL_SET.put(id("udec"), (n, u, v) -> {
			u.setDecays(v);
			return true;
		});
		UNIT_BOOL_GET.put(id("ubdg"), (n, u) -> u.getUnitType().isBuilding());
		UNIT_BOOL_GET.put(id("ulos"), (n, u) -> u.getUnitType().isExtendedLineOfSight());
		UNIT_BOOL_GET.put(id("unbm"), (n, u) -> u.getUnitType().isNeutralBuildingShowMinimapIcon());

		UNIT_STRING_GET.put(id("unam"), (n, u) -> u.getName());
		UNIT_STRING_SET.put(id("unam"), (n, u, v) -> {
			u.setName(v);
			return true;
		});
		UNIT_STRING_GET.put(id("upro"), (n, u) -> {
			final CAbilityHero hero = u.getHeroData();
			return hero == null ? "" : hero.getProperName();
		});
		UNIT_STRING_SET.put(id("upro"), (n, u, v) -> {
			final CAbilityHero hero = u.getHeroData();
			if (hero == null) {
				return false;
			}
			hero.setProperName(v);
			return true;
		});

		ITEM_INT_GET.put(id("iuse"), (n, i) -> i.getCharges());
		ITEM_INT_SET.put(id("iuse"), (n, i, v) -> {
			i.setCharges(v);
			return true;
		});
		ITEM_INT_GET.put(id("ilev"), (n, i) -> i.getItemType().getLevel());
		ITEM_INT_GET.put(id("ihtp"), (n, i) -> i.getItemType().getMaxLife());
		ITEM_INT_GET.put(id("ihpc"), (n, i) -> Math.round(i.getLife()));
		ITEM_INT_SET.put(id("ihpc"), (n, i, v) -> {
			i.setLife(n.simulation, v);
			return true;
		});
		// The name and the text belong to the item: another item of the same type keeps what its type says.
		ITEM_STRING_GET.put(id("unam"), (n, i) -> {
			final ItemUI ui = n.itemUI(i);
			return i.getNameOverride() != null ? i.getNameOverride() : ui == null ? "" : ui.getName();
		});
		ITEM_STRING_SET.put(id("unam"), (n, i, v) -> {
			i.setNameOverride(v);
			return true;
		});
		ITEM_STRING_GET.put(id("ides"), (n, i) -> {
			final ItemUI ui = n.itemUI(i);
			return i.getDescriptionOverride() != null ? i.getDescriptionOverride()
					: ui == null ? "" : ui.getDescription();
		});
		ITEM_STRING_SET.put(id("ides"), (n, i, v) -> {
			i.setDescriptionOverride(v);
			return true;
		});
		ITEM_INT_GET.put(id("icid"), (n, i) -> i.getItemType().getCooldownGroup() == null ? 0
				: i.getItemType().getCooldownGroup().getValue());
		ITEM_INT_GET.put(id("ipri"), (n, i) -> i.getItemType().getPriority());
		ITEM_INT_GET.put(id("iarm"), (n, i) -> {
			final int index = ITEM_ARMOR_TYPES.indexOf(String.valueOf(i.getItemType().getArmorType()).toLowerCase(Locale.ROOT));
			return Math.max(0, index);
		});
		tintItemField("iclr", 0);
		tintItemField("iclg", 1);
		tintItemField("iclb", 2);
		tintItemField("ical", 3);
		ITEM_REAL_GET.put(id("isca"), (n, i) -> i.getModelScale());
		ITEM_REAL_SET.put(id("isca"), (n, i, v) -> {
			i.setModelScale(v);
			n.render.post(RenderSide.WORLD, () -> {
				final RenderItem peer = n.viewer.getRenderPeer(i);
				if (peer != null) {
					peer.getInstance().setUniformScale(v);
				}
			});
			return true;
		});
		ITEM_BOOL_GET.put(id("idrp"), (n, i) -> i.isDropOnDeath());
		ITEM_BOOL_SET.put(id("idrp"), (n, i, v) -> {
			i.setDropOnDeath(v);
			return true;
		});
		ITEM_BOOL_GET.put(id("idro"), (n, i) -> i.isDroppable());
		ITEM_BOOL_SET.put(id("idro"), (n, i, v) -> {
			i.setDroppable(v);
			return true;
		});
		ITEM_BOOL_GET.put(id("iper"), (n, i) -> i.isPerishable());
		ITEM_BOOL_SET.put(id("iper"), (n, i, v) -> {
			i.setPerishable(v);
			return true;
		});
		ITEM_BOOL_GET.put(id("iprn"), (n, i) -> i.isIncludeAsRandomChoice());
		ITEM_BOOL_SET.put(id("iprn"), (n, i, v) -> {
			i.setIncludeAsRandomChoice(v);
			return true;
		});
		ITEM_BOOL_GET.put(id("ipow"), (n, i) -> i.isUseAutomaticallyWhenAcquired());
		ITEM_BOOL_SET.put(id("ipow"), (n, i, v) -> {
			i.setUseAutomaticallyWhenAcquired(v);
			return true;
		});
		ITEM_BOOL_GET.put(id("ipaw"), (n, i) -> i.isPawnable());
		ITEM_BOOL_SET.put(id("ipaw"), (n, i, v) -> {
			i.setPawnable(v);
			return true;
		});
		ITEM_BOOL_GET.put(id("iusa"), (n, i) -> i.isActivelyUsed());
		ITEM_BOOL_SET.put(id("iusa"), (n, i, v) -> {
			i.setActivelyUsed(v);
			return true;
		});
		ITEM_STRING_GET.put(id("iico"), (n, i) -> {
			final ItemUI ui = n.itemUI(i);
			return ui == null ? "" : ui.getIconUI().getIconPath();
		});
	}

	private final JassProgram program;
	private final War3MapViewer viewer;
	private final CSimulation simulation;

	private final RenderSide render;

	private ObjectFieldNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
		this.simulation = viewer.simulation;
		this.render = RenderSide.of(viewer);
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final ObjectFieldNatives natives = new ObjectFieldNatives(program, viewer);
		natives.registerUnitFields();
		natives.registerItemFields();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private ItemUI itemUI(final CItem item) {
		final AbilityDataUI dataUI = this.viewer.getAbilityDataUI();
		return (item == null) || (dataUI == null) ? null : dataUI.getItemUI(item.getTypeId());
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static int field(final List<JassValue> arguments, final int index) {
		final Object constant = ObjectFieldNatives.<Object>object(arguments, index);
		return constant instanceof ConvertedConstant ? ((ConvertedConstant) constant).getValue() : 0;
	}

	private String mapString(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		final String text = value == null ? null : value.visit(StringJassValueVisitor.getInstance());
		return text == null ? "" : this.viewer.getGameUI().getTrigStr(text);
	}

	private static CUnit unit(final List<JassValue> arguments) {
		return CommonNatives.inGame(ObjectFieldNatives.<CUnit>object(arguments, 0));
	}

	private void registerUnitFields() {
		add("BlzGetUnitIntegerField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final IntGet<CUnit> getter = UNIT_INT_GET.get(field(arguments, 1));
			return IntegerJassValue.of((unit == null) || (getter == null) ? 0 : getter.get(this, unit));
		});
		add("BlzSetUnitIntegerField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final IntSet<CUnit> setter = UNIT_INT_SET.get(field(arguments, 1));
			return BooleanJassValue.of((unit != null) && (setter != null)
					&& setter.set(this, unit, arguments.get(2).visit(IntegerJassValueVisitor.getInstance())));
		});
		add("BlzGetUnitRealField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final RealGet<CUnit> getter = UNIT_REAL_GET.get(field(arguments, 1));
			return RealJassValue.of((unit == null) || (getter == null) ? 0 : getter.get(this, unit));
		});
		add("BlzSetUnitRealField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final RealSet<CUnit> setter = UNIT_REAL_SET.get(field(arguments, 1));
			return BooleanJassValue.of((unit != null) && (setter != null) && setter.set(this, unit,
					arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue()));
		});
		add("BlzGetUnitBooleanField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final BoolGet<CUnit> getter = UNIT_BOOL_GET.get(field(arguments, 1));
			return BooleanJassValue.of((unit != null) && (getter != null) && getter.get(this, unit));
		});
		add("BlzSetUnitBooleanField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final BoolSet<CUnit> setter = UNIT_BOOL_SET.get(field(arguments, 1));
			return BooleanJassValue.of((unit != null) && (setter != null)
					&& setter.set(this, unit, arguments.get(2).visit(BooleanJassValueVisitor.getInstance())));
		});
		add("BlzGetUnitStringField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final StringGet<CUnit> getter = UNIT_STRING_GET.get(field(arguments, 1));
			final String value = (unit == null) || (getter == null) ? "" : getter.get(this, unit);
			return new StringJassValue(value == null ? "" : value);
		});
		add("BlzSetUnitStringField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = unit(arguments);
			final StringSet<CUnit> setter = UNIT_STRING_SET.get(field(arguments, 1));
			return BooleanJassValue
					.of((unit != null) && (setter != null) && setter.set(this, unit, mapString(arguments, 2)));
		});
	}

	private void registerItemFields() {
		add("BlzGetItemRealField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final RealGet<CItem> getter = ITEM_REAL_GET.get(field(arguments, 1));
			return RealJassValue.of((item == null) || (getter == null) ? 0 : getter.get(this, item));
		});
		add("BlzSetItemRealField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final RealSet<CItem> setter = ITEM_REAL_SET.get(field(arguments, 1));
			return BooleanJassValue.of((item != null) && (setter != null) && setter.set(this, item,
					arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue()));
		});
		add("BlzGetItemBooleanField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final BoolGet<CItem> getter = ITEM_BOOL_GET.get(field(arguments, 1));
			return BooleanJassValue.of((item != null) && (getter != null) && getter.get(this, item));
		});
		add("BlzSetItemBooleanField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final BoolSet<CItem> setter = ITEM_BOOL_SET.get(field(arguments, 1));
			return BooleanJassValue.of((item != null) && (setter != null)
					&& setter.set(this, item, arguments.get(2).visit(BooleanJassValueVisitor.getInstance())));
		});
		add("BlzGetItemIntegerField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final IntGet<CItem> getter = ITEM_INT_GET.get(field(arguments, 1));
			return IntegerJassValue.of((item == null) || (getter == null) ? 0 : getter.get(this, item));
		});
		add("BlzSetItemIntegerField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final IntSet<CItem> setter = ITEM_INT_SET.get(field(arguments, 1));
			return BooleanJassValue.of((item != null) && (setter != null)
					&& setter.set(this, item, arguments.get(2).visit(IntegerJassValueVisitor.getInstance())));
		});
		add("BlzGetItemStringField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final StringGet<CItem> getter = ITEM_STRING_GET.get(field(arguments, 1));
			final String value = (item == null) || (getter == null) ? "" : getter.get(this, item);
			return new StringJassValue(value == null ? "" : value);
		});
		add("BlzSetItemStringField", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final StringSet<CItem> setter = ITEM_STRING_SET.get(field(arguments, 1));
			return BooleanJassValue
					.of((item != null) && (setter != null) && setter.set(this, item, mapString(arguments, 2)));
		});
	}
}
