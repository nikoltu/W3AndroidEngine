package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityEntangledMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityOverlayedMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.AllyColorFilter;

/**
 * Fills a {@link SimFrame} from the live simulation at the end of a step. It runs on the thread that runs steps, only
 * reads the simulation (no state, random stream or list order is touched) and allocates nothing once its arrays have
 * grown to the size of the game.
 * <p>
 * What a frame carries: every unit (also the ones made later in the step and the ones waiting to be removed), every
 * item, every destructable, every missile that is a {@link CProjectile}, the local player's fog, the step number, the
 * game time and the time of day. Rows are sorted by handle; a missile has no handle of its own, so the writer numbers
 * it when it first sees it and keeps that number while it lives. Columns the simulation does not hold are written as
 * neutral values: {@link UnitFrameState#scale} is 1, a missile's z is 0, the height of a ground unit is 0 (the render
 * side adds the terrain and bridge height), and the unit colours are the owner's colour and the vertex colour a
 * script gave (0 when none).
 */
public final class SimFrameWriter implements CSimulation.StepEndListener {
	/** Starts the debug writer on every simulation made in the process (frames are released at once). */
	public static final String PROPERTY = "warsmash.simthread.writer";
	/** As {@link #PROPERTY}, and checks every frame against the live state; see {@link FrameVerifier}. */
	public static final String VERIFY_PROPERTY = "warsmash.simthread.verify";
	/** Player the debug writer takes as the local one; 0 without it. */
	public static final String PLAYER_PROPERTY = "warsmash.simthread.player";

	private static final int REPORT_STEPS = 1000;

	private final SimFrameRing ring;
	private int localPlayerIndex;
	private boolean autoRelease;
	private FrameVerifier verifier;
	private BiConsumer<CSimulation, SimFrame> uiPart;
	private java.util.function.Consumer<CSimulation> stepHook;
	private ToIntFunction<CProjectile> projectileIds;

	private long[] keys = new long[64];
	private CUnit[] unitRows = new CUnit[64];
	private CItem[] itemRows = new CItem[64];
	private CDestructable[] destructableRows = new CDestructable[64];

	private CProjectile[] knownProjectiles = new CProjectile[16];
	private int[] knownProjectileIds = new int[16];
	private int knownProjectileCount;
	private CProjectile[] nextProjectiles = new CProjectile[16];
	private int[] nextProjectileIds = new int[16];
	private int nextProjectileId = 1;

	private byte[] lastFog = new byte[0];
	private int lastFogLength = -1;
	private int fogVersion;

	private long steps;
	private long droppedFrames;
	private long totalNanos;
	private long maxNanos;
	private long windowNanos;
	private long rowsTotal;
	private long projectileRowsTotal;
	private long pathUnitsTotal;
	private long fogChanges;

	/** @param ring where frames are published; null when only {@link #write} is used */
	public SimFrameWriter(final SimFrameRing ring, final int localPlayerIndex) {
		this.ring = ring;
		this.localPlayerIndex = localPlayerIndex;
	}

	/**
	 * For a process that has no render side: the writer is made from the system properties and every published frame
	 * is released at once. Null when the properties are not set.
	 */
	public static SimFrameWriter installFromProperties(final CSimulation simulation) {
		final boolean verify = System.getProperty(VERIFY_PROPERTY) != null;
		if (!verify && (System.getProperty(PROPERTY) == null)) {
			return null;
		}
		final int player = Integer.getInteger(PLAYER_PROPERTY, 0);
		final SimFrameWriter writer = new SimFrameWriter(
				new SimFrameRing(SimFrameRing.DEFAULT_CAPACITY, new RenderEventPool()), player);
		writer.autoRelease = true;
		if (verify) {
			writer.verifier = new FrameVerifier(player);
		}
		return writer;
	}

	public void setLocalPlayerIndex(final int localPlayerIndex) {
		this.localPlayerIndex = localPlayerIndex;
	}

	public int getLocalPlayerIndex() {
		return this.localPlayerIndex;
	}

