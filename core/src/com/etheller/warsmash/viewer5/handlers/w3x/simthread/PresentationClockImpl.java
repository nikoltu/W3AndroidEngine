package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;

/**
 * The presentation clock of the render thread: shows the game one step (D) behind the newest published frame, between
 * the two frames around that time. A late step is hidden in three stages. The buffer is the step the clock trails by.
 * When less than half a step is left the clock slows down to {@link #SLOW_FLOOR} of real time, and when the newest
 * frame is passed it goes on past it ({@link State#EXTRAPOLATING}, units continue along their paths) for at most
 * {@link #EXTRAPOLATION_LIMIT_MILLIS}, after which the picture is held ({@link State#STARVED}). A frame arriving
 * after a hold is reached by the normal slowdown and catch-up, never by a jump, except after a long gap.
 * <p>
 * Frames of one tick can be published more than once (the one made for events outside a step carries the same game
 * time); the clock treats equal times as one moment and takes the newest frame of them.
 * <p>
 * {@code -Dwarsmash.simthread.stall=<tick>:<ms>[,<tick>:<ms>...]} delays the frame of the first step at or after the
 * tick, and every later one, by that many milliseconds of render time, as if the step took that long: the way to see
 * a late step on a simulation that runs in step with the render thread.
 * <p>
 * {@code -Dwarsmash.simthread.clocktrace=<file>} writes one line per render frame (real time, presentation time, state,
 * the frames around it, and with {@code -Dwarsmash.simthread.clocktrace.unit=<handle>} (or {@code auto} for a moving
 * unit) where that unit is shown).
 * <p>
 * Render thread only.
 */
public final class PresentationClockImpl implements PresentationClock {
	/** Property that lists the artificial delays, see the class comment. */
	public static final String STALL_PROPERTY = "warsmash.simthread.stall";
	public static final String TRACE_PROPERTY = "warsmash.simthread.clocktrace";
	public static final String TRACE_UNIT_PROPERTY = "warsmash.simthread.clocktrace.unit";
	/** Slowest the clock runs while waiting for a step, relative to real time. */
	public static final float SLOW_FLOOR = 0.7f;
	/** The longest the picture is carried past the newest frame before it is held. */
	public static final long EXTRAPOLATION_LIMIT_MILLIS = 300;
	private static final float CATCH_UP = 1.25f;
	/** The most real time one wait for models slows the clock. */
	private static final double MODEL_WAIT_MILLIS = 300;
	/** A gap this large (a load, a long hitch) is crossed by one jump to the normal distance behind the newest frame. */
	private static final long JUMP_MILLIS = 1000;

	private final SimFrameRing ring;
	private final long[] stallTicks;
	private final long[] stallMillis;
	private int stallNext;
	private long stallSequence = -1;
	private long stallReleaseNanos;
	private long stallScanned;
	private long nowNanos;

	private double presentation;
	private boolean started;
	private long beforeSequence = -1;
	private long afterSequence = -1;
	private float interpolation;
	private State state = State.NORMAL;
	private long lead;
	private long extrapolated;
	private float timeScale = 1f;
	private boolean paused;
	private boolean modelWait;
	/** Real time the clock may still run slow for models in this wait. */
	private double modelWaitBudgetMillis = MODEL_WAIT_MILLIS;
	private BufferedWriter trace;
	private int traceUnit;
	private boolean traceAuto;
	private final Extrapolator.UnitPose tracePose = new Extrapolator.UnitPose();

	public PresentationClockImpl(final SimFrameRing ring) {
		this.ring = ring;
		final String traceFile = System.getProperty(TRACE_PROPERTY);
		if (traceFile != null) {
			try {
				this.trace = Files.newBufferedWriter(Paths.get(traceFile), StandardCharsets.UTF_8);
				final String unit = System.getProperty(TRACE_UNIT_PROPERTY, "");
				this.traceAuto = "auto".equals(unit);
				this.traceUnit = unit.isEmpty() || this.traceAuto ? 0 : Integer.parseInt(unit);
			}
			catch (final IOException e) {
				Log.warn("Clock trace not written: " + e);
			}
		}
		final String stalls = System.getProperty(STALL_PROPERTY);
		if ((stalls == null) || stalls.trim().isEmpty()) {
			this.stallTicks = new long[0];
			this.stallMillis = new long[0];
		}
		else {
			final String[] parts = stalls.split(",");
			this.stallTicks = new long[parts.length];
			this.stallMillis = new long[parts.length];
			for (int i = 0; i < parts.length; i++) {
				final String[] pair = parts[i].trim().split(":");
				this.stallTicks[i] = Long.parseLong(pair[0]);
				this.stallMillis[i] = Long.parseLong(pair[1]);
			}
		}
	}

