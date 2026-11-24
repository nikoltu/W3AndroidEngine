package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * Script terrain deformations (TerrainDeformCrater, Ripple, Wave, Random). They move the drawn ground and the heights
 * scripts read (GetLocationZ), not pathing. Each one adds a height offset to the terrain corners it covers; offsets
 * of overlapping deformations add up. A permanent crater leaves its final shape in the ground, rounded down to the
 * quarter units the map stores heights in.
 */
public final class CTerrainDeformations {
	private static final float CELL = 128f;

	private final List<Deformation> active = new ArrayList<>();
	private final UpdateTimer timer = new UpdateTimer(this);
	private float[] permanent;
	private int columns;
	private int rows;
	private float originX;
	private float originY;
	private int lastTick = -1;
	private final int[] pushedRect = { 0, 0, -1, -1 };
	private int randomSeed = 0x2F6E2B1;

	public Deformation crater(final CSimulation simulation, final float x, final float y, final float radius,
			final float depth, final int durationMs, final boolean permanent) {
		return add(simulation, new Crater(x, y, radius, depth, durationMs, permanent));
	}

	public Deformation ripple(final CSimulation simulation, final float x, final float y, final float radius,
			final float depth, final int durationMs, final int count, final float spaceWaves, final float timeWaves,
			final float radiusStartPct, final boolean limitNeg) {
		return ripple(simulation, x, y, radius, depth, durationMs, count, spaceWaves, timeWaves, radiusStartPct,
				limitNeg, 0);
	}

	/**
	 * The ripples of abilities start their waves a little after they are made: the ground shows nothing until
	 * delayMs has passed, and the wave runs from there.
	 */
	public Deformation ripple(final CSimulation simulation, final float x, final float y, final float radius,
			final float depth, final int durationMs, final int count, final float spaceWaves, final float timeWaves,
			final float radiusStartPct, final boolean limitNeg, final int delayMs) {
		return add(simulation, new Ripple(x, y, radius, depth, durationMs, count, spaceWaves, timeWaves,
				radiusStartPct, limitNeg, delayMs));
	}

	public Deformation wave(final CSimulation simulation, final float x, final float y, final float dirX,
			final float dirY, final float distance, final float speed, final float radius, final float depth,
			final int trailTimeMs, final int count) {
		return add(simulation,
				new Wave(x, y, dirX, dirY, distance, speed, radius, depth, trailTimeMs, count));
	}

	/**
	 * A mound (positive height) or hollow that grows over the sink time, stays for the static time and settles over
	 * the fill time, in the shape of a quarter cosine from the middle to the radius.
	 */
	public Deformation bowl(final CSimulation simulation, final float x, final float y, final float radius,
			final float height, final int sinkTimeMs, final int staticTimeMs, final int fillTimeMs) {
		return add(simulation, new Bowl(x, y, radius, height, sinkTimeMs, staticTimeMs, fillTimeMs));
	}

	public Deformation random(final CSimulation simulation, final float x, final float y, final float radius,
			final float minDelta, final float maxDelta, final int durationMs, final int updateIntervalMs) {
		this.randomSeed = (this.randomSeed * 1103515245) + 12345;
		return add(simulation,
				new RandomDeformation(x, y, radius, minDelta, maxDelta, durationMs, updateIntervalMs, this.randomSeed));
	}

	/**
	 * The original's deformation handles are slot numbers and the first slot is 0, which scripts see as null: stopping
	 * null stops the deformation in slot 0.
	 */
	public void stop(final CSimulation simulation, final Deformation deformation, final int durationMs) {
		final Deformation target = deformation != null ? deformation : bySlot(0);
		if ((target == null) || !this.active.contains(target)) {
			return;
		}
		sync(simulation);
		if (durationMs <= 0) {
			this.active.remove(target);
		}
		else if (target.stopDuration < 0) {
			target.stopStart = target.elapsed;
			target.stopDuration = durationMs / 1000f;
		}
		push(simulation);
	}

	public void stopAll(final CSimulation simulation) {
		sync(simulation);
		this.active.clear();
		push(simulation);
	}

	private Deformation bySlot(final int slot) {
		for (final Deformation deformation : this.active) {
			if (deformation.slot == slot) {
				return deformation;
			}
		}
		return null;
	}