	/** Releases every frame at once after publishing it: for runs without a reader. */
	public void setAutoRelease(final boolean autoRelease) {
		this.autoRelease = autoRelease;
	}

	/** Fills the interface part of every frame, after the world part (the {@code UiFrameWriter}); null for none. */
	/** Runs at the end of every step on the simulation thread, whether or not a frame is written. */
	public void setStepHook(final java.util.function.Consumer<CSimulation> stepHook) {
		this.stepHook = stepHook;
	}

	public void setUiPart(final BiConsumer<CSimulation, SimFrame> uiPart) {
		this.uiPart = uiPart;
	}

	/**
	 * Numbers the missiles with the ids the events use for them, so a render peer made from an event finds its row in
	 * the frame; null numbers them here, in list order.
	 */
	public void setProjectileIds(final ToIntFunction<CProjectile> projectileIds) {
		this.projectileIds = projectileIds;
	}

	public void setVerifier(final FrameVerifier verifier) {
		this.verifier = verifier;
	}

	public FrameVerifier getVerifier() {
		return this.verifier;
	}

	public long getSteps() {
		return this.steps;
	}

	/** Steps whose frame was not written because the ring was full. */
	public long getDroppedFrames() {
		return this.droppedFrames;
	}

	public long getTotalNanos() {
		return this.totalNanos;
	}

	public long getMaxNanos() {
		return this.maxNanos;
	}

	@Override
	public void onStepEnd(final CSimulation simulation) {
		if (this.stepHook != null) {
			this.stepHook.accept(simulation);
		}
		if (this.ring == null) {
			return;
		}
		final SimFrame frame = this.ring.beginWrite();
		if (frame == null) {
			this.droppedFrames++;
			return;
		}
		final long start = System.nanoTime();
		write(simulation, frame);
		final long took = System.nanoTime() - start;
		this.steps++;
		this.totalNanos += took;
		this.windowNanos += took;
		if (took > this.maxNanos) {
			this.maxNanos = took;
		}
		this.rowsTotal += frame.units.count + frame.items.count + frame.trees.count + frame.projectiles.count;
		this.projectileRowsTotal += frame.projectiles.count;
		for (int i = 0; i < frame.units.count; i++) {
			if (frame.units.pathCount[i] > 0) {
				this.pathUnitsTotal++;
			}
		}
		if (frame.fogChanged) {
			this.fogChanges++;
		}
		if (this.verifier != null) {
			this.verifier.verify(simulation, frame);
		}
		this.ring.publish();
		if (this.autoRelease) {
			this.ring.release(this.ring.publishedCount());
		}
		if ((this.steps % REPORT_STEPS) == 0) {
			report(frame);
		}
	}

	private void report(final SimFrame frame) {
		Log.info("SIMFRAME steps=" + this.steps + " dropped=" + this.droppedFrames + " avg_us="
				+ ((this.totalNanos / this.steps) / 1000) + " window_us=" + ((this.windowNanos / REPORT_STEPS) / 1000)
				+ " max_us=" + (this.maxNanos / 1000) + " rows=" + (this.rowsTotal / this.steps) + " units="
				+ frame.units.count + " items=" + frame.items.count + " trees=" + frame.trees.count + " projectiles="
				+ frame.projectiles.count + " projectileRows=" + this.projectileRowsTotal + " pathUnitRows="
				+ this.pathUnitsTotal + " fogChanges=" + this.fogChanges
				+ (this.verifier == null ? "" : " verifyMismatches=" + this.verifier.getMismatches()));
		this.windowNanos = 0;
	}

	/** Writes everything into the frame, which the caller got from the ring and has not published yet. */
	public void write(final CSimulation game, final SimFrame frame) {
		frame.tick = game.getGameTurnTick();
		frame.gameTimeMillis = Math.round(frame.tick * 1000.0 * WarsmashConstants.SIMULATION_STEP_TIME);
		frame.timeOfDay = game.getGameTimeOfDay();
		frame.falseTimeOfDay = game.isFalseTimeOfDay();
		final CPlayer local = (this.localPlayerIndex >= 0) && (this.localPlayerIndex < game.getPlayers().size())
				? game.getPlayer(this.localPlayerIndex)
				: null;
		final CPlayerFogOfWar fog = local == null ? null : local.getFogOfWar();
		final PathingGrid grid = game.getPathingGrid();
		writeUnits(game, frame.units, local, fog, grid);
		writeProjectiles(game, frame.projectiles);
		writeItems(game, frame.items, fog, grid);
		writeDestructables(game, frame.trees);
		writeFog(frame, fog);
		if (this.uiPart != null) {
			this.uiPart.accept(game, frame);
		}
	}

