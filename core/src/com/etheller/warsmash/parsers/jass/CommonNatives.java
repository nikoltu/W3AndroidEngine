package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.interpreter.ast.debug.JassException;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.JassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.triggers.TriggerEventHandle;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CFogModifierJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerSleepAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import java.util.List;
import java.util.Set;

/**
 * Base of the classes that register the common.j natives of a game: what their natives share (the program, the map,
 * the simulation, the interface) and the helpers they have in common.
 */
abstract class CommonNatives {
	final CommonEnvironment environment;
	final JassProgram jassProgramVisitor;
	final GlobalScope globals;
	final DataSource dataSource;
	final Viewport uiViewport;
	final Scene uiScene;
	final War3MapViewer war3MapViewer;
	final WarsmashUI meleeUI;
	final String[] originalFiles;
	final CSimulation simulation;
	final GameUI gameUI;
	/** One rectangle reused by the natives that enumerate an area. */
	final Rectangle tempRect;

	CommonNatives(final CommonEnvironment environment) {
		this.environment = environment;
		this.jassProgramVisitor = environment.jassProgramVisitor;
		this.globals = this.jassProgramVisitor.getGlobalScope();
		this.dataSource = environment.dataSource;
		this.uiViewport = environment.uiViewport;
		this.uiScene = environment.uiScene;
		this.war3MapViewer = environment.war3MapViewer;
		this.meleeUI = environment.meleeUI;
		this.originalFiles = environment.originalFiles;
		this.simulation = environment.simulation;
		this.gameUI = environment.gameUI;
		this.tempRect = environment.tempRect;
	}

	abstract void register();

	HandleJassType type(final String name) {
		return this.globals.getHandleType(name);
	}

	static final java.util.Set<String> REPORTED_MISSING_DESTRUCTORS = java.util.concurrent.ConcurrentHashMap
			.newKeySet();

	// Campaign scripts call these natives tens of thousands of times per map.
	static void reportMissingDestructorOnce(final String nativeName) {
		if (REPORTED_MISSING_DESTRUCTORS.add(nativeName)) {
			Log.warn(nativeName
					+ " called but in Java we don't have a destructor, so we need to unregister later when that is implemented (reported once)");
		}
	}

	/**
	 * 1.29 resumes a TriggerSleepAction or TriggerWaitForSound 0.02-0.23 s after the time asked for (0.17 s on
	 * average), paced by real time; here a fixed number of steps, the same on every computer. A wait of under a tenth
	 * of a second never resumes sooner than 0.2 s there (0.18-0.23 s measured for waits of 0 up to 0.1 s).
	 */
	static final int SCRIPT_WAIT_LATENCY_STEPS = 3;
	static final int SCRIPT_WAIT_MIN_STEPS = 4;

	/**
	 * TriggerSleepAction and TriggerWaitForSound: the thread resumes on a whole simulation step.
	 */
	static void scriptWait(final CSimulation simulation, final GlobalScope globalScope, final float seconds) {
		final JassThread currentThread = globalScope.getCurrentThread();
		if ((currentThread != null) && currentThread.isSleepEndsThread()) {
			currentThread.halt();
		}
		else if (currentThread != null) {
			currentThread.setSleeping(true);
			final CTimerSleepAction timer = new CTimerSleepAction(currentThread);
			timer.setRepeats(false);
			final int steps = Math.max(SCRIPT_WAIT_MIN_STEPS,
					Math.round(Math.max(0, seconds) / WarsmashConstants.SIMULATION_STEP_TIME)
							+ SCRIPT_WAIT_LATENCY_STEPS);
			timer.setTimeoutTime(steps * WarsmashConstants.SIMULATION_STEP_TIME);
			timer.start(simulation);
		}
		else {
			throw new JassException(globalScope, "Needs to sleep " + seconds + " but no thread was found", null);
		}
	}

	/**
	 * A unit removed from the game reads as no unit once the step that removed it is over, as in the original game.
	 */
	static <T extends CWidget> T inGame(final T widget) {
		if ((widget instanceof CUnit) && ((CUnit) widget).isRemovedFromGame()) {
			return null;
		}
		if ((widget instanceof CDestructable) && ((CDestructable) widget).isRemovedFromGame()) {
			return null;
		}
		return widget;
	}