	private Deformation add(final CSimulation simulation, final Deformation deformation) {
		if (!initGrid(simulation)) {
			return deformation;
		}
		sync(simulation);
		int slot = 0;
		while (bySlot(slot) != null) {
			slot++;
		}
		deformation.slot = slot;
		deformation.startTick = simulation.getGameTurnTick();
		deformation.bounds(this);
		this.active.add(deformation);
		this.timer.setRepeats(true);
		if (!this.timer.isRunning()) {
			this.timer.setTimeoutTime(WarsmashConstants.SIMULATION_STEP_TIME);
			this.timer.start(simulation);
		}
		return deformation;
	}

	private boolean initGrid(final CSimulation simulation) {
		if (this.columns > 0) {
			return true;
		}
		final CTerrainData terrain = simulation.getTerrainData();
		if (terrain == null) {
			return false;
		}
		final int[] size = terrain.getCornerCount();
		this.columns = size[0];
		this.rows = size[1];
		this.originX = -terrain.getTerrainSpaceX(0) * CELL;
		this.originY = -terrain.getTerrainSpaceY(0) * CELL;
		return true;
	}

	/** Brings the ground up to the current game tick; heights read by scripts go through here. */
	public void sync(final CSimulation simulation) {
		final int tick = simulation.getGameTurnTick();
		if ((tick == this.lastTick) || this.active.isEmpty()) {
			this.lastTick = tick;
			return;
		}
		this.lastTick = tick;
		for (int i = this.active.size() - 1; i >= 0; i--) {
			final Deformation deformation = this.active.get(i);
			deformation.elapsed = (tick - deformation.startTick) * WarsmashConstants.SIMULATION_STEP_TIME;
			final boolean stopped = (deformation.stopDuration >= 0)
					&& (deformation.elapsed >= (deformation.stopStart + deformation.stopDuration));
			if (stopped) {
				this.active.remove(i);
			}
			else if (deformation.elapsed >= deformation.totalTime()) {
				this.active.remove(i);
				if (deformation.leavesShape()) {
					bake(deformation);
				}
			}
		}
		push(simulation);
	}

	private void bake(final Deformation deformation) {
		if (this.permanent == null) {
			this.permanent = new float[this.columns * this.rows];
		}
		for (int j = deformation.y0; j <= deformation.y1; j++) {
			for (int i = deformation.x0; i <= deformation.x1; i++) {
				final float offset = deformation.finalOffset(this.originX + (i * CELL), this.originY + (j * CELL));
				this.permanent[(j * this.columns) + i] += (float) Math.floor(offset * 4) / 4;
			}
		}
	}

	private void push(final CSimulation simulation) {
		int x0 = Integer.MAX_VALUE;
		int y0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		int y1 = Integer.MIN_VALUE;
		if (this.pushedRect[2] >= this.pushedRect[0]) {
			x0 = this.pushedRect[0];
			y0 = this.pushedRect[1];
			x1 = this.pushedRect[2];
			y1 = this.pushedRect[3];
		}
		for (final Deformation deformation : this.active) {
			x0 = Math.min(x0, deformation.x0);
			y0 = Math.min(y0, deformation.y0);
			x1 = Math.max(x1, deformation.x1);
			y1 = Math.max(y1, deformation.y1);
		}
		if (x1 < x0) {
			return;
		}
		pushRect(simulation, x0, y0, x1, y1);
		if (this.active.isEmpty()) {
			this.pushedRect[0] = 0;
			this.pushedRect[1] = 0;
			this.pushedRect[2] = -1;
			this.pushedRect[3] = -1;
		}
		else {
			this.pushedRect[0] = Integer.MAX_VALUE;
			this.pushedRect[1] = Integer.MAX_VALUE;
			this.pushedRect[2] = Integer.MIN_VALUE;
			this.pushedRect[3] = Integer.MIN_VALUE;
			for (final Deformation deformation : this.active) {
				this.pushedRect[0] = Math.min(this.pushedRect[0], deformation.x0);
				this.pushedRect[1] = Math.min(this.pushedRect[1], deformation.y0);
				this.pushedRect[2] = Math.max(this.pushedRect[2], deformation.x1);
				this.pushedRect[3] = Math.max(this.pushedRect[3], deformation.y1);
			}
		}
	}