	private void writeUnits(final CSimulation game, final UnitFrameState state, final CPlayer local,
			final CPlayerFogOfWar fog, final PathingGrid grid) {
		final List<CUnit> units = game.getUnits();
		final List<CUnit> pending = game.getPendingNewUnits();
		final int unitCount = units.size();
		final int total = unitCount + pending.size();
		if (this.unitRows.length < total) {
			this.unitRows = new CUnit[Math.max(total, this.unitRows.length * 2)];
		}
		if (this.keys.length < total) {
			this.keys = new long[Math.max(total, this.keys.length * 2)];
		}
		boolean sorted = true;
		int previous = Integer.MIN_VALUE;
		for (int i = 0; i < total; i++) {
			final CUnit unit = i < unitCount ? units.get(i) : pending.get(i - unitCount);
			this.unitRows[i] = unit;
			final int handle = unit.getHandleId();
			this.keys[i] = (((long) handle) << 32) | i;
			if (handle <= previous) {
				sorted = false;
			}
			previous = handle;
		}
		if (!sorted) {
			Arrays.sort(this.keys, 0, total);
		}
		for (int i = 0; i < total; i++) {
			final CUnit unit = this.unitRows[sorted ? i : (int) this.keys[i]];
			writeUnit(game, state, state.addRow(), unit, local, fog, grid);
		}
		Arrays.fill(this.unitRows, 0, total, null);
	}