	/**
	 * Measured on 1.29: rect enumerations of units, items and destructables work on the 32
	 * world-unit cells. An object is found when the rect covers the whole cell its centre is in and the rect's
	 * left and bottom edges lie strictly before that cell: cells floor(min / 32) + 1 to floor(max / 32) - 1.
	 */
	static boolean rectCellsContain(final Rectangle rect, final float x, final float y) {
		final int cellX = (int) StrictMath.floor(x / 32f);
		final int cellY = (int) StrictMath.floor(y / 32f);
		return (cellX > (int) StrictMath.floor(rect.x / 32f))
				&& (cellX < (int) StrictMath.floor((rect.x + rect.width) / 32f))
				&& (cellY > (int) StrictMath.floor(rect.y / 32f))
				&& (cellY < (int) StrictMath.floor((rect.y + rect.height) / 32f));
	}

	static <T> T nullable(final List<JassValue> arguments, final int index, final JassValueVisitor<T> visitor) {
		final JassValue arg = arguments.get(index);
		if (arg == null) {
			return null;
		}
		return arg.visit(visitor);
	}

	// 1.29 keeps a value it has no constant for as an enum handle of that value instead of failing.
	static HandleJassValue enumValue(final HandleJassType type, final Object[] values, final int i) {
		if ((i >= 0) && (i < values.length)) {
			return new HandleJassValue(type, values[i]);
		}
		return type.getUnknownEnumValue(i);
	}

	static HandleJassValue enumValue(final HandleJassType type, final Object value, final int i) {
		return value == null ? type.getUnknownEnumValue(i) : new HandleJassValue(type, value);
	}

	// Measured on 1.29: to the fog natives the neutral players see no point at all, even next to their own units,
	// yet every unit is visible to them (and still masked).
	CFogState fogState(final CPlayer player, final float x, final float y) {
		if (this.simulation.isNeutralPlayer(player.getId())) {
			return CFogState.MASKED;
		}
		return player.getFogOfWar().getFogState(this.simulation, this.simulation.getPathingGrid(), x, y);
	}

	// A unit the player has seen reads as fogged under black mask (1.29).
	CFogState unitFogState(final CUnit unit, final CPlayer player) {
		final CFogState state = fogState(player, unit.getX(), unit.getY());
		if ((state == CFogState.MASKED) && unit.isSeenBy(player.getId())
				&& !this.simulation.isNeutralPlayer(player.getId())) {
			return CFogState.FOGGED;
		}
		return state;
	}

	boolean isUnitVisibleToPlayer(final CUnit unit, final CPlayer player) {
		if (this.simulation.isUnitVisionShared(unit, player)) {
			return true;
		}
		if (this.simulation.isNeutralPlayer(player.getId())) {
			return unit.isVisible(this.simulation, player.getId());
		}
		if (fogState(player, unit.getX(), unit.getY()) != CFogState.VISIBLE) {
			return false;
		}
		if ((unit.getInvisLevels() > 0) && (unit.getPlayerIndex() != player.getId())
				&& !this.simulation.getPlayer(unit.getPlayerIndex()).hasAlliance(player.getId(),
						CAllianceType.SHARED_VISION)) {
			return unit.isVisible(this.simulation, player.getId());
		}
		return true;
	}

	HandleJassValue newFogModifier(final HandleJassType fogmodifierType, final CFogModifierJass modifier) {
		modifier.applyNow(this.simulation);
		return newHandle(fogmodifierType, modifier);
	}

	HandleJassValue newHandle(final HandleJassType type, final CAllocatedHandle handle) {
		return new HandleJassValue(type,
				handle == null ? null : this.simulation.getHandleIdAllocator().assign(handle));
	}

	HandleJassValue newEvent(final HandleJassType type, final RemovableTriggerEvent event) {
		return newHandle(type, event == null ? null : new TriggerEventHandle(event));
	}

	HandleJassValue newLightning(final HandleJassType type,
			final SimulationRenderComponentLightning lightning) {
		if ((lightning instanceof CAllocatedHandle) && (((CAllocatedHandle) lightning).getHandleId() == 0)) {
			((CAllocatedHandle) lightning).setHandleId(this.simulation.getHandleIdAllocator().createLightningId());
		}
		return new HandleJassValue(type, lightning);
	}

	void release(final Object handle) {
		if (handle instanceof CAllocatedHandle) {
			this.simulation.getHandleIdAllocator().release((CAllocatedHandle) handle);
		}
	}

	static float realArg(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	static int intArg(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}
}
