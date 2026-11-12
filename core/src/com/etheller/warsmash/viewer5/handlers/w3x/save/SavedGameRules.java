package com.etheller.warsmash.viewer5.handlers.w3x.save;

import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWeatherEffect;
import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderLightningEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.SpellEffectModel;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.execution.instruction.JassInstruction;
import com.etheller.interpreter.ast.execution.instruction.PushLiteralInstruction;
import com.etheller.interpreter.ast.function.UserJassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.GlobalScopeAssignable;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.warsmash.parsers.jass.JassAIEnvironment;
import com.etheller.warsmash.parsers.jass.ai.AIController;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Tells the object graph what belongs to a save. What the map's own data and script build the same way every time
 * (types, data tables, script instructions) is found again by name; what only draws the game (render peers, frames,
 * models, sounds) is left out and set up again after loading.
 */
final class SavedGameRules implements SavedObjectGraph.Rules {
	private static final String[] DROPPED_PREFIXES = { "com.etheller.warsmash.viewer5.handlers.w3x.rendersim.",
			"com.etheller.warsmash.viewer5.handlers.w3x.ui.", "com.etheller.warsmash.viewer5.handlers.mdx.",
			"com.etheller.warsmash.parsers.fdf.", "com.etheller.warsmash.parsers.mdlx.", "com.etheller.warsmash.units.",
			"com.etheller.warsmash.viewer5.gl.", "com.etheller.warsmash.parsers.jass.ai.DummyOnly",
			"com.badlogic.gdx.graphics.", "com.badlogic.gdx.audio.", "com.badlogic.gdx.utils.viewport.",
			"com.badlogic.gdx.backends.", "org.lwjgl.", "com.etheller.warsmash.viewer5.Scene",
			"com.etheller.warsmash.viewer5.ModelInstance", "com.etheller.warsmash.viewer5.Model",
			"com.etheller.warsmash.audio", "com.etheller.warsmash.datasources." };

	private static final String SCRIPT_DIALOG_PREFIX = "com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog";
	private static final String SCRIPT_DIALOG_BUTTON_CLICK = SCRIPT_DIALOG_PREFIX + "Button$1";
	// Asked for every object a save meets; matching class names is slow enough on Android to show.
	private static final Map<Class<?>, Boolean> DROPPED_CLASSES = new ConcurrentHashMap<>();

	private final Map<SpellEffectModel, SavedEffectModel> effectModels = new IdentityHashMap<>();
	private final War3MapViewer viewer;
	private final CSimulation simulation;
	private final List<GlobalScope> scopes;
	private final Map<Object, String> externalNames = new IdentityHashMap<>();
	private final Map<String, Object> externalObjects = new HashMap<>();
	private final Map<String, Integer> droppedReport = new TreeMap<>();
	private final Set<Class<?>> scannedClasses = new HashSet<>();
	private final List<SavedScriptPositions.Program> programs;
	private final SavedScriptPositions.Recorder positions;
	private Map<Object, Integer> literalCodes;