	private void pushRect(final CSimulation simulation, final int x0, final int y0, final int x1, final int y1) {
		final int width = (x1 - x0) + 1;
		final int height = (y1 - y0) + 1;
		final float[] offsets = new float[width * height];
		if (this.permanent != null) {
			for (int j = 0; j < height; j++) {
				System.arraycopy(this.permanent, ((y0 + j) * this.columns) + x0, offsets, j * width, width);
			}
		}
		for (final Deformation deformation : this.active) {
			if (deformation.elapsed <= 0) {
				// Like the original, a deformation made this tick first moves the ground on the next one.
				continue;
			}
			for (int j = deformation.y0; j <= deformation.y1; j++) {
				for (int i = deformation.x0; i <= deformation.x1; i++) {
					offsets[((j - y0) * width) + (i - x0)] += deformation.stoppedFactor()
							* deformation.offset(this.originX + (i * CELL), this.originY + (j * CELL));
				}
			}
		}
		simulation.getTerrainData().setDeformation(x0, y0, width, height, offsets);
		simulation.getSimulationRenderController().setTerrainDeformation(x0, y0, width, height, offsets);
	}

	/** A loaded game starts from the map's own ground: everything deformed is put back. */
	public void restore(final CSimulation simulation) {
		if (!initGrid(simulation)) {
			return;
		}
		this.lastTick = -1;
		if (this.permanent != null) {
			pushRect(simulation, 0, 0, this.columns - 1, this.rows - 1);
		}
		sync(simulation);
	}

	private int clampColumn(final float worldX) {
		return Math.max(0, Math.min(this.columns - 1, (int) Math.floor((worldX - this.originX) / CELL)));
	}

	private int clampRow(final float worldY) {
		return Math.max(0, Math.min(this.rows - 1, (int) Math.floor((worldY - this.originY) / CELL)));
	}

	public abstract static class Deformation {
		private int slot;
		private int startTick;
		protected float elapsed;
		private float stopStart;
		private float stopDuration = -1;
		private int x0;
		private int y0;
		private int x1;
		private int y1;

		protected void bounds(final CTerrainDeformations owner, final float minX, final float minY, final float maxX,
				final float maxY) {
			this.x0 = owner.clampColumn(minX);
			this.y0 = owner.clampRow(minY);
			this.x1 = Math.min(owner.columns - 1, owner.clampColumn(maxX) + 1);
			this.y1 = Math.min(owner.rows - 1, owner.clampRow(maxY) + 1);
		}

		public int getSlot() {
			return this.slot;
		}

		abstract void bounds(CTerrainDeformations owner);

		abstract float totalTime();

		abstract float offset(float x, float y);

		boolean leavesShape() {
			return false;
		}

		float finalOffset(final float x, final float y) {
			return 0;
		}

		float stoppedFactor() {
			if (this.stopDuration < 0) {
				return 1;
			}
			return Math.max(0, 1 - ((this.elapsed - this.stopStart) / this.stopDuration));
		}
	}

	private static final class Crater extends Deformation {
		private final float x;
		private final float y;
		private final float radius;
		private final float depth;
		private final float duration;
		private final boolean permanent;

		Crater(final float x, final float y, final float radius, final float depth, final int durationMs,
				final boolean permanent) {
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.depth = depth;
			this.duration = Math.max(0, durationMs) / 1000f;
			this.permanent = permanent;
		}

		@Override
		void bounds(final CTerrainDeformations owner) {
			bounds(owner, this.x - this.radius, this.y - this.radius, this.x + this.radius, this.y + this.radius);
		}

		@Override
		float totalTime() {
			return this.duration;
		}

		private float shape(final float px, final float py, final float fraction) {
			if (this.radius <= 0) {
				return 0;
			}
			final float distance = (float) StrictMath.hypot(px - this.x, py - this.y);
			if (distance >= this.radius) {
				return 0;
			}
			return (float) (-this.depth * fraction * StrictMath.cos((Math.PI / 2) * (distance / this.radius)));
		}

		@Override
		float offset(final float px, final float py) {
			return shape(px, py, this.duration <= 0 ? 1 : Math.min(1, this.elapsed / this.duration));
		}