	/** Frames the clock may see: all the published ones, minus the ones an artificial delay still holds back. */
	private long visibleCount() {
		final long published = this.ring.publishedCount();
		if (this.stallNext >= this.stallTicks.length) {
			return published;
		}
		if (this.stallSequence < 0) {
			for (long sequence = Math.max(this.stallScanned, this.ring.oldestHeld()); sequence < published; sequence++) {
				final SimFrame frame = this.ring.get(sequence);
				this.stallScanned = sequence + 1;
				if ((frame != null) && (frame.tick >= this.stallTicks[this.stallNext])) {
					this.stallSequence = sequence;
					this.stallReleaseNanos = this.nowNanos + (this.stallMillis[this.stallNext] * 1_000_000L);
					break;
				}
			}
		}
		if (this.stallSequence >= 0) {
			if (this.nowNanos >= this.stallReleaseNanos) {
				this.stallSequence = -1;
				this.stallNext++;
			}
			else {
				return this.stallSequence;
			}
		}
		return published;
	}

	@Override
	public void advance(final long realNanos) {
		this.nowNanos += realNanos;
		final long visible = visibleCount();
		if ((visible <= 0) || (this.ring.get(visible - 1) == null)) {
			this.state = this.paused ? State.PAUSED : State.NORMAL;
			this.lead = 0;
			this.extrapolated = 0;
			if (visible <= 0) {
				this.beforeSequence = -1;
				this.afterSequence = -1;
				this.started = false;
			}
			return;
		}
		final SimFrame newest = this.ring.get(visible - 1);
		final long newestTime = newest.gameTimeMillis;
		boolean restarted = false;
		if (!this.started || (this.beforeSequence >= visible)) {
			// first frame, or the ring started again after a load; the frame that took the load does not count
			this.presentation = newestTime - DELAY_MILLIS;
			this.started = true;
			this.beforeSequence = Math.max(0, this.ring.oldestHeld());
			restarted = true;
		}
		final double realMillis = restarted ? 0.0 : realNanos / 1_000_000.0;
		double distance = newestTime - this.presentation;
		double advance = realMillis * this.timeScale;
		float slow = 1f;
		if (distance > JUMP_MILLIS) {
			this.presentation = newestTime - DELAY_MILLIS;
			distance = DELAY_MILLIS;
		}
		else if ((distance - advance) > ((DELAY_MILLIS * 1.5f))) {
			slow = CATCH_UP;
		}
		else if ((advance > 0) && ((distance - advance) < (DELAY_MILLIS / 2f))) {
			// keep half a step in hand after this frame, as far as the floor allows
			slow = (float) Math.max(SLOW_FLOOR, Math.min(1.0, (distance - (DELAY_MILLIS / 2f)) / advance));
		}
		if (this.modelWait && (advance > 0) && (slow > SLOW_FLOOR)) {
			if (this.modelWaitBudgetMillis > 0) {
				slow = SLOW_FLOOR;
				this.modelWaitBudgetMillis -= realMillis;
			}
		}
		else if (!this.modelWait) {
			this.modelWaitBudgetMillis = MODEL_WAIT_MILLIS;
		}
		boolean held = false;
		if (this.paused || newest.paused) {
			// a paused game shows its last step exactly and does not go on past it
			advance = Math.min(advance, Math.max(0.0, distance));
			slow = 1f;
		}
		else {
			advance *= slow;
		}
		double next = this.presentation + advance;
		final double limit = newestTime + EXTRAPOLATION_LIMIT_MILLIS;
		if (next > limit) {
			next = Math.max(this.presentation, limit);
			held = true;
		}
		final boolean wasSlowed = (slow < 1f) && !this.paused;
		this.presentation = next;
		this.lead = Math.round(newestTime - this.presentation);
		findFrames(visible);
		final SimFrame before = this.ring.get(this.beforeSequence);
		this.extrapolated = (this.afterSequence < 0) && (before != null)
				? Math.max(0L, Math.round(this.presentation - before.gameTimeMillis))
				: 0;
		if (this.paused || newest.paused) {
			this.state = State.PAUSED;
		}
		else if (held) {
			this.state = State.STARVED;
		}
		else if ((this.afterSequence < 0) && (this.extrapolated > 0)) {
			this.state = State.EXTRAPOLATING;
		}
		else if (wasSlowed) {
			this.state = State.SLOWED;
		}
		else {
			this.state = State.NORMAL;
		}
		if (this.trace != null) {
			writeTrace(visible);
		}
		if (PerfStats.ENABLED) {
			PerfStats.simLead(Math.max(0L, this.lead));
			if (wasSlowed) {
				PerfStats.slowed((long) (realNanos * (1.0 - slow)));
			}
			if ((this.state == State.EXTRAPOLATING) || (this.state == State.STARVED)) {
				PerfStats.extrapolated(realNanos);
			}
			if (this.state == State.STARVED) {
				PerfStats.starvedFrame();
			}
		}
	}