	private void writeUnit(final CSimulation game, final UnitFrameState state, final int row, final CUnit unit,
			final CPlayer local, final CPlayerFogOfWar fog, final PathingGrid grid) {
		final int owner = unit.getPlayerIndex();
		final float x = unit.getX();
		final float y = unit.getY();
		final boolean dead = unit.isDead();
		final MovementType movementType = unit.getMovementType();
		int flags = 0;
		if (unit.isHidden()) {
			flags |= UnitFrameState.HIDDEN;
		}
		if (dead) {
			flags |= UnitFrameState.DEAD;
		}
		if (local == null) {
			flags |= UnitFrameState.VISIBLE_TO_LOCAL;
		}
		else if (dead ? fog.isVisible(game, grid, x, y) : unit.isVisible(game, this.localPlayerIndex)) {
			flags |= UnitFrameState.VISIBLE_TO_LOCAL;
		}
		if (unit.isBuilding()) {
			flags |= UnitFrameState.BUILDING;
		}
		final int queueFirst = unit.getQueueFirstTypeId();
		if (unit.getBuildQueueTypes()[0] != null) {
			flags |= UnitFrameState.BUSY;
		}
		if (unit.isHero()) {
			flags |= UnitFrameState.HERO;
		}
		if (movementType == MovementType.FLY) {
			flags |= UnitFrameState.FLYING;
		}
		if (unit.getClassifications().contains(CUnitClassification.SUMMONED)) {
			flags |= UnitFrameState.SUMMONED;
		}
		final boolean boneCorpse = unit.isBoneCorpse();
		final boolean corpse = unit.isCorpse();
		if (corpse) {
			flags |= UnitFrameState.CORPSE;
		}
		if (boneCorpse) {
			flags |= UnitFrameState.BONE_CORPSE;
		}
		if (unit.isTeamGlowShown()) {
			flags |= UnitFrameState.TEAM_GLOW;
		}
		if (unit.isIllusion() && (local != null) && local.hasAlliance(owner, CAllianceType.PASSIVE)) {
			flags |= UnitFrameState.ILLUSION_ALLY;
		}
		final CTimer fadeTimer = unit.getFadeTimer();
		final boolean fading = (fadeTimer != null) && fadeTimer.isRunning();
		if (fading) {
			flags |= UnitFrameState.FADING;
		}
		final boolean constructing = unit.isConstructingOrUpgrading();
		if (constructing) {
			flags |= UnitFrameState.CONSTRUCTING;
		}
		if (unit.isConstructing()) {
			flags |= UnitFrameState.RISING;
		}
		if (local != null) {
			final int fakePlayer = unit.getFakePlayerIndex();
			if (!local.hasAlliance(fakePlayer, CAllianceType.PASSIVE)) {
				flags |= UnitFrameState.SELECT_ENEMY;
			}
			else if (local.hasAlliance(owner, CAllianceType.SHARED_CONTROL)
					|| local.hasAlliance(fakePlayer, CAllianceType.SHARED_CONTROL)) {
				flags |= UnitFrameState.SELECT_FRIEND;
			}
		}
		if (unit.isMovementOnWaterAllowed() || (movementType == MovementType.HOVER)
				|| (movementType == MovementType.FLY)) {
			flags |= UnitFrameState.ABOVE_WATER;
		}
		if (game.isNeutralAggressive(owner)) {
			flags |= UnitFrameState.CREEP;
		}
		if (unit.isBuilding() && (unit.getAbilityByRawcode(CUnitData.ALARM_ABILITY_ID) != null)) {
			flags |= UnitFrameState.ALARM;
		}
		if (unit.isHideMinimapIcon()) {
			flags |= UnitFrameState.HIDE_MINIMAP_ICON;
		}
		if (unit.isUsesAltMinimapIcon()) {
			flags |= UnitFrameState.ALT_MINIMAP_ICON;
		}
		if (unit.getMaximumMana() > 0) {
			flags |= UnitFrameState.HAS_MANA;
		}
		if (local != null) {
			if (unit.isSelectable(game, this.localPlayerIndex)) {
				flags |= UnitFrameState.SELECTABLE;
			}
			if (local.hasAlliance(unit.getFakePlayerIndex(), CAllianceType.PASSIVE)) {
				flags |= UnitFrameState.TEAM_ALLIED;
			}
			final boolean ownerShared = local.hasAlliance(owner, CAllianceType.SHARED_CONTROL);
			if (ownerShared) {
				flags |= UnitFrameState.CONTROL_SHARED;
			}
			if (ownerShared || local.hasAlliance(unit.getFakePlayerIndex(), CAllianceType.SHARED_CONTROL)) {
				flags |= UnitFrameState.TEAM_CONTROL_SHARED;
			}
		}
		final int maxLife = unit.getMaximumLife();
		final int maxMana = unit.getMaximumMana();
		final CPlayer ownerPlayer = game.getPlayer(owner);
		state.handle[row] = unit.getHandleId();
		state.typeId[row] = unit.getTypeId().getValue();
		state.owner[row] = owner;
		state.x[row] = x;
		state.y[row] = y;
		state.z[row] = unit.getFlyHeight();
		state.facing[row] = unit.getFacing();
		state.speed[row] = unit.isMoving() ? unit.getSpeed() : 0;
		state.scale[row] = 1f;
		state.movementType[row] = (byte) movementType.ordinal();
		state.flags[row] = flags;
		state.invisLevels[row] = unit.getInvisLevels();
		state.targetClass[row] = (byte) targetClass(game, unit, owner, local);
		state.hpFraction[row] = maxLife > 0 ? unit.getLife() / maxLife : 0f;
		state.manaFraction[row] = maxMana > 0 ? unit.getMana() / maxMana : 0f;
		state.playerColor[row] = ownerPlayer == null ? owner : ownerPlayer.getColor();
		state.vertexColor[row] = packColor(unit.getVertexColor());
		state.orderId[row] = unit.getCurrentOrderId();
		state.queueFirstType[row] = queueFirst;
		state.pathCount[row] = (byte) unit.copyPathPoints(state.path, row * UnitFrameState.PATH_POINTS * 2,
				UnitFrameState.PATH_POINTS);
		state.teamPlayer[row] = unit.getFakePlayerIndex();
		state.fadeRemaining[row] = fading ? Math.max(0f, fadeTimer.getTimeoutTime() - fadeTimer.getElapsed(game)) : 0f;
		state.fadeTimeout[row] = fading ? fadeTimer.getTimeoutTime() : 0f;
		float constructRatio = 0f;
		if (constructing) {
			final War3ID upgradeIdType = unit.getUpgradeIdType();
			final CUnitType constructedType = upgradeIdType != null ? game.getUnitData().getUnitType(upgradeIdType)
					: unit.getUnitType();
			constructRatio = unit.getConstructionProgress() / constructedType.getBuildTime();
		}
		state.constructRatio[row] = constructRatio;
		state.decayTime[row] = boneCorpse ? unit.getEndingDecayTime(game)
				: (corpse ? game.getGameplayConstants().getDecayTime() : 0f);
		state.landmark[row] = (byte) landmarkKind(unit);
		state.relation[row] = (byte) (local == null ? 0
				: AllyColorFilter.relation(game, this.localPlayerIndex, state.teamPlayer[row]));
		state.level[row] = unit.getUnitType().getLevel();
		state.collisionSize[row] = unit.getUnitType().getCollisionSize();
	}