		@Override
		boolean leavesShape() {
			return this.permanent;
		}

		@Override
		float finalOffset(final float px, final float py) {
			return stoppedFactor() * shape(px, py, 1);
		}
	}

	private static final class Bowl extends Deformation {
		private final float x;
		private final float y;
		private final float radius;
		private final float height;
		private final float sink;
		private final float hold;
		private final float fill;

		Bowl(final float x, final float y, final float radius, final float height, final int sinkTimeMs,
				final int staticTimeMs, final int fillTimeMs) {
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.height = height;
			this.sink = Math.max(0, sinkTimeMs) / 1000f;
			this.hold = Math.max(0, staticTimeMs) / 1000f;
			this.fill = Math.max(0, fillTimeMs) / 1000f;
		}

		@Override
		void bounds(final CTerrainDeformations owner) {
			bounds(owner, this.x - this.radius, this.y - this.radius, this.x + this.radius, this.y + this.radius);
		}

		@Override
		float totalTime() {
			return this.sink + this.hold + this.fill;
		}

		@Override
		float offset(final float px, final float py) {
			final float distance = (float) StrictMath.hypot(px - this.x, py - this.y);
			if ((this.radius <= 0) || (distance >= this.radius)) {
				return 0;
			}
			final float phase;
			if (this.elapsed < this.sink) {
				phase = this.elapsed / this.sink;
			}
			else if (this.elapsed < (this.sink + this.hold)) {
				phase = 1;
			}
			else {
				phase = 1 - ((this.elapsed - this.sink - this.hold) / this.fill);
			}
			return (float) (this.height * phase * StrictMath.cos((Math.PI / 2) * (distance / this.radius)));
		}
	}

	private static final class Ripple extends Deformation {
		private final float x;
		private final float y;
		private final float radius;
		private final float depth;
		private final float period;
		private final float total;
		private final float spaceWaves;
		private final float timeWaves;
		private final float radiusStartPct;
		private final boolean limitNeg;
		private final float delay;

		Ripple(final float x, final float y, final float radius, final float depth, final int durationMs,
				final int count, final float spaceWaves, final float timeWaves, final float radiusStartPct,
				final boolean limitNeg, final int delayMs) {
			this.delay = Math.max(0, delayMs) / 1000f;
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.depth = depth;
			this.period = Math.max(0, durationMs) / 1000f;
			this.total = this.period * Math.max(1, count);
			this.spaceWaves = spaceWaves;
			this.timeWaves = timeWaves;
			this.radiusStartPct = radiusStartPct;
			this.limitNeg = limitNeg;
		}

		@Override
		void bounds(final CTerrainDeformations owner) {
			final float reach = Math.max(this.radius, this.radius * this.radiusStartPct);
			bounds(owner, this.x - reach, this.y - reach, this.x + reach, this.y + reach);
		}

		@Override
		float totalTime() {
			return this.total + this.delay;
		}

		@Override
		float offset(final float px, final float py) {
			final float time = this.elapsed - this.delay;
			if ((this.radius <= 0) || (this.total <= 0) || (time < 0)) {
				return 0;
			}
			final float progress = Math.min(1, time / this.total);
			final float currentRadius = this.radius
					* (this.radiusStartPct + ((1 - this.radiusStartPct) * progress));
			final float distance = (float) StrictMath.hypot(px - this.x, py - this.y);
			if (distance >= currentRadius) {
				return 0;
			}
			final float value = (float) (-this.depth * (1 - progress) * StrictMath.sin((Math.PI * this.spaceWaves
					* (distance / this.radius)) + (Math.PI * this.timeWaves * (time / this.period))));
			return this.limitNeg ? Math.min(0, value) : value;
		}
	}

	private static final class Wave extends Deformation {
		private final float x;
		private final float y;
		private final float dirX;
		private final float dirY;
		private final float distance;
		private final float speed;
		private final float radius;
		private final float depth;
		private final float trail;
		private final float run;
		private final int count;

