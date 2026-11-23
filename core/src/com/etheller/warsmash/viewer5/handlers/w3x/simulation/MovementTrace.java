package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;

/**
 * Per-step movement log of chosen units, for finding out why a unit walked where it did (a replay played with the
 * trace on). Off unless -Dwarsmash.trace.move is set, and then it only reads the game: nothing it does reaches the
 * simulation, so a traced game stays equal to an untraced one.
 * <ul>
 * <li>-Dwarsmash.trace.move=&lt;handle&gt;,&lt;handle&gt;,... or all: the units to follow;</li>
 * <li>-Dwarsmash.trace.move.file=&lt;file&gt;: where the lines go (default warsmash-move-trace.txt);</li>
 * <li>-Dwarsmash.trace.move.max=&lt;lines&gt;: the log stops there (default 2000000).</li>
 * </ul>
 * A line is written when a followed unit's position, facing, behavior, order or path changed:
 * {@code step handle type owner x y facing behavior order target path}. The log, its state and the line limit are
 * shared by every simulation of the process.
 */
public final class MovementTrace {
	public static final String PROPERTY = "warsmash.trace.move";
	public static final String FILE_PROPERTY = "warsmash.trace.move.file";
	public static final String MAX_PROPERTY = "warsmash.trace.move.max";
	public static final boolean ENABLED = System.getProperty(PROPERTY) != null;

	private static boolean all;
	private static Set<Integer> handles;
	private static BufferedWriter writer;
	private static long linesLeft;
	private static final Map<Integer, String> lastLine = new HashMap<>();

	private MovementTrace() {
	}

	private static synchronized void open() {
		if (writer != null) {
			return;
		}
		handles = new HashSet<>();
		for (final String part : System.getProperty(PROPERTY).split(",")) {
			final String trimmed = part.trim();
			if (trimmed.equals("all")) {
				all = true;
			}
			else if (!trimmed.isEmpty()) {
				try {
					handles.add(Integer.parseInt(trimmed));
				}
				catch (final NumberFormatException e) {
					System.err.println("MovementTrace: not a handle: " + trimmed);
				}
			}
		}
		linesLeft = Long.getLong(MAX_PROPERTY, 2000000L);
		try {
			writer = Files.newBufferedWriter(Paths.get(System.getProperty(FILE_PROPERTY, "warsmash-move-trace.txt")),
					StandardCharsets.UTF_8);
			writer.write("# step handle type owner x y facing behavior order target path\n");
			final BufferedWriter opened = writer;
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				synchronized (MovementTrace.class) {
					try {
						opened.close();
					}
					catch (final IOException e) {
						// the process is ending
					}
				}
			}));
		}
		catch (final IOException e) {
			System.err.println("MovementTrace: cannot write the trace: " + e);
			linesLeft = 0;
		}
	}

	/** Called once a step, after the units and the path searches have run. */
	public static synchronized void afterStep(final CSimulation simulation) {
		if (writer == null) {
			open();
		}
		if (linesLeft <= 0) {
			return;
		}
		final int step = simulation.getGameTurnTick();
		final StringBuilder line = new StringBuilder();
		try {
			for (final CUnit unit : simulation.getUnits()) {
				if (!all && !handles.contains(unit.getHandleId())) {
					continue;
				}
				line.setLength(0);
				describe(line, simulation, unit);
				final String state = line.toString();
				if (state.equals(lastLine.put(unit.getHandleId(), state))) {
					continue;
				}
				writer.write(Integer.toString(step));
				writer.write(' ');
				writer.write(state);
				writer.write('\n');
				if (--linesLeft <= 0) {
					writer.write("# stopped: line limit\n");
					break;
				}
			}
			// Flushed every step: the tool that plays the replay may kill the engine at the mark.
			writer.flush();
		}
		catch (final IOException e) {
			linesLeft = 0;
		}
	}

	private static void describe(final StringBuilder line, final CSimulation simulation, final CUnit unit) {
		line.append(unit.getHandleId()).append(' ').append(unit.getTypeId().asStringValue()).append(' ')
				.append(unit.getPlayerIndex()).append(' ').append(unit.getX()).append(' ').append(unit.getY())
				.append(' ').append(unit.getFacing()).append(' ');
		final CBehavior behavior = unit.getCurrentBehavior();
		line.append(behavior == null ? "-" : behavior.getClass().getSimpleName()).append(' ');
		final COrder order = unit.getCurrentOrder();
		if (order == null) {
			line.append("- -");
		}
		else {
			line.append(order.getOrderId()).append(' ');
			final AbilityTarget target = order.getTarget(simulation);
			line.append(target == null ? "-" : target.getX() + "," + target.getY());
		}
		line.append(' ');
		if (behavior instanceof CBehaviorMove) {
			line.append(((CBehaviorMove) behavior).traceSummary());
		}
		else {
			line.append('-');
		}
	}
}