	/** The minimap landmark the unit is (see {@code RenderUnit.LANDMARK_*}), from its abilities and type. */
	private static int landmarkKind(final CUnit unit) {
		if (unit.getGoldMineData() != null) {
			return 1;
		}
		final CAbilityOverlayedMine overlayed = unit.getOverlayedGoldMineData();
		if (overlayed != null) {
			return overlayed instanceof CAbilityEntangledMine ? 2 : 3;
		}
		return unit.getUnitType().isNeutralBuildingShowMinimapIcon() ? 4 : 0;
	}

	private static int targetClass(final CSimulation game, final CUnit unit, final int owner, final CPlayer local) {
		int result = 0;
		if (local != null) {
			if (owner == local.getId()) {
				result |= UnitFrameState.TARGET_OWN | UnitFrameState.TARGET_ALLY;
			}
			else if (unit.isUnitAlly(local)) {
				result |= UnitFrameState.TARGET_ALLY;
			}
			else {
				result |= UnitFrameState.TARGET_ENEMY;
			}
		}
		if (game.isNeutralPlayer(owner)) {
			result |= UnitFrameState.TARGET_NEUTRAL;
		}
		if (unit.getTargetedAs().contains(CTargetType.MECHANICAL)) {
			result |= UnitFrameState.TARGET_MECHANICAL;
		}
		if (unit.isInvulnerable()) {
			result |= UnitFrameState.TARGET_INVULNERABLE;
		}
		return result;
	}

	/** Alpha, red, green, blue of a vertex colour (components 0..1) in the four bytes of an int; 0 for none. */
	static int packColor(final float[] color) {
		if (color == null) {
			return 0;
		}
		return (channel(color.length > 3 ? color[3] : 1f) << 24) | (channel(color[0]) << 16) | (channel(color[1]) << 8)
				| channel(color[2]);
	}

	private static int channel(final float value) {
		return Math.max(0, Math.min(255, Math.round(value * 255f)));
	}

