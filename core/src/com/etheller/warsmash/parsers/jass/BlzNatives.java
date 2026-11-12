package com.etheller.warsmash.parsers.jass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericAliasedAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/**
 * The Blz natives that read and change the texts, icons and button places of abilities and items, the mana cost
 * and cooldown of one unit's ability, plus the few small ones around them. Level numbers are 1-based; what each
 * native does with a level it does not have, an empty text or a place off the card is measured against 1.29.
 */
final class BlzNatives {
	private static final String MISSING_TIP = "Tool tip missing!";
	private static final String MISSING_ICON = "ReplaceableTextures\\CommandButtons\\BTNTemp.blp";
	private static final int MAX_BUTTON_X = 3;
	private static final int MAX_BUTTON_Y = 2;

	private final JassProgram program;
	private final War3MapViewer viewer;
	private final CSimulation simulation;
	// The original keeps a button place it was given for a rawcode that has no ability data.
	private final Map<Integer, Integer> loosePlaceX = new HashMap<>();
	private final Map<Integer, Integer> loosePlaceY = new HashMap<>();
	private final Map<Integer, Integer> looseActivatedPlaceX = new HashMap<>();
	private final Map<Integer, Integer> looseActivatedPlaceY = new HashMap<>();

	private BlzNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
		this.simulation = viewer.simulation;
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final BlzNatives natives = new BlzNatives(program, viewer);
		natives.registerAbilityTexts();
		natives.registerAbilityIconsAndPlaces();
		natives.registerAbilityCosts();
		natives.registerItems();
		natives.registerUnits();
		natives.registerPlayerAndInput();
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

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	private static float real(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static StringJassValue text(final String value) {
		return new StringJassValue(value == null ? "" : value);
	}

	private AbilityUI abilityUI(final int rawcode) {
		final AbilityDataUI dataUI = this.viewer.getAbilityDataUI();
		return dataUI == null ? null : dataUI.getUI(new War3ID(rawcode));
	}

	private String mapString(final String value) {
		return value == null ? null : this.viewer.getGameUI().getTrigStr(value);
	}

	/** A read with a level the ability does not have gives the last level's value. */
	private static IconUI levelIcon(final List<IconUI> icons, final int level) {
		if (icons.isEmpty()) {
			return null;
		}
		return icons.get((level >= 1) && (level <= icons.size()) ? level - 1 : icons.size() - 1);
	}

	private static String orMissing(final String tip) {
		return (tip == null) || tip.isEmpty() ? MISSING_TIP : tip;
	}

	private void registerAbilityTexts() {
		add("BlzGetAbilityTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final IconUI icon = ui == null ? null : levelIcon(ui.getOnIconUIs(), integer(arguments, 1));
			return text(icon == null ? MISSING_TIP : orMissing(icon.getToolTip()));
		});
		add("BlzGetAbilityExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final IconUI icon = ui == null ? null : levelIcon(ui.getOnIconUIs(), integer(arguments, 1));
			return text(icon == null ? "" : icon.getUberTip());
		});
		// The learn and the off texts exist once per ability, so any level reads them.
		add("BlzGetAbilityResearchTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			return text(ui == null ? MISSING_TIP : orMissing(ui.getLearnIconUI().getToolTip()));
		});
		add("BlzGetAbilityResearchExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			return text(ui == null ? "" : ui.getLearnIconUI().getUberTip());
		});
		add("BlzGetAbilityActivatedTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final IconUI icon = ui == null ? null : levelIcon(ui.getOffIconUIs(), 1);
			return text(icon == null ? MISSING_TIP : orMissing(icon.getToolTip()));
		});
		add("BlzGetAbilityActivatedExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final IconUI icon = ui == null ? null : levelIcon(ui.getOffIconUIs(), 1);
			return text(icon == null ? "" : icon.getUberTip());
		});

		add("BlzSetAbilityTooltip", (arguments, globalScope, triggerScope) -> {
			final IconUI icon = leveledIcon(arguments);
			final String tip = mapString(string(arguments, 1));
			if ((icon != null) && (tip != null) && !tip.isEmpty()) {
				icon.setToolTip(tip);
			}
			return null;
		});
		add("BlzSetAbilityExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final IconUI icon = leveledIcon(arguments);
			final String tip = mapString(string(arguments, 1));
			if ((icon != null) && (tip != null) && !tip.isEmpty()) {
				icon.setUberTip(tip);
			}
			return null;
		});
		add("BlzSetAbilityResearchTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final String tip = mapString(string(arguments, 1));
			if ((ui != null) && (integer(arguments, 2) == 1) && (tip != null) && !tip.isEmpty()) {
				ui.getLearnIconUI().setToolTip(tip);
			}
			return null;
		});
		add("BlzSetAbilityResearchExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final String tip = mapString(string(arguments, 1));
			if ((ui != null) && (integer(arguments, 2) == 1) && (tip != null) && !tip.isEmpty()) {
				ui.getLearnIconUI().setUberTip(tip);
			}
			return null;
		});
		// Measured: only an ability that already has an off text takes a new one (a toggle such as Immolation).
		add("BlzSetAbilityActivatedTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final String tip = mapString(string(arguments, 1));
			if ((ui != null) && (integer(arguments, 2) == 1) && (tip != null) && !tip.isEmpty()
					&& !ui.getOffIconUIs().isEmpty() && !orMissing(ui.getOffIconUIs().get(0).getToolTip())
							.equals(MISSING_TIP)) {
				for (final IconUI icon : ui.getOffIconUIs()) {
					icon.setToolTip(tip);
				}
			}
			return null;
		});
		add("BlzSetAbilityActivatedExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final String tip = mapString(string(arguments, 1));
			if ((ui != null) && (integer(arguments, 2) == 1) && (tip != null) && !tip.isEmpty()
					&& !ui.getOffIconUIs().isEmpty() && !ui.getOffIconUIs().get(0).getUberTip().isEmpty()) {
				for (final IconUI icon : ui.getOffIconUIs()) {
					icon.setUberTip(tip);
				}
			}
			return null;
		});
	}

	/** The icon of a level for the setters that take the ability and a level: only a level the ability has. */
	private IconUI leveledIcon(final List<JassValue> arguments) {
		final AbilityUI ui = abilityUI(integer(arguments, 0));
		final int level = integer(arguments, 2);
		if ((ui == null) || (level < 1) || (level > ui.getLevelCount())) {
			return null;
		}
		return ui.getOnIconUIs().get(level - 1);
	}

	private void registerAbilityIconsAndPlaces() {
		add("BlzGetAbilityIcon", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final IconUI icon = ui == null ? null : levelIcon(ui.getOnIconUIs(), 1);
			return text(icon == null ? MISSING_ICON : iconPath(icon));
		});
		add("BlzGetAbilityActivatedIcon", (arguments, globalScope, triggerScope) -> {
			final AbilityUI ui = abilityUI(integer(arguments, 0));
			final IconUI icon = ui == null ? null : levelIcon(ui.getOffIconUIs(), 1);
			return text(icon == null ? MISSING_ICON : iconPath(icon));
		});
		add("BlzSetAbilityIcon", (arguments, globalScope, triggerScope) -> {
			setIcons(abilityUI(integer(arguments, 0)), string(arguments, 1), false);
			return null;
		});
		add("BlzSetAbilityActivatedIcon", (arguments, globalScope, triggerScope) -> {
			setIcons(abilityUI(integer(arguments, 0)), string(arguments, 1), true);
			return null;
		});

		add("BlzGetAbilityPosX", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(place(integer(arguments, 0), false, false)));
		add("BlzGetAbilityPosY", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(place(integer(arguments, 0), true, false)));
		add("BlzGetAbilityActivatedPosX", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(place(integer(arguments, 0), false, true)));
		add("BlzGetAbilityActivatedPosY", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(place(integer(arguments, 0), true, true)));
		add("BlzSetAbilityPosX", (arguments, globalScope, triggerScope) -> {
			setPlace(integer(arguments, 0), integer(arguments, 1), false, false);
			return null;
		});
		add("BlzSetAbilityPosY", (arguments, globalScope, triggerScope) -> {
			setPlace(integer(arguments, 0), integer(arguments, 1), true, false);
			return null;
		});
		add("BlzSetAbilityActivatedPosX", (arguments, globalScope, triggerScope) -> {
			setPlace(integer(arguments, 0), integer(arguments, 1), false, true);
			return null;
		});
		add("BlzSetAbilityActivatedPosY", (arguments, globalScope, triggerScope) -> {
			setPlace(integer(arguments, 0), integer(arguments, 1), true, true);
			return null;
		});
	}

	private static String iconPath(final IconUI icon) {
		final String path = icon.getIconPath();
		return (path == null) || path.isEmpty() ? MISSING_ICON : path;
	}

	private void setIcons(final AbilityUI ui, final String path, final boolean activated) {
		if ((ui == null) || (path == null) || path.isEmpty()) {
			return;
		}
		final AbilityDataUI dataUI = this.viewer.getAbilityDataUI();
		for (final IconUI icon : activated ? ui.getOffIconUIs() : ui.getOnIconUIs()) {
			dataUI.setIconPath(icon, path);
		}
	}

	private int place(final int rawcode, final boolean y, final boolean activated) {
		final AbilityUI ui = abilityUI(rawcode);
		if (ui != null) {
			final IconUI icon = levelIcon(activated ? ui.getOffIconUIs() : ui.getOnIconUIs(), 1);
			if (icon != null) {
				return y ? icon.getButtonPositionY() : icon.getButtonPositionX();
			}
		}
		final Map<Integer, Integer> loose = activated ? (y ? this.looseActivatedPlaceY : this.looseActivatedPlaceX)
				: (y ? this.loosePlaceY : this.loosePlaceX);
		return loose.getOrDefault(rawcode, 0);
	}

	private void setPlace(final int rawcode, final int value, final boolean y, final boolean activated) {
		if ((value < 0) || (value > (y ? MAX_BUTTON_Y : MAX_BUTTON_X))) {
			return;
		}
		final AbilityUI ui = abilityUI(rawcode);
		if (ui == null) {
			(activated ? (y ? this.looseActivatedPlaceY : this.looseActivatedPlaceX)
					: (y ? this.loosePlaceY : this.loosePlaceX)).put(rawcode, value);
			return;
		}
		for (final IconUI icon : activated ? ui.getOffIconUIs() : ui.getOnIconUIs()) {
			if (y) {
				icon.setButtonPositionY(value);
			}
			else {
				icon.setButtonPositionX(value);
			}
		}
	}

	private int levelCount(final int rawcode) {
		return Math.max(0, this.simulation.getAbilityData().getLevelCount(new War3ID(rawcode)));
	}

	private GameObject abilityData(final int rawcode) {
		return this.viewer.getAllObjectData().getAbilities().get(new War3ID(rawcode));
	}

	private int dataManaCost(final int rawcode, final int level) {
		final GameObject data = abilityData(rawcode);
		if ((data == null) || (level < 1) || (level > levelCount(rawcode))) {
			return 0;
		}
		return data.getFieldAsInteger(AbilityFields.MANA_COST + level, 0);
	}

	private float dataCooldown(final int rawcode, final int level) {
		final GameObject data = abilityData(rawcode);
		if ((data == null) || (level < 1) || (level > levelCount(rawcode))) {
			return 0;
		}
		return data.getFieldAsFloat(AbilityFields.COOLDOWN + level, 0);
	}

	private AbstractGenericAliasedAbility levelingAbility(final CUnit unit, final int rawcode) {
		if ((unit == null) || unit.isRemovedFromGame()) {
			return null;
		}
		final CAbility ability = unit.getAbilityByRawcode(new War3ID(rawcode));
		return ability instanceof AbstractGenericAliasedAbility ? (AbstractGenericAliasedAbility) ability : null;
	}

	private void registerAbilityCosts() {
		add("BlzGetAbilityManaCost", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(dataManaCost(integer(arguments, 0), integer(arguments, 1))));
		add("BlzGetAbilityCooldown", (arguments, globalScope, triggerScope) -> RealJassValue
				.of(dataCooldown(integer(arguments, 0), integer(arguments, 1))));
		add("BlzGetUnitAbilityManaCost", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final int rawcode = integer(arguments, 1);
			final int level = integer(arguments, 2);
			if (!hasAbility(unit, rawcode)) {
				return IntegerJassValue.ZERO;
			}
			final AbstractGenericAliasedAbility ability = levelingAbility(unit, rawcode);
			final Integer override = ability == null ? null : ability.getManaCostOverride(level);
			return IntegerJassValue.of(override != null ? override : dataManaCost(rawcode, level));
		});
		add("BlzGetUnitAbilityCooldown", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final int rawcode = integer(arguments, 1);
			final int level = integer(arguments, 2);
			if (!hasAbility(unit, rawcode)) {
				return RealJassValue.ZERO;
			}
			final AbstractGenericAliasedAbility ability = levelingAbility(unit, rawcode);
			final Float override = ability == null ? null : ability.getCooldownOverride(level);
			return RealJassValue.of(override != null ? override : dataCooldown(rawcode, level));
		});
		// Measured: a level the ability does not have is ignored, a negative mana cost reads 0 and a negative
		// cooldown reads back as given (casting treats it as none).
		add("BlzSetUnitAbilityManaCost", (arguments, globalScope, triggerScope) -> {
			final AbstractGenericAliasedAbility ability = levelingAbility(object(arguments, 0), integer(arguments, 1));
			final int level = integer(arguments, 2);
			if ((ability != null) && (level >= 1) && (level <= levelCount(integer(arguments, 1)))) {
				ability.overrideManaCost(level, Math.max(0, integer(arguments, 3)));
			}
			return null;
		});
		add("BlzSetUnitAbilityCooldown", (arguments, globalScope, triggerScope) -> {
			final AbstractGenericAliasedAbility ability = levelingAbility(object(arguments, 0), integer(arguments, 1));
			final int level = integer(arguments, 2);
			if ((ability != null) && (level >= 1) && (level <= levelCount(integer(arguments, 1)))) {
				ability.overrideCooldown(level, real(arguments, 3));
			}
			return null;
		});
	}

	private static CommonTriggerExecutionScope mouseScope(final TriggerExecutionScope triggerScope) {
		if (triggerScope instanceof CommonTriggerExecutionScope) {
			final CommonTriggerExecutionScope scope = (CommonTriggerExecutionScope) triggerScope;
			final JassGameEventsWar3 event = scope.getTriggerEventId();
			if ((event == JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN)
					|| (event == JassGameEventsWar3.EVENT_PLAYER_MOUSE_UP)
					|| (event == JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE)) {
				return scope;
			}
		}
		return null;
	}

	private boolean hasAbility(final CUnit unit, final int rawcode) {
		return (unit != null) && !unit.isRemovedFromGame() && (unit.getAbilityByRawcode(new War3ID(rawcode)) != null);
	}

	/**
	 * Items share what the original keeps per item type: a name, text or icon set on one item shows on every item of
	 * its type, and (measured) an empty text is accepted.
	 */
	private ItemUI itemUI(final CItem item) {
		final AbilityDataUI dataUI = this.viewer.getAbilityDataUI();
		return (item == null) || (dataUI == null) ? null
				: dataUI.getItemUI(item.getTypeId());
	}

	private void registerItems() {
		add("BlzGetItemDescription", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			return text(ui == null ? "" : ui.getDescription());
		});
		add("BlzGetItemTooltip", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			return text(ui == null ? "" : ui.getIconUI().getToolTip());
		});
		add("BlzGetItemExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			return text(ui == null ? "" : ui.getIconUI().getUberTip());
		});
		add("BlzGetItemIconPath", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			return text(ui == null ? "" : ui.getIconUI().getIconPath());
		});
		add("BlzSetItemName", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			if (ui != null) {
				ui.setName(nonNull(mapString(string(arguments, 1))));
			}
			return null;
		});
		add("BlzSetItemDescription", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			if (ui != null) {
				ui.setDescription(nonNull(mapString(string(arguments, 1))));
			}
			return null;
		});
		add("BlzSetItemTooltip", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			if (ui != null) {
				ui.getIconUI().setToolTip(nonNull(mapString(string(arguments, 1))));
			}
			return null;
		});
		add("BlzSetItemExtendedTooltip", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			if (ui != null) {
				ui.getIconUI().setUberTip(nonNull(mapString(string(arguments, 1))));
			}
			return null;
		});
		add("BlzSetItemIconPath", (arguments, globalScope, triggerScope) -> {
			final ItemUI ui = itemUI(object(arguments, 0));
			final String path = string(arguments, 1);
			if ((ui != null) && (path != null) && !path.isEmpty()) {
				this.viewer.getAbilityDataUI().setIconPath(ui.getIconUI(), path);
				ui.setItemIconPathForDragging(path);
			}
			return null;
		});
	}

	private static String nonNull(final String value) {
		return value == null ? "" : value;
	}

	private void registerUnits() {
		// Measured: the value is the unit's fly height; a unit on the ground reads 0 whatever the terrain height.
		add("BlzGetLocalUnitZ", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return RealJassValue.of((unit == null) || unit.isRemovedFromGame() ? 0 : unit.getFlyHeight());
		});
		add("BlzDeleteHeroAbility", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if ((unit == null) || unit.isRemovedFromGame()) {
				return null;
			}
			final CAbility ability = unit.getAbilityByRawcode(new War3ID(integer(arguments, 1)));
			if (ability != null) {
				final CAbilityHero hero = unit.getHeroData();
				if (hero != null) {
					hero.deleteAbility(this.simulation, unit, ability);
				}
				else {
					unit.remove(this.simulation, ability);
				}
			}
			return null;
		});
		add("BlzUnitInterruptAttack", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if ((unit != null) && !unit.isRemovedFromGame()) {
				unit.interruptAttack(this.simulation);
			}
			return null;
		});
	}

	private void registerPlayerAndInput() {
		add("BlzDecPlayerTechResearched", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			final War3ID tech = new War3ID(integer(arguments, 1));
			if ((player != null) && (this.simulation.getUpgradeData().getType(tech) != null)) {
				player.decTechResearched(this.simulation, tech, integer(arguments, 2));
			}
			return null;
		});
		// Measured: ConvertMouseButtonType keeps one value per number (0 and numbers past 3 included), and the
		// mouse getters read 0 and null outside a mouse event.
		add("ConvertMouseButtonType", (arguments, globalScope, triggerScope) -> {
			final HandleJassType type = globalScope.getHandleType("mousebuttontype");
			return type.getUnknownEnumValue(integer(arguments, 0));
		});
		add("BlzGetTriggerPlayerMouseX", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope mouse = mouseScope(triggerScope);
			return mouse == null ? RealJassValue.ZERO : RealJassValue.of(mouse.getMouseX());
		});
		add("BlzGetTriggerPlayerMouseY", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope mouse = mouseScope(triggerScope);
			return mouse == null ? RealJassValue.ZERO : RealJassValue.of(mouse.getMouseY());
		});
		add("BlzGetTriggerPlayerMousePosition", (arguments, globalScope, triggerScope) -> {
			final HandleJassType locationType = globalScope.getHandleType("location");
			final CommonTriggerExecutionScope mouse = mouseScope(triggerScope);
			return mouse == null ? locationType.getNullValue()
					: new HandleJassValue(locationType, new AbilityPointTarget(mouse.getMouseX(), mouse.getMouseY()));
		});
		// A move has no button.
		add("BlzGetTriggerPlayerMouseButton", (arguments, globalScope, triggerScope) -> {
			final HandleJassType type = globalScope.getHandleType("mousebuttontype");
			final CommonTriggerExecutionScope mouse = mouseScope(triggerScope);
			return (mouse == null) || (mouse.getMouseButton() == 0) ? type.getNullValue()
					: type.getUnknownEnumValue(mouse.getMouseButton());
		});
		add("AutomationTestStart", (arguments, globalScope, triggerScope) -> null);
		add("AutomationTestEnd", (arguments, globalScope, triggerScope) -> null);
	}
}
