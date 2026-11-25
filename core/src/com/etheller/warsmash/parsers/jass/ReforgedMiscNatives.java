package com.etheller.warsmash.parsers.jass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.badlogic.gdx.graphics.Color;
import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.FogStyle;
import com.etheller.warsmash.viewer5.handlers.w3x.ScriptUiState;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderCommands;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CAbilityAuraBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template.AbstractCAbilityAbilityBuilderAuraTemplate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CheckpointSaves;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.CommandButtonEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;

/**
 * Reforged natives on units, items, text and the game environment that need little of their own: the height of a
 * unit, counted pauses, resetting an attack, aura switches, ability cooldowns as numbers or percent, skins,
 * tag text, the terrain fog, and the ones that only concern a client the engine does not have (a video
 * recorder, the test harness of the original's developers).
 */
final class ReforgedMiscNatives {
	private static final Pattern TAG = Pattern.compile("<([^<>,]+),([^<>,]+)(?:,([^<>]*))?>");

	private final JassProgram program;
	private final War3MapViewer viewer;
	private final CSimulation simulation;
	private int minShadowPointLights;
	private final RenderCommands commands;
	private final SoundVoices voices;

	private ReforgedMiscNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
		this.simulation = viewer.simulation;
		this.commands = LocalUiLink.of(viewer, null).commands();
		this.voices = new SoundVoices(viewer, null, LocalUiLink.of(viewer, null));
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final ReforgedMiscNatives natives = new ReforgedMiscNatives(program, viewer);
		natives.registerUnits();
		natives.registerCooldowns();
		natives.registerSkins();
		natives.registerItems();
		natives.registerText();
		natives.registerEnvironment();
		natives.registerClientOnly();
		natives.registerTriggerState();
		natives.registerWithoutCounterpart();
		natives.registerScriptInterface();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	/** A native that changes the picture or the interface and returns nothing: it runs on the render side in the frame's order. */
	private void addCommand(final String name, final JassFunction function) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final List<JassValue> copy = new ArrayList<>(arguments);
			this.commands.send(() -> function.call(copy, globalScope, triggerScope));
			return null;
		});
	}

	/** A native whose result needs the render side: the render thread runs it, after the commands sent before it. */
	private void addCall(final String name, final JassFunction function) {
		add(name, (arguments, globalScope, triggerScope) -> this.commands.call(name,
				() -> function.call(arguments, globalScope, triggerScope)));
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

	private static boolean bool(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(BooleanJassValueVisitor.getInstance());
	}

	private static int constant(final List<JassValue> arguments, final int index) {
		final Object value = ReforgedMiscNatives.<Object>object(arguments, index);
		return value instanceof ConvertedConstant ? ((ConvertedConstant) value).getValue() : 0;
	}

	private static CUnit inGame(final CUnit unit) {
		return (unit == null) || unit.isRemovedFromGame() ? null : unit;
	}

	private void registerUnits() {
		// The height the unit stands at in the world: the ground under it and its fly height.
		add("BlzGetUnitZ", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit == null) {
				return RealJassValue.ZERO;
			}
			final float ground = this.simulation.getTerrainData().getGroundZ(unit.getX(), unit.getY());
			return RealJassValue.of(ground + unit.getFlyHeight());
		});
		add("BlzPauseUnitEx", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.pauseCounted(bool(arguments, 1), this.simulation);
			}
			return null;
		});
		// The weapon index is the script's; the unit keeps one attack timer, which this makes ready.
		add("BlzResetUnitAttack", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setCooldownEndTime(this.simulation.getGameTurnTick());
			}
			return null;
		});
		// Aura abilities stop giving their effect while disabled; the ones from items and buffs count as well.
		add("BlzUnitEnableAuras", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final boolean enable = bool(arguments, 1);
				for (final CAbility ability : unit.getAbilities()) {
					if ((ability instanceof CAbilityAuraBase)
							|| (ability instanceof AbstractCAbilityAbilityBuilderAuraTemplate)) {
						ability.setDisabled(!enable, CAbilityDisableType.DISABLED_EFFECT);
					}
				}
			}
			return null;
		});
	}

	private static War3ID cooldownKey(final CUnit unit, final War3ID alias) {
		final CAbility ability = unit.getAbilityByRawcode(alias);
		return ability == null ? alias : ability.getCode();
	}

	/** The cooldown that is running under this ability, by its alias or the code it is made from. */
	private War3ID runningKey(final CUnit unit, final War3ID alias) {
		final War3ID code = cooldownKey(unit, alias);
		return unit.getCooldownRemainingTicks(this.simulation, code) > unit.getCooldownRemainingTicks(this.simulation,
				alias) ? code : alias;
	}

	private float remainingSeconds(final CUnit unit, final War3ID alias) {
		return unit.getCooldownRemainingTicks(this.simulation, runningKey(unit, alias))
				* WarsmashConstants.SIMULATION_STEP_TIME;
	}

	private float lengthSeconds(final CUnit unit, final War3ID alias) {
		return unit.getCooldownLengthDisplayTicks(this.simulation, runningKey(unit, alias))
				* WarsmashConstants.SIMULATION_STEP_TIME;
	}

	private void setRemaining(final CUnit unit, final War3ID alias, final float seconds) {
		unit.beginCooldown(this.simulation, runningKey(unit, alias), Math.max(0f, seconds));
	}

	/** The cooldown natives read the running cooldown, and a change starts one of the new remaining time. */
	private void registerCooldowns() {
		// Percent runs 0 to 100, like the other percent natives (GetUnitLifePercent).
		add("BlzGetUnitAbilityCooldownPercent", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit == null) {
				return RealJassValue.ZERO;
			}
			final War3ID alias = new War3ID(integer(arguments, 1));
			final float length = lengthSeconds(unit, alias);
			return RealJassValue.of(length <= 0 ? 0 : (remainingSeconds(unit, alias) * 100f) / length);
		});
		add("BlzSetUnitAbilityCooldownRemaining", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				setRemaining(unit, new War3ID(integer(arguments, 1)), real(arguments, 2));
			}
			return null;
		});
		add("BlzSetUnitAbilityCooldownPercent", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final War3ID alias = new War3ID(integer(arguments, 1));
				setRemaining(unit, alias, (lengthSeconds(unit, alias) * real(arguments, 2)) / 100f);
			}
			return null;
		});
		add("BlzAdjustUnitAbilityCooldownRemaining", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final War3ID alias = new War3ID(integer(arguments, 1));
				setRemaining(unit, alias, remainingSeconds(unit, alias) + real(arguments, 2));
			}
			return null;
		});
		add("BlzAdjustUnitAbilityCooldownPercent", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final War3ID alias = new War3ID(integer(arguments, 1));
				setRemaining(unit, alias,
						remainingSeconds(unit, alias) + ((lengthSeconds(unit, alias) * real(arguments, 2)) / 100f));
			}
			return null;
		});
	}

	private void registerSkins() {
		add("BlzGetUnitSkin", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit == null) {
				return IntegerJassValue.ZERO;
			}
			final War3ID skin = unit.getScriptSkinId();
			return IntegerJassValue.of((skin != null ? skin : unit.getTypeId()).getValue());
		});
		// The unit keeps its type and every rule of it; only the drawn model, selection size and sounds are those of the
		// skin's unit type. An id that is no unit type keeps the skin on record without redrawing.
		add("BlzSetUnitSkin", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final War3ID skin = new War3ID(integer(arguments, 1));
				unit.setScriptSkinId(skin);
				this.commands.send(() -> this.viewer.setUnitSkin(unit, skin));
			}
			return null;
		});
		add("BlzGetItemSkin", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			return IntegerJassValue.of(item == null ? 0 : item.getSkinId().getValue());
		});
		add("BlzSetItemSkin", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			if (item != null) {
				final War3ID skin = new War3ID(integer(arguments, 1));
				// The render side shows the skin's model with the skin's own color and size.
				final CItemType skinType = this.simulation.getItemData().getItemType(skin);
				if (skinType != null) {
					item.setSkinId(skin);
					item.setSkinLook(skinType);
					this.commands.send(() -> this.viewer.setItemSkin(item, skin));
				}
			}
			return null;
		});
	}

	private static CommonTriggerExecutionScope eventScope(final TriggerExecutionScope scope) {
		return (scope instanceof CommonTriggerExecutionScope) ? (CommonTriggerExecutionScope) scope : null;
	}

	private static JassValue itemValue(final GlobalScope globalScope,
			final CItem item) {
		final HandleJassType type = globalScope.getHandleType("item");
		return item == null ? type.getNullValue() : new HandleJassValue(type, item);
	}

	/** The abilities of an item lying on the ground are made when a script first asks; a carried item's are the carrier's. */
	private List<CAbility> itemAbilities(final CItem item) {
		final CAbilityInventory inventory = item.getContainedInventory();
		if (inventory != null) {
			return inventory.getItemAbilities(item);
		}
		if (item.getGroundAbilities() == null) {
			final List<CAbility> made = new ArrayList<>();
			for (final War3ID abilityId : item.getAbilityIds()) {
				final CAbility ability = createGroundAbility(item, abilityId);
				if (ability != null) {
					made.add(ability);
				}
			}
			item.setGroundAbilities(made);
		}
		return item.getGroundAbilities();
	}

	private CAbility createGroundAbility(final CItem item, final War3ID abilityId) {
		final CAbilityType<?> type = this.simulation.getAbilityData().getAbilityType(abilityId);
		if (type == null) {
			return null;
		}
		final CAbility ability = type.createAbility(this.simulation.getHandleIdAllocator().createId());
		ability.setIconShowing(false);
		ability.setItemAbility(item, -1);
		return ability;
	}

	private void registerItems() {
		final JassFunction itemAbilityByIndex = (arguments, globalScope, triggerScope) -> {
			final HandleJassType abilityType = globalScope.getHandleType("ability");
			final CItem item = object(arguments, 0);
			final int index = integer(arguments, 1);
			if (item == null) {
				return abilityType.getNullValue();
			}
			final List<CAbility> abilities = itemAbilities(item);
			return ((index < 0) || (index >= abilities.size())) ? abilityType.getNullValue()
					: new HandleJassValue(abilityType, abilities.get(index));
		};
		add("BlzGetItemAbilityByIndex", itemAbilityByIndex);
		add("BlzGetItemAbility", (arguments, globalScope, triggerScope) -> {
			final HandleJassType abilityType = globalScope.getHandleType("ability");
			final CItem item = object(arguments, 0);
			if (item != null) {
				final War3ID abilityId = new War3ID(integer(arguments, 1));
				for (final CAbility ability : itemAbilities(item)) {
					if (abilityId.equals(ability.getAlias())) {
						return new HandleJassValue(abilityType, ability);
					}
				}
			}
			return abilityType.getNullValue();
		});
		add("BlzItemAddAbility", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final War3ID abilityId = new War3ID(integer(arguments, 1));
			if ((item == null) || (this.simulation.getAbilityData().getAbilityType(abilityId) == null)
					|| !item.addAbilityId(abilityId)) {
				return BooleanJassValue.FALSE;
			}
			final CAbilityInventory inventory = item.getContainedInventory();
			if (inventory != null) {
				inventory.addItemAbility(this.simulation, item.getContainedUnit(), item, abilityId);
			}
			else if (item.getGroundAbilities() != null) {
				final CAbility ability = createGroundAbility(item, abilityId);
				if (ability != null) {
					item.getGroundAbilities().add(ability);
				}
			}
			return BooleanJassValue.TRUE;
		});
		add("BlzItemRemoveAbility", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			final War3ID abilityId = new War3ID(integer(arguments, 1));
			if ((item == null) || !item.removeAbilityId(abilityId)) {
				return BooleanJassValue.FALSE;
			}
			final CAbilityInventory inventory = item.getContainedInventory();
			if (inventory != null) {
				inventory.removeItemAbility(this.simulation, item.getContainedUnit(), item, abilityId);
			}
			else if (item.getGroundAbilities() != null) {
				item.getGroundAbilities().removeIf(ability -> abilityId.equals(ability.getAlias()));
			}
			return BooleanJassValue.TRUE;
		});
		// Read in a pickup event: the held item that took in every charge of the picked up one, null when it was not
		// absorbed.
		add("BlzGetAbsorbingItem", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = eventScope(triggerScope);
			return itemValue(globalScope, scope == null ? null : scope.getAbsorbingItem());
		});
		add("BlzGetManipulatedItemWasAbsorbed", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = eventScope(triggerScope);
			return BooleanJassValue.of((scope != null) && (scope.getAbsorbingItem() != null));
		});
		add("BlzGetStackingItemSource", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = eventScope(triggerScope);
			return itemValue(globalScope, scope == null ? null : scope.getStackingItemSource());
		});
		add("BlzGetStackingItemTarget", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = eventScope(triggerScope);
			return itemValue(globalScope, scope == null ? null : scope.getStackingItemTarget());
		});
		add("BlzGetStackingItemTargetPreviousCharges", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = eventScope(triggerScope);
			return IntegerJassValue.of(scope == null ? 0 : scope.getStackingItemTargetPreviousCharges());
		});
	}

	private GameObject abilityData(final String alias) {
		try {
			return this.viewer.getAllObjectData().getAbilities().get(War3ID.fromString(alias));
		}
		catch (final RuntimeException exc) {
			return null;
		}
	}

	private static String number(final float value) {
		return Math.abs(value - Math.round(value)) < 0.0005f ? Integer.toString(Math.round(value))
				: Float.toString(Math.round(value * 100f) / 100f);
	}

	/**
	 * ParseTags: every {@code <ability,column>} of the text becomes the value of that column of the ability data
	 * (Dur1, DataA2, Cool1, ...); a third part "%" gives the value times 100 with a percent sign. Text that
	 * is no tag stays as it is.
	 */
	private void registerText() {
		add("ParseTags", (arguments, globalScope, triggerScope) -> {
			final String tagged = string(arguments, 0);
			if (tagged == null) {
				return new StringJassValue("");
			}
			final Matcher matcher = TAG.matcher(tagged);
			final StringBuffer result = new StringBuffer();
			while (matcher.find()) {
				final GameObject data = abilityData(matcher.group(1).trim());
				String replacement = matcher.group(0);
				if (data != null) {
					final String value = data.getField(matcher.group(2).trim());
					if ((value != null) && !value.isEmpty()) {
						float parsed = Float.NaN;
						try {
							parsed = Float.parseFloat(value.trim());
						}
						catch (final NumberFormatException exc) {
							// Text values are put in as they are.
						}
						if (Float.isNaN(parsed)) {
							replacement = value;
						}
						else if ((matcher.group(3) != null) && matcher.group(3).trim().equals("%")) {
							replacement = number(parsed * 100f) + "%";
						}
						else {
							replacement = number(parsed);
						}
					}
				}
				matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
			}
			matcher.appendTail(result);
			return new StringJassValue(result.toString());
		});
		add("BlzForceHasPlayer", (arguments, globalScope, triggerScope) -> {
			final List<CPlayerJass> force = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			return BooleanJassValue.of((force != null) && (player != null) && force.contains(player));
		});
		// The language this machine reads its game data in. Every machine of a network game can differ, so a script
		// must not let this reach the game state.
		add("BlzGetLocale", (arguments, globalScope, triggerScope) -> new StringJassValue(GameLanguage.inGame()));
		add("BlzConvertColor", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(((integer(arguments, 0) & 0xFF) << 24) | ((integer(arguments, 1) & 0xFF) << 16)
						| ((integer(arguments, 2) & 0xFF) << 8) | (integer(arguments, 3) & 0xFF)));
	}

	private void registerEnvironment() {
		// The one terrain fog of the renderer: the z range and the linear range both set its range.
		addCommand("BlzSetTerrainFogStyle", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.setStyleByIndex(constant(arguments, 0) + 1);
			return null;
		});
		addCommand("BlzSetTerrainFogZStart", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.start = real(arguments, 0);
			return null;
		});
		addCommand("BlzSetTerrainFogZEnd", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.end = real(arguments, 0);
			return null;
		});
		addCommand("BlzSetTerrainFogLinearStart", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.start = real(arguments, 0);
			return null;
		});
		addCommand("BlzSetTerrainFogLinearEnd", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.end = real(arguments, 0);
			return null;
		});
		addCommand("BlzSetTerrainFogDensity", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.density = real(arguments, 0);
			return null;
		});
		addCommand("BlzSetTerrainFogHeightStart", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.heightMin = real(arguments, 0);
			return null;
		});
		addCommand("BlzSetTerrainFogHeightEnd", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.heightMax = real(arguments, 0);
			return null;
		});
		// Same reading as the map's own fog setting: a value outside (0, 1) is the uncapped fog.
		addCommand("BlzSetTerrainFogMaxLinearDensity", (arguments, globalScope, triggerScope) -> {
			final float maxOpacity = real(arguments, 0);
			this.viewer.worldScene.fogSettings.maxOpacity = ((maxOpacity > 0) && (maxOpacity < 1)) ? maxOpacity : 1f;
			return null;
		});
		addCommand("BlzSetTerrainFogDrawOverSky", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.drawOverSky = bool(arguments, 0);
			return null;
		});
		addCommand("SetTerrainFogExV", (arguments, globalScope, triggerScope) -> {
			final FogSettings fog = this.viewer.worldScene.fogSettings;
			fog.setStyleByIndex(integer(arguments, 0) + 1);
			fog.start = real(arguments, 1);
			fog.end = real(arguments, 2);
			fog.density = real(arguments, 3);
			fog.heightMin = real(arguments, 4);
			fog.heightMax = real(arguments, 5);
			final float linearStart = real(arguments, 6);
			final float linearEnd = real(arguments, 7);
			if ((fog.style == FogStyle.LINEAR) && (linearEnd > linearStart) && (linearEnd > 0)) {
				fog.start = linearStart;
				fog.end = linearEnd;
			}
			fog.color = new Color(real(arguments, 8), real(arguments, 9), real(arguments, 10), 1f);
			return null;
		});
		addCommand("BlzSetTerrainFogColor", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.color = new Color(real(arguments, 0), real(arguments, 1),
					real(arguments, 2), 1f);
			return null;
		});
		add("StartSoundEx", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			final int tick = this.simulation.getGameTurnTick();
			// A fade in is not supported: the sound starts at its full volume.
			if (sound != null) {
				this.voices.start(sound, tick);
			}
			return null;
		});
	}

	private void ignored(final String reason, final String... names) {
		for (final String name : names) {
			add(name, (arguments, globalScope, triggerScope) -> null);
		}
		if (reason.isEmpty()) {
			throw new IllegalStateException();
		}
	}

	/** The threads a trigger has running or sleeping, counting the one that asks. */
	private List<com.etheller.interpreter.ast.execution.JassThread> threadsOf(
			final com.etheller.interpreter.ast.scope.trigger.Trigger trigger,
			final com.etheller.interpreter.ast.scope.GlobalScope globalScope) {
		final List<com.etheller.interpreter.ast.execution.JassThread> found = new java.util.ArrayList<>();
		final List<com.etheller.interpreter.ast.execution.JassThread> all = new java.util.ArrayList<>(
				globalScope.getThreads());
		all.addAll(globalScope.getNewThreads());
		for (final com.etheller.interpreter.ast.execution.JassThread thread : all) {
			if ((thread.triggerScope != null) && (thread.triggerScope.getTriggeringTrigger() == trigger)
					&& !thread.isHalted()) {
				found.add(thread);
			}
		}
		return found;
	}

	private void registerTriggerState() {
		add("BlzTriggerIsRunning", (arguments, globalScope, triggerScope) -> {
			final com.etheller.interpreter.ast.scope.trigger.Trigger trigger = object(arguments, 0);
			if (trigger == null) {
				return BooleanJassValue.FALSE;
			}
			return BooleanJassValue.of((triggerScope.getTriggeringTrigger() == trigger)
					|| !threadsOf(trigger, globalScope).isEmpty());
		});
		// The actions of the trigger end where they are: sleeping ones are halted, the running one stops at its
		// next instruction.
		add("BlzTriggerInterrupt", (arguments, globalScope, triggerScope) -> {
			final com.etheller.interpreter.ast.scope.trigger.Trigger trigger = object(arguments, 0);
			if (trigger != null) {
				for (final com.etheller.interpreter.ast.execution.JassThread thread : threadsOf(trigger,
						globalScope)) {
					thread.halt();
				}
			}
			return null;
		});
		add("AllowHeroGlowOnUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setHeroGlowAllowed(true);
			}
			return null;
		});
		add("DisallowHeroGlowOnUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setHeroGlowAllowed(false);
			}
			return null;
		});
		add("HeroGlowIsAllowedOnUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			return BooleanJassValue.of((unit != null) && unit.isHeroGlowAllowed());
		});
	}

	/**
	 * Settings of features this engine does not have: HD water, height fog, point light shadows and camera blockers.
	 * A map that sets them runs on; the switches that read back (point light count) keep what was set.
	 */
	private void registerWithoutCounterpart() {
		ignored("no HD water in the renderer", "SetHDWaterParams", "BlzSetHDWaterColor", "BlzSetHDWaterColorOverride",
				"BlzSetHDWaterVertexDisplacement", "BlzSetHDWaterMinOpacity", "BlzSetHDWaterMaxOpacity",
				"BlzSetHDWaterReflectivity", "BlzSetHDWaterEmissivity", "BlzSetHDWaterEdgeSoftness",
				"BlzSetHDWaterWaveStrength", "BlzSetHDWaterEnvMapStrength");
		ignored("no camera blockers in the camera of this engine", "EnableCameraBlocker", "AddCameraBlocker");
		add("BlzSetMinShadowCastingPointLightCount", (arguments, globalScope, triggerScope) -> {
			this.minShadowPointLights = integer(arguments, 0);
			return null;
		});
		add("BlzGetMinShadowCastingPointLightCount", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(this.minShadowPointLights));
	}

	/**
	 * What a script switches in the picture and the interface of this machine: terrain and sky, the minimap's
	 * terrain, the portrait light, music on a lost focus, glows on command buttons, the team glow and color of
	 * models, and the number of checkpoint saves kept. None of it is read by the simulation.
	 */
	private void registerScriptInterface() {
		final ScriptUiState state = this.viewer.scriptUiState;
		addCommand("BlzShowTerrain", (arguments, globalScope, triggerScope) -> {
			state.setTerrainShown(bool(arguments, 0));
			return null;
		});
		addCommand("BlzShowSkyBox", (arguments, globalScope, triggerScope) -> {
			state.setSkyBoxShown(bool(arguments, 0));
			return null;
		});
		addCall("BlzChangeMinimapTerrainTex", (arguments, globalScope, triggerScope) -> {
			final String path = string(arguments, 0);
			if (!textureExists(path)) {
				return BooleanJassValue.FALSE;
			}
			state.setMinimapTerrainTexture(path);
			return BooleanJassValue.TRUE;
		});
		addCommand("SetPortraitLight", (arguments, globalScope, triggerScope) -> {
			this.viewer.setPortraitLight(string(arguments, 0));
			return null;
		});
		addCommand("BlzPauseThematicMusicOnFocusLost", (arguments, globalScope, triggerScope) -> {
			state.setPauseThematicMusicOnFocusLost(bool(arguments, 0));
			return null;
		});
		addCommand("SetMaxCheckpointSaves", (arguments, globalScope, triggerScope) -> {
			CheckpointSaves.delete(state.getCheckpointSaves().setMax(integer(arguments, 0)));
			return null;
		});
		add("BlzShowUnitTeamGlow", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setTeamGlowShown(bool(arguments, 1));
			}
			return null;
		});
		add("SetItemColor", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			if (item != null) {
				item.setScriptColor(constant(arguments, 1));
			}
			return null;
		});
		add("CreateCommandButtonEffect", (arguments, globalScope, triggerScope) -> newButtonEffect(globalScope,
				CommandButtonEffect.ability(integer(arguments, 0), orderOf(string(arguments, 1)))));
		add("CreateUpgradeCommandButtonEffect", (arguments, globalScope, triggerScope) -> newButtonEffect(globalScope,
				CommandButtonEffect.upgrade(integer(arguments, 0))));
		add("CreateLearnCommandButtonEffect", (arguments, globalScope, triggerScope) -> newButtonEffect(globalScope,
				CommandButtonEffect.learn(integer(arguments, 0))));
		addCommand("DestroyCommandButtonEffect", (arguments, globalScope, triggerScope) -> {
			final CommandButtonEffect effect = object(arguments, 0);
			if (effect != null) {
				state.removeCommandButtonEffect(effect);
			}
			return null;
		});
	}

	private JassValue newButtonEffect(final GlobalScope globalScope, final CommandButtonEffect effect) {
		this.commands.send(() -> this.viewer.scriptUiState.addCommandButtonEffect(effect));
		return new HandleJassValue(globalScope.getHandleType("commandbuttoneffect"), effect);
	}

	/** An order as the script names it: an order string, or the unit it trains or builds; 0 for none. */
	private int orderOf(final String order) {
		if ((order == null) || order.isEmpty()) {
			return 0;
		}
		final int orderId = OrderIdUtils.getOrderId(order);
		if (orderId != 0) {
			return orderId;
		}
		final CUnitType unitType = this.simulation.getUnitData().getUnitTypeByJassLegacyName(order);
		if (unitType != null) {
			return unitType.getTypeId().getValue();
		}
		return order.length() == 4 ? War3ID.fromString(order).getValue() : 0;
	}

	private boolean textureExists(final String path) {
		if ((path == null) || path.isEmpty() || (this.viewer.mapMpq == null)) {
			return false;
		}
		final int dot = path.lastIndexOf('.');
		final String base = dot < 0 ? path : path.substring(0, dot);
		return this.viewer.mapMpq.has(path) || this.viewer.mapMpq.has(base + ".blp")
				|| this.viewer.mapMpq.has(base + ".dds") || this.viewer.mapMpq.has(base + ".tga");
	}

	/** A video recorder and its test harness, neither of which exists here. */
	private void registerClientOnly() {
		ignored("no video recorder in this engine", "BlzStartRecording", "BlzEndRecording");
		ignored("quality assurance test harness of the original", "AutomationSetTestType",
				"AutomationTestingFinished");
	}
}