		Wave(final float x, final float y, final float dirX, final float dirY, final float distance,
				final float speed, final float radius, final float depth, final int trailTimeMs, final int count) {
			this.x = x;
			this.y = y;
			final float length = (float) StrictMath.hypot(dirX, dirY);
			this.dirX = length > 0 ? dirX / length : 0;
			this.dirY = length > 0 ? dirY / length : 0;
			this.distance = Math.max(0, distance);
			this.speed = speed;
			this.radius = radius;
			this.depth = depth;
			this.trail = Math.max(0, trailTimeMs) / 1000f;
			this.run = (speed > 0 ? this.distance / speed : 0) + this.trail;
			this.count = Math.max(1, count);
		}

		@Override
		void bounds(final CTerrainDeformations owner) {
			final float endX = this.x + (this.dirX * this.distance);
			final float endY = this.y + (this.dirY * this.distance);
			bounds(owner, Math.min(this.x, endX) - this.radius, Math.min(this.y, endY) - this.radius,
					Math.max(this.x, endX) + this.radius, Math.max(this.y, endY) + this.radius);
		}

		@Override
		float totalTime() {
			return this.run * this.count;
		}

		@Override
		float offset(final float px, final float py) {
			if ((this.radius <= 0) || (this.run <= 0)) {
				return 0;
			}
			final float time = this.elapsed % this.run;
			final float lead = Math.min(this.distance, this.speed * time);
			final float tail = Math.max(0, Math.min(this.distance, this.speed * (time - this.trail)));
			final float along = ((px - this.x) * this.dirX) + ((py - this.y) * this.dirY);
			final float nearest = Math.max(tail, Math.min(lead, along));
			final float gap = (float) StrictMath.hypot(px - (this.x + (this.dirX * nearest)),
					py - (this.y + (this.dirY * nearest)));
			if (gap >= this.radius) {
				return 0;
			}
			float value = (float) (-0.5 * this.depth * (StrictMath.cos(Math.PI * (gap / this.radius)) + 1));
			final float progress = time / this.run;
			if (progress > 0.9f) {
				value *= 1 - ((progress - 0.9f) / 0.1f);
			}
			return value;
		}
	}

	private static final class RandomDeformation extends Deformation {
		private final float x;
		private final float y;
		private final float radius;
		private final float minDelta;
		private final float maxDelta;
		private final float duration;
		private final float interval;
		private final int seed;

		RandomDeformation(final float x, final float y, final float radius, final float minDelta,
				final float maxDelta, final int durationMs, final int updateIntervalMs, final int seed) {
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.minDelta = minDelta;
			this.maxDelta = maxDelta;
			this.duration = Math.max(0, durationMs) / 1000f;
			this.interval = Math.max(WarsmashConstants.SIMULATION_STEP_TIME, updateIntervalMs / 1000f);
			this.seed = seed;
		}

		@Override
		void bounds(final CTerrainDeformations owner) {
			bounds(owner, this.x - this.radius, this.y - this.radius, this.x + this.radius, this.y + this.radius);
		}

		@Override
		float totalTime() {
			return this.duration;
		}

		private float target(final float px, final float py, final int step) {
			if (step <= 0) {
				return 0;
			}
			int hash = this.seed ^ (Float.floatToIntBits(px) * 73856093) ^ (Float.floatToIntBits(py) * 19349663)
					^ (step * 83492791);
			hash ^= hash >>> 13;
			hash *= 0x5bd1e995;
			hash ^= hash >>> 15;
			final float unit = (hash & 0xFFFFFF) / (float) 0x1000000;
			return this.minDelta + ((this.maxDelta - this.minDelta) * unit);
		}

		@Override
		float offset(final float px, final float py) {
			if ((this.radius <= 0) || (StrictMath.hypot(px - this.x, py - this.y) >= this.radius)) {
				return 0;
			}
			final float steps = this.elapsed / this.interval;
			final int step = (int) steps;
			final float from = target(px, py, step);
			final float to = target(px, py, step + 1);
			return from + ((to - from) * (steps - step));
		}
	}

	static final class UpdateTimer extends CTimer {
		private final CTerrainDeformations owner;

		UpdateTimer(final CTerrainDeformations owner) {
			this.owner = owner;
		}

		@Override
		public void onFire(final CSimulation simulation) {
			this.owner.sync(simulation);
			if (this.owner.active.isEmpty()) {
				setRepeats(false);
			}
		}
	}
}