	SavedGameRules(final War3MapViewer viewer, final List<GlobalScope> scopes,
			final List<JassAIEnvironment> aiEnvironments) {
		this.viewer = viewer;
		this.simulation = viewer.simulation;
		this.scopes = scopes;
		this.programs = new ArrayList<>();
		name(viewer, "viewer");
		name(viewer.getScriptEnvironment(), "scriptEnvironment");
		name(viewer.mapMpq, "mapMpq");
		name(viewer.worldScene.audioContext, "audio:world");
		name(viewer.getUiAudioContext(), "audio:ui");
		name(this.simulation, "simulation");
		name(this.simulation.getPathingGrid(), "pathing");
		name(this.simulation.getSimulationRenderController(), "renderController");
		name(this.simulation.getQuestLog(), "questLog");
		for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
			name(this.simulation.getPlayer(i), "player:" + i);
			name(this.simulation.getPlayer(i).getFogOfWar(), "fog:" + i);
		}
		name(this.simulation.getUnitData(), "unitData");
		name(this.simulation.getAbilityData(), "abilityData");
		name(this.simulation.getItemData(), "itemData");
		name(this.simulation.getDestructableData(), "destructableData");
		name(this.simulation.getUpgradeData(), "upgradeData");
		name(this.simulation.getGameplayConstants(), "gameplayConstants");
		for (int i = 0; i < aiEnvironments.size(); i++) {
			name(aiEnvironments.get(i), "ai:" + i);
			name(aiEnvironments.get(i).getProgram(), "aiprogram:" + i);
			name(aiEnvironments.get(i).getController(), "aicontroller:" + i);
		}
		for (int i = 0; i < scopes.size(); i++) {
			final GlobalScope scope = scopes.get(i);
			final String prefix = "s" + i + ":";
			name(scope, prefix + "scope");
			for (final Map.Entry<String, JassType> type : scope.getTypes().entrySet()) {
				name(type.getValue(), prefix + "t:" + type.getKey());
			}
			name(JassType.INTEGER, "t:integer");
			name(JassType.REAL, "t:real");
			name(JassType.STRING, "t:string");
			name(JassType.BOOLEAN, "t:boolean");
			name(JassType.CODE, "t:code");
			name(JassType.NOTHING, "t:nothing");
			// By name, so a global added to the scripts does not move the others; "g:<index>" of older saves is
			// resolved on demand.
			for (final Map.Entry<String, Integer> global : scope.getGlobalIds().entrySet()) {
				name(scope.getAssignableGlobalById(global.getValue()), prefix + "gn:" + global.getKey());
			}
			this.programs.add(new SavedScriptPositions.Program(scope));
			for (final Map.Entry<String, UserJassFunction> function : scope.getFunctionDefinitions()
					.entrySet()) {
				name(function.getValue(), prefix + "f:" + function.getKey());
			}
		}
		this.positions = new SavedScriptPositions.Recorder(this.programs);
	}

	List<SavedScriptPositions.Program> getPrograms() {
		return this.programs;
	}

	/** The functions the written positions and references are in; complete once the graph is written. */
	SavedScriptPositions.Table getPositions() {
		return this.positions.table();
	}

	/**
	 * Instructions and code literals are named by function and offset, found when first asked for: naming every
	 * instruction ahead would cost a name per instruction and per program.
	 */
	private String scriptName(final Object object) {
		if (object instanceof JassInstruction) {
			final JassInstruction instruction = (JassInstruction) object;
			for (int i = 0; i < this.programs.size(); i++) {
				final int index = this.programs.get(i).indexOf(instruction);
				if (index >= 0) {
					return functionName(i, "fi:", index);
				}
			}
			return null;
		}
		if (object.getClass() == CodeJassValue.class) {
			final Integer scope = literalCodes().get(object);
			final Integer pointer = ((CodeJassValue) object).getUserFunctionInstructionPtr();
			if ((scope != null) && (pointer != null) && (pointer >= 0)) {
				return functionName(scope, "code:", pointer);
			}
		}
		return null;
	}

	private String functionName(final int scope, final String kind, final int pointer) {
		final SavedScriptPositions.Program program = this.programs.get(scope);
		final int segment = program.segmentAt(pointer);
		if (segment < 0) {
			return "s" + scope + ":i:" + pointer;
		}
		final int offset = pointer - program.start(segment);
		this.positions.function(scope, segment, offset);
		return "s" + scope + ":" + kind + program.key(segment) + "@" + offset;
	}

	/** Code values written in the scripts ("function X") live in their program and come back by name. */
	private Map<Object, Integer> literalCodes() {
		if (this.literalCodes == null) {
			this.literalCodes = new IdentityHashMap<>();
			try {
				final Field value = PushLiteralInstruction.class.getDeclaredField("value");
				value.setAccessible(true);
				for (int i = 0; i < this.scopes.size(); i++) {
					for (final JassInstruction instruction : this.scopes.get(i).getInstructions()) {
						if (instruction instanceof PushLiteralInstruction) {
							final Object literal = value.get(instruction);
							if ((literal != null) && (literal.getClass() == CodeJassValue.class)
									&& !this.literalCodes.containsKey(literal)) {
								this.literalCodes.put(literal, i);
							}
						}
					}
				}
			}
			catch (final ReflectiveOperationException exc) {
				throw new IllegalStateException(exc);
			}
		}
		return this.literalCodes;
	}

	private Object resolveScriptName(final String name) {
		final int colon = name.indexOf(':');
		final int scopeIndex = Integer.parseInt(name.substring(1, colon));
		final GlobalScope scope = this.scopes.get(scopeIndex);
		final String rest = name.substring(colon + 1);
		if (rest.startsWith("i:")) {
			return scope.getInstructions().get(Integer.parseInt(rest.substring(2)));
		}
		if (rest.startsWith("g:")) {
			return scope.getIndexedGlobals().get(Integer.parseInt(rest.substring(2)));
		}
		if (rest.startsWith("gn:")) {
			// A global the scripts no longer have: its saved value is read into a stand-in and dropped.
			Log.warn("Saved game: the scripts no longer have the global " + rest.substring(3));
			return new GlobalScopeAssignable(JassType.INTEGER, scope);
		}
		final boolean code = rest.startsWith("code:");
		if (code || rest.startsWith("fi:")) {
			final String function = rest.substring(rest.indexOf(':') + 1);
			final int at = function.lastIndexOf('@');
			final SavedScriptPositions.Program program = this.programs.get(scopeIndex);
			final Integer segment = program.segmentOf(function.substring(0, at));
			if (segment == null) {
				throw new IllegalStateException("The loaded game has no script function " + function);
			}
			final int pointer = program.start(segment) + Integer.parseInt(function.substring(at + 1));
			final Object resolved = code ? new CodeJassValue(pointer) : program.instructionAt(pointer);
			this.externalObjects.put(name, resolved);
			return resolved;
		}
		return null;
	}

	/** A destructable exists before the state is read: the map placed it, or loading created it again first. */
	void nameDestructable(final CDestructable destructable, final int savedHandleId) {
		name(destructable, "dest:" + savedHandleId);
	}

	private void name(final Object object, final String name) {
		if ((object != null) && !this.externalNames.containsKey(object)) {
			this.externalNames.put(object, name);
			this.externalObjects.put(name, object);
		}
	}

	@Override
	public String externalName(final Object object) {
		scanStaticFinals(object.getClass());
		final String known = this.externalNames.get(object);
		if (known != null) {
			return known;
		}
		if ((object instanceof JassInstruction) || (object instanceof CodeJassValue)) {
			return scriptName(object);
		}
		if (object instanceof UnitSound) {
			final String key = this.viewer.getUiSounds().keyOf((UnitSound) object);
			return key == null ? null : "uisound:" + key;
		}
		if (object instanceof Sound) {
			final String fileName = this.viewer.getFileSoundName((Sound) object);
			return fileName == null ? null : "filesound:" + fileName;
		}
		if (object instanceof GameObject) {
			final String id = ((GameObject) object).getId();
			return this.simulation.getAbilityData().getEditorData(id) == object ? "abilitydata:" + id : null;
		}
		if (object instanceof CUnitType) {
			return "ut:" + ((CUnitType) object).getTypeId().getValue();
		}
		if (object instanceof CItemType) {
			return "it:" + ((CItemType) object).getTypeId().getValue();
		}
		if (object instanceof CDestructableType) {
			return "dt:" + ((CDestructableType) object).getTypeId().getValue();
		}
		if (object instanceof CUpgradeType) {
			return "gt:" + ((CUpgradeType) object).getTypeId().getValue();
		}
		return null;
	}

	@Override
	public boolean isInPlace(final Object object) {
		return (object == this.simulation) || (object == this.simulation.getPathingGrid())
				|| (object == this.simulation.getQuestLog()) || (object instanceof GlobalScopeAssignable)
				|| (object instanceof JassAIEnvironment) || (object instanceof AIController)
				|| (object instanceof CPlayer)
				|| (object instanceof CPlayerFogOfWar) || (object instanceof CDestructable);
	}

	@Override
	public boolean isDropped(final Object object) {
		final Class<?> type = object.getClass();
		Boolean dropped = DROPPED_CLASSES.get(type);
		if (dropped == null) {
			dropped = isDroppedClass(type);
			DROPPED_CLASSES.put(type, dropped);
		}
		return dropped;
	}

	private static boolean isDroppedClass(final Class<?> type) {
		if (CTimerDialog.class.isAssignableFrom(type)) {
			return false;
		}
		final String name = type.getName();
		if (name.startsWith(SCRIPT_DIALOG_PREFIX) && !name.startsWith(SCRIPT_DIALOG_BUTTON_CLICK)) {
			return false;
		}
		for (final String prefix : DROPPED_PREFIXES) {
			if (name.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Object replace(final Object object) {
		if (object instanceof JassThread) {
			this.positions.thread((JassThread) object);
			return object;
		}
		if ((object instanceof CodeJassValue) && (externalName(object) == null)) {
			this.positions.inlineCode((CodeJassValue) object);
			return object;
		}
		if (object instanceof RenderSpellEffect) {
			final RenderSpellEffect effect = (RenderSpellEffect) object;
			return effect.getModelName() == null ? object : SavedEffect.of(effect);
		}
		if (object instanceof SpellEffectModel) {
			return this.effectModels.computeIfAbsent((SpellEffectModel) object, SavedEffectModel::new);
		}
		if (object instanceof RenderLightningEffect) {
			return SavedLightning.of((RenderLightningEffect) object);
		}
		if (object instanceof RenderWeatherEffect) {
			return SavedWeather.of((RenderWeatherEffect) object);
		}
		if (object instanceof RenderUbersplat) {
			return SavedUbersplat.of((RenderUbersplat) object);
		}
		if ((object instanceof SavedEffect) || (object instanceof SavedEffectModel)
				|| (object instanceof SavedLightning) || (object instanceof SavedWeather)
				|| (object instanceof SavedUbersplat)) {
			return object;
		}
		if (object instanceof SimulationRenderComponent) {
			if (object instanceof SimulationRenderComponentLightning) {
				return SavedRenderStubs.LIGHTNING;
			}
			if (object instanceof SimulationRenderComponentModel) {
				return SavedRenderStubs.MODEL;
			}
			return SavedRenderStubs.COMPONENT;
		}
		return object;
	}

	/**
	 * Shared constants (DO_NOTHING listeners, EMPTY scopes, boolean values, filters) are compared by identity, so
	 * they must stay the one object the class holds. Only classes a save runs into are looked at, on first sight.
	 */
	private void scanStaticFinals(final Class<?> type) {
		if (this.scannedClasses.add(type)) {
			String hostName = type.getName();
			final int lambda = hostName.indexOf("$$");
			if (lambda > 0) {
				hostName = hostName.substring(0, lambda);
				try {
					scanStaticFinals(Class.forName(hostName, false, type.getClassLoader()));
				}
				catch (final ClassNotFoundException | LinkageError exc) {
					return;
				}
			}
			if (type.getEnclosingClass() != null) {
				scanStaticFinals(type.getEnclosingClass());
			}
			if (type.getSuperclass() != null) {
				scanStaticFinals(type.getSuperclass());
			}
			for (final Class<?> interfaceType : type.getInterfaces()) {
				scanStaticFinals(interfaceType);
			}
			if (type.getName().startsWith("java.")) {
				return;
			}
			for (final Field field : type.getDeclaredFields()) {
				final int modifiers = field.getModifiers();
				if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && !field.getType().isPrimitive()
						&& !field.getType().isEnum() && (field.getType() != String.class)) {
					try {
						field.setAccessible(true);
						final Object value = field.get(null);
						if ((value != null) && !(value instanceof String) && !(value instanceof Number)
								&& !(value instanceof Enum)) {
							name(value, "static:" + type.getName() + "#" + field.getName());
						}
					}
					catch (final IllegalAccessException | RuntimeException | LinkageError exc) {
						continue;
					}
				}
			}
		}
	}

	@Override
	public Object resolveExternal(final String name) {
		final Object known = this.externalObjects.get(name);
		if (known != null) {
			return known;
		}
		if ((name.length() > 1) && (name.charAt(0) == 's') && Character.isDigit(name.charAt(1))) {
			final Object script = resolveScriptName(name);
			if (script != null) {
				return script;
			}
		}
		if (name.startsWith("static:")) {
			final int hash = name.indexOf('#');
			try {
				final Field field = Class.forName(name.substring(7, hash), true, getClass().getClassLoader())
						.getDeclaredField(name.substring(hash + 1));
				field.setAccessible(true);
				return field.get(null);
			}
			catch (final ReflectiveOperationException exc) {
				throw new IllegalStateException("This engine has no " + name, exc);
			}
		}
		if (name.startsWith("abilitydata:")) {
			return this.simulation.getAbilityData().getEditorData(name.substring(12));
		}
		if (name.startsWith("uisound:")) {
			return this.viewer.getUiSounds().getSound(name.substring(8));
		}
		if (name.startsWith("filesound:")) {
			final Sound sound = UnitSound.createSound(this.viewer.mapMpq, name.substring(10));
			this.viewer.rememberFileSound(sound, name.substring(10));
			return sound;
		}
		final int colon = name.indexOf(':');
		if (colon > 0) {
			final War3ID id = new War3ID(Integer.parseInt(name.substring(colon + 1)));
			switch (name.substring(0, colon)) {
			case "ut":
				return this.simulation.getUnitData().getUnitType(id);
			case "it":
				return this.simulation.getItemData().getItemType(id);
			case "dt":
				return this.simulation.getDestructableData().getUnitType(id);
			case "gt":
				return this.simulation.getUpgradeData().getType(id);
			default:
				break;
			}
		}
		throw new IllegalStateException("The loaded game has nothing called " + name);
	}

	@Override
	public void reportDropped(final Class<?> holder, final String field, final Object object) {
		this.droppedReport.merge(holder.getName() + "." + field + " -> " + object.getClass().getName(), 1,
				Integer::sum);
	}

	Map<String, Integer> getDroppedReport() {
		return this.droppedReport;
	}

	War3MapViewer getViewer() {
		return this.viewer;
	}

	List<GlobalScope> getScopes() {
		return this.scopes;
	}
}