	private void writeTrace(final long visible) {
		final SimFrame before = frameBefore();
		final SimFrame after = frameAfter();
		final StringBuilder line = new StringBuilder();
		line.append(this.nowNanos / 1_000_000L).append(' ').append(String.format("%.1f", this.presentation))
				.append(' ').append(this.state).append(" lead=").append(this.lead).append(" visible=").append(visible)
				.append(" before=").append(before == null ? -1 : before.tick).append(" after=")
				.append(after == null ? -1 : after.tick).append(" t=").append(String.format("%.2f", this.interpolation))
				.append(" extra=").append(this.extrapolated);
		if (this.traceAuto && (before != null) && (before.tick >= 40)
				&& ((this.traceUnit == 0) || (Extrapolator.rowOf(before.units, this.traceUnit, -1) < 0))) {
			// a unit that is alive and walked since the frame before; another one once it is gone
			this.traceUnit = 0;
			final SimFrame previous = framePrevious();
			for (int i = 0; (previous != null) && (i < before.units.count); i++) {
				final int other = previous.units.indexOf(before.units.handle[i]);
				if ((other >= 0) && !before.units.has(i, UnitFrameState.DEAD) && (Math.abs(before.units.x[i]
						- previous.units.x[other]) + Math.abs(before.units.y[i] - previous.units.y[other]) > 1f)) {
					this.traceUnit = before.units.handle[i];
					break;
				}
			}
		}
		if ((this.traceUnit != 0) && Extrapolator.sampleUnit(this, this.traceUnit, this.tracePose)) {
			line.append(String.format(" x=%.2f y=%.2f", this.tracePose.x, this.tracePose.y));
		}
		try {
			this.trace.write(line.append('\n').toString());
			this.trace.flush();
		}
		catch (final IOException e) {
			this.trace = null;
		}
	}

	/** Finds the frames around the presentation time and the weight between them. */
	private void findFrames(final long visible) {
		final long oldest = Math.max(0, this.ring.oldestHeld());
		if (this.beforeSequence < oldest) {
			this.beforeSequence = oldest;
		}
		while (((this.beforeSequence + 1) < visible)
				&& (this.ring.get(this.beforeSequence + 1).gameTimeMillis <= this.presentation)) {
			this.beforeSequence++;
		}
		final SimFrame before = this.ring.get(this.beforeSequence);
		this.afterSequence = -1;
		this.interpolation = 0f;
		if (before == null) {
			return;
		}
		if ((before.gameTimeMillis <= this.presentation) && ((this.beforeSequence + 1) < visible)) {
			long after = this.beforeSequence + 1;
			final long time = this.ring.get(after).gameTimeMillis;
			while (((after + 1) < visible) && (this.ring.get(after + 1).gameTimeMillis == time)) {
				after++;
			}
			this.afterSequence = after;
			final double span = time - before.gameTimeMillis;
			this.interpolation = span <= 0 ? 1f
					: (float) Math.min(1.0, Math.max(0.0, (this.presentation - before.gameTimeMillis) / span));
		}
		else if (before.gameTimeMillis <= this.presentation) {
			this.interpolation = (float) ((this.presentation - before.gameTimeMillis) / DELAY_MILLIS);
		}
	}

	@Override
	public long presentationMillis() {
		return Math.round(this.presentation);
	}

	@Override
	public float presentationMillisExact() {
		return (float) this.presentation;
	}

	@Override
	public SimFrame frameBefore() {
		return this.beforeSequence < 0 ? null : this.ring.get(this.beforeSequence);
	}

	@Override
	public SimFrame frameAfter() {
		return this.afterSequence < 0 ? null : this.ring.get(this.afterSequence);
	}

	@Override
	public SimFrame framePrevious() {
		return this.beforeSequence < 1 ? null : this.ring.get(this.beforeSequence - 1);
	}

	@Override
	public float interpolation() {
		return this.interpolation;
	}

	@Override
	public State state() {
		return this.state;
	}

	@Override
	public long extrapolatedMillis() {
		return this.extrapolated;
	}

	@Override
	public long leadMillis() {
		return this.lead;
	}

	@Override
	public long retainFromSequence() {
		return Math.max(0, this.beforeSequence - 1);
	}

	/** Releases the frames the clock is past, but never one the caller has not played yet. */
	public void releaseConsumed(final long playedUpTo) {
		this.ring.release(Math.min(retainFromSequence(), playedUpTo));
	}

	@Override
	public void setModelWait(final boolean waiting) {
		this.modelWait = waiting;
	}

	@Override
	public void setTimeScale(final float scale) {
		this.timeScale = scale;
	}

	@Override
	public void setPaused(final boolean paused) {
		this.paused = paused;
	}

	@Override
	public void reset(final long gameTimeMillis) {
		this.started = false;
		this.beforeSequence = -1;
		this.afterSequence = -1;
		this.interpolation = 0f;
		this.presentation = gameTimeMillis;
		this.state = State.NORMAL;
		this.lead = 0;
		this.extrapolated = 0;
		this.stallScanned = 0;
		this.stallSequence = -1;
	}
}