	private void writeProjectiles(final CSimulation game, final WorldObjectFrameState state) {
		final List<CEffect> live = game.getProjectiles();
		final List<CEffect> pending = game.getPendingProjectiles();
		final int liveCount = live.size();
		final int total = liveCount + pending.size();
		if (this.nextProjectiles.length < total) {
			final int capacity = Math.max(total, this.nextProjectiles.length * 2);
			this.nextProjectiles = new CProjectile[capacity];
			this.nextProjectileIds = new int[capacity];
		}
		// The list only loses elements in place and gets new ones at its end, so the elements seen last step come
		// again in the same order and a single pass over them finds each one's number.
		int known = 0;
		boolean searching = true;
		int count = 0;
		for (int i = 0; i < total; i++) {
			final CEffect effect = i < liveCount ? live.get(i) : pending.get(i - liveCount);
			if (!(effect instanceof CProjectile)) {
				continue;
			}
			final CProjectile projectile = (CProjectile) effect;
			int id = this.projectileIds != null ? this.projectileIds.applyAsInt(projectile) : 0;
			if ((id == 0) && searching) {
				int at = known;
				while ((at < this.knownProjectileCount) && (this.knownProjectiles[at] != projectile)) {
					at++;
				}
				if (at < this.knownProjectileCount) {
					id = this.knownProjectileIds[at];
					known = at + 1;
				}
				else {
					searching = false;
				}
			}
			if (id == 0) {
				id = this.nextProjectileId++;
			}
			this.nextProjectiles[count] = projectile;
			this.nextProjectileIds[count] = id;
			count++;
			final int row = state.addRow();
			state.handle[row] = id;
			state.x[row] = projectile.getX();
			state.y[row] = projectile.getY();
			state.z[row] = 0f;
			state.speed[row] = projectile.getSpeed();
			final AbilityTarget target = projectile.getTarget();
			state.target[row] = target instanceof CWidget ? ((CWidget) target).getHandleId() : 0;
			state.life[row] = 1f;
			state.targetX[row] = projectile.getTargetX();
			state.targetY[row] = projectile.getTargetY();
			state.flags[row] = projectile.isDone() ? WorldObjectFrameState.DEAD : WorldObjectFrameState.VISIBLE;
		}
		if (this.projectileIds != null) {
			sortRows(state);
		}
		Arrays.fill(this.knownProjectiles, 0, this.knownProjectileCount, null);
		final CProjectile[] swapProjectiles = this.knownProjectiles;
		final int[] swapIds = this.knownProjectileIds;
		this.knownProjectiles = this.nextProjectiles;
		this.knownProjectileIds = this.nextProjectileIds;
		this.knownProjectileCount = count;
		this.nextProjectiles = swapProjectiles;
		this.nextProjectileIds = swapIds;
	}

	/** Insertion sort by handle: the ids come from first use by an event, so they are not always in list order. */
	private static void sortRows(final WorldObjectFrameState state) {
		for (int i = 1; i < state.count; i++) {
			int j = i;
			while ((j > 0) && (state.handle[j - 1] > state.handle[j])) {
				swapRows(state, j - 1, j);
				j--;
			}
		}
	}

	private static void swapRows(final WorldObjectFrameState s, final int a, final int b) {
		int ti = s.handle[a]; s.handle[a] = s.handle[b]; s.handle[b] = ti;
		float tf = s.x[a]; s.x[a] = s.x[b]; s.x[b] = tf;
		tf = s.y[a]; s.y[a] = s.y[b]; s.y[b] = tf;
		tf = s.z[a]; s.z[a] = s.z[b]; s.z[b] = tf;
		tf = s.speed[a]; s.speed[a] = s.speed[b]; s.speed[b] = tf;
		ti = s.target[a]; s.target[a] = s.target[b]; s.target[b] = ti;
		tf = s.life[a]; s.life[a] = s.life[b]; s.life[b] = tf;
		ti = s.flags[a]; s.flags[a] = s.flags[b]; s.flags[b] = ti;
		tf = s.targetX[a]; s.targetX[a] = s.targetX[b]; s.targetX[b] = tf;
		tf = s.targetY[a]; s.targetY[a] = s.targetY[b]; s.targetY[b] = tf;
	}

