package com.etheller.warsmash.parsers.jass;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderCommands;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDoodad;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDoodadTable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;

/**
 * The Reforged natives that read and change the doodads of the map by their index in the map's doodad table
 * (war3map.doo, in file order, the doodads the map shows), and colour them by type and area.
 */
final class ReforgedDoodadNatives {
	private final JassProgram program;
	private final War3MapViewer viewer;
	private final RenderCommands commands;

	private ReforgedDoodadNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
		this.commands = LocalUiLink.of(viewer, null).commands();
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final ReforgedDoodadNatives natives = new ReforgedDoodadNatives(program, viewer);
		natives.registerReaders();
		natives.registerSetters();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	/** A native that colours or animates doodads and returns nothing: it runs on the render side in the frame's order. */
	private void addCommand(final String name, final JassFunction function) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final List<JassValue> copy = new ArrayList<>(arguments);
			this.commands.send(() -> function.call(copy, globalScope, triggerScope));
			return null;
		});
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
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

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}

	private RenderDoodad doodad(final List<JassValue> arguments) {
		final int index = integer(arguments, 0);
		final List<RenderDoodad> doodads = this.viewer.doodads;
		return (index >= 0) && (index < doodads.size()) ? doodads.get(index) : null;
	}

	private interface RealReader {
		float read(CDoodadTable.Entry doodad);
	}

	private void addReal(final String name, final RealReader reader) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final CDoodadTable.Entry doodad = this.viewer.doodadTable.get(integer(arguments, 0));
			return RealJassValue.of(doodad == null ? 0f : reader.read(doodad));
		});
	}

	private void registerReaders() {
		add("BlzGetNumDoodads",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(this.viewer.doodadTable.size()));
		addReal("BlzGetDoodadX", doodad -> doodad.x);
		addReal("BlzGetDoodadY", doodad -> doodad.y);
		addReal("BlzGetDoodadZ", doodad -> doodad.z);
		addReal("BlzGetDoodadScaleX", doodad -> doodad.scaleX);
		addReal("BlzGetDoodadScaleY", doodad -> doodad.scaleY);
		addReal("BlzGetDoodadScaleZ", doodad -> doodad.scaleZ);
		addReal("BlzGetDoodadYaw", doodad -> doodad.yaw);
		addReal("BlzGetDoodadPitch", doodad -> doodad.pitch);
		addReal("BlzGetDoodadRoll", doodad -> doodad.roll);
		add("BlzGetDoodadVariation", (arguments, globalScope, triggerScope) -> {
			final CDoodadTable.Entry doodad = this.viewer.doodadTable.get(integer(arguments, 0));
			return IntegerJassValue.of(doodad == null ? 0 : doodad.variation);
		});
		add("BlzGetDoodadId", (arguments, globalScope, triggerScope) -> {
			final CDoodadTable.Entry doodad = this.viewer.doodadTable.get(integer(arguments, 0));
			return IntegerJassValue.of(doodad == null ? 0 : doodad.typeId.getValue());
		});
		// The doodads here always turn about the map's own axes: the file has no per-doodad model axes flag.
		add("BlzGetDoodadIsUsingModelAxes", (arguments, globalScope, triggerScope) -> BooleanJassValue.of(false));
	}

	private void registerSetters() {
		addCommand("BlzSetSingleDoodadColor", (arguments, globalScope, triggerScope) -> {
			final RenderDoodad doodad = doodad(arguments);
			final Object color = object(arguments, 1);
			if ((doodad != null) && (color instanceof CPlayerColor)) {
				doodad.setPlayerColor(((CPlayerColor) color).ordinal());
			}
			return null;
		});
		addCommand("BlzSetSingleDoodadAnimation", (arguments, globalScope, triggerScope) -> {
			final RenderDoodad doodad = doodad(arguments);
			final String animation = string(arguments, 1);
			if ((doodad != null) && (animation != null)) {
				doodad.playAnimation(animation, bool(arguments, 2));
			}
			return null;
		});
		addCommand("SetDoodadColor", (arguments, globalScope, triggerScope) -> {
			final Object color = object(arguments, 5);
			if (color instanceof CPlayerColor) {
				this.viewer.setDoodadColor(new War3ID(integer(arguments, 3)).toString(), null, real(arguments, 0),
						real(arguments, 1), real(arguments, 2), bool(arguments, 4), ((CPlayerColor) color).ordinal());
			}
			return null;
		});
		addCommand("SetDoodadColorRect", (arguments, globalScope, triggerScope) -> {
			final Rectangle area = object(arguments, 0);
			final Object color = object(arguments, 2);
			if ((area != null) && (color instanceof CPlayerColor)) {
				this.viewer.setDoodadColor(new War3ID(integer(arguments, 1)).toString(), area, 0, 0, 0, false,
						((CPlayerColor) color).ordinal());
			}
			return null;
		});
	}
}