	private void writeItems(final CSimulation game, final WorldObjectFrameState state, final CPlayerFogOfWar fog,
			final PathingGrid grid) {
		final List<CItem> items = game.getItems();
		final int total = items.size();
		if (this.itemRows.length < total) {
			this.itemRows = new CItem[Math.max(total, this.itemRows.length * 2)];
		}
		if (this.keys.length < total) {
			this.keys = new long[Math.max(total, this.keys.length * 2)];
		}
		boolean sorted = true;
		int previous = Integer.MIN_VALUE;
		for (int i = 0; i < total; i++) {
			final CItem item = items.get(i);
			this.itemRows[i] = item;
			final int handle = item.getHandleId();
			this.keys[i] = (((long) handle) << 32) | i;
			if (handle <= previous) {
				sorted = false;
			}
			previous = handle;
		}
		if (!sorted) {
			Arrays.sort(this.keys, 0, total);
		}
		for (int i = 0; i < total; i++) {
			final CItem item = this.itemRows[sorted ? i : (int) this.keys[i]];
			final int row = state.addRow();
			final float x = item.getX();
			final float y = item.getY();
			int flags = 0;
			if (!item.isHidden() && ((fog == null) || fog.isVisible(game, grid, x, y))) {
				flags |= WorldObjectFrameState.VISIBLE;
			}
			if (item.isDead()) {
				flags |= WorldObjectFrameState.DEAD;
			}
			if (item.getContainedUnit() != null) {
				flags |= WorldObjectFrameState.CARRIED;
			}
			state.handle[row] = item.getHandleId();
			state.x[row] = x;
			state.y[row] = y;
			state.z[row] = item.getFlyHeight();
			state.speed[row] = 0f;
			state.target[row] = item.getScriptColor();
			state.life[row] = item.getLife();
			state.flags[row] = flags;
		}
		Arrays.fill(this.itemRows, 0, total, null);
	}

	private void writeDestructables(final CSimulation game, final WorldObjectFrameState state) {
		final List<CDestructable> destructables = game.getDestructables();
		final int total = destructables.size();
		if (this.destructableRows.length < total) {
			this.destructableRows = new CDestructable[Math.max(total, this.destructableRows.length * 2)];
		}
		if (this.keys.length < total) {
			this.keys = new long[Math.max(total, this.keys.length * 2)];
		}
		boolean sorted = true;
		int previous = Integer.MIN_VALUE;
		for (int i = 0; i < total; i++) {
			final CDestructable destructable = destructables.get(i);
			this.destructableRows[i] = destructable;
			final int handle = destructable.getHandleId();
			this.keys[i] = (((long) handle) << 32) | i;
			if (handle <= previous) {
				sorted = false;
			}
			previous = handle;
		}
		if (!sorted) {
			Arrays.sort(this.keys, 0, total);
		}
		for (int i = 0; i < total; i++) {
			final CDestructable destructable = this.destructableRows[sorted ? i : (int) this.keys[i]];
			final int row = state.addRow();
			final float maxLife = destructable.getMaxLife();
			state.handle[row] = destructable.getHandleId();
			state.x[row] = destructable.getX();
			state.y[row] = destructable.getY();
			state.z[row] = destructable.getFlyHeight();
			state.speed[row] = 0f;
			state.target[row] = 0;
			state.life[row] = maxLife > 0 ? destructable.getLife() / maxLife : 0f;
			state.flags[row] = WorldObjectFrameState.VISIBLE
					| (destructable.isDead() ? WorldObjectFrameState.DEAD : 0)
					| (destructable.isBlighted() ? WorldObjectFrameState.BLIGHTED : 0)
					| (destructable.getDestType().getTargetedAs().contains(CTargetType.TREE)
							? WorldObjectFrameState.TREE : 0);
		}
		Arrays.fill(this.destructableRows, 0, total, null);
	}

	/** The fog goes into the frame only when it differs from what the last frame that had it carried. */
	private void writeFog(final SimFrame frame, final CPlayerFogOfWar fog) {
		if (fog == null) {
			frame.fogVersion = this.fogVersion;
			return;
		}
		final byte[] cells = fog.getFogOfWarBuffer().array();
		final int length = cells.length;
		boolean changed = length != this.lastFogLength;
		if (!changed) {
			final byte[] last = this.lastFog;
			for (int i = 0; i < length; i++) {
				if (cells[i] != last[i]) {
					changed = true;
					break;
				}
			}
		}
		if (changed) {
			if (this.lastFog.length < length) {
				this.lastFog = new byte[length];
			}
			System.arraycopy(cells, 0, this.lastFog, 0, length);
			this.lastFogLength = length;
			this.fogVersion++;
			frame.setFog(cells, length, this.fogVersion);
		}
		else {
			frame.fogVersion = this.fogVersion;
		}
	}
}
