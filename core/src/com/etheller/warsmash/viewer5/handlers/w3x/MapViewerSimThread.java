package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import com.badlogic.gdx.utils.IntMap;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.warsmash.networking.GameTurnManager;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget.UnitAnimationListenerImpl;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.BufferedAnimationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.BufferingRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.FrameEventSink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.OrderInbox;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.OrderMessage;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PresentationClockImpl;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderCallCodes;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEventPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEventPool;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SerialSimThread;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThread;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadMode;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadRunner;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrameRing;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrameWriter;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadGuard;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ThreadedOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.UiFrameSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitAnimationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.CommandErrorListener;

/**
 * Serial simulation thread of a map viewer: the simulation steps run on their own thread, one at a time while the
 * render thread waits, and the simulation reaches the render side only through frames. Each step's calls to the
 * render controller and animation listeners are recorded as events, the step's world and interface state is written
 * into a frame, and the render thread plays the frame's events on the real controller once the step is over.
 * <p>
 * What still crosses directly is what the simulation needs an answer to at once: units, items and destructables are
 * made on the render thread on the simulation thread's request and returned. Everything else that reads or writes
 * across the two sides is found with {@link SimThreadGuard}.
 * <p>
 * With {@link SimThreadMode#ON} the same thread makes the steps on its own ({@link SimThread}): the render thread
 * grants the steps its frame time makes due, plays a frame's events once the presentation clock reaches it, and serves
 * the simulation's calls a few times per frame. What the render thread still reads from the simulation directly
 * (peers being built, the type prewarm, the interface's input) it reads while the simulation is held between two
 * steps.
 */
final class MapViewerSimThread {
	private final War3MapViewer viewer;
	private final War3MapRenderController real;
	/** Frames the ring holds with the thread on: the clock keeps a few, and a late step must not stop the writer. */
	private static final int ON_RING_CAPACITY = 32;
	/** The game falls behind by at most about this much game time before it runs slower instead. */
	private static final float MAX_LAG_SECONDS = 1f;
	/** How often the thread does a pause's work for the turn manager, as the render frame does without the thread. */
	private static final long WHILE_PAUSED_NANOS = 16_000_000L;
	/** How long the render thread waits for a step that runs when the game is left. */
	private static final long STOP_WAIT_MILLIS = 3000;

	private final SimThreadRunner thread;
	/** The thread of {@link SimThreadMode#ON}, which makes the steps on its own; null in SERIAL. */
	private final SimThread free;
	private final RenderEventPool pool = new RenderEventPool();
	private final SimFrameRing ring;
	private final FrameEventSink sink;
	private final LocalUiLink localUi;
	private final BufferingRenderController buffering;
	private final RenderEventPlayer player;
	private final IntMap<CUnit> units = new IntMap<>();
	private final IntMap<CDestructable> destructables = new IntMap<>();
	private final IntMap<CUnitAnimationListener> listeners = new IntMap<>();
	private final MapViewerPeers peers;
	/** Units whose simulation listener the peer made (they were not made by the simulation); their tags still need seeding. */
	private final Set<Integer> unseeded = new HashSet<>();
	private CSimulation simulation;
	private SimFrameWriter writer;
	private long nextFrame;
	private PresentationClockImpl clock;
	private boolean presenting;
	private final OrderInbox inbox = new OrderInbox();
	private ThreadedOrderListener orders;
	private CPlayerUnitOrderListener orderTarget;
	/** Render thread: frames are being played (a call served meanwhile must not play them again). */
	private boolean playing;
	/** Simulation thread: when the turn manager last did a pause's work. */
	private long lastWhilePaused;

	MapViewerSimThread(final War3MapViewer viewer, final War3MapRenderController real) {
		this.viewer = viewer;
		this.real = real;
		SimThreadGuard.registerGl(Thread.currentThread());
		if (SimThreadMode.current().isThreaded()) {
			this.free = new SimThread(new SimThread.Stepper() {
				@Override
				public int step(final int pendingSteps) {
					return stepOnThread(pendingSteps);
				}

				@Override
				public void idle() {
					idleOnThread();
				}
			});
			// a call of the simulation finds the render side as far as the steps made so far
			this.free.setBeforeCall(this::catchUp);
			this.thread = this.free;
			this.ring = new SimFrameRing(ON_RING_CAPACITY, this.pool);
		}
		else {
			this.free = null;
			this.thread = new SerialSimThread();
			this.ring = new SimFrameRing(SimFrameRing.DEFAULT_CAPACITY, this.pool);
		}
		this.sink = new FrameEventSink(this.ring, this.pool,
				() -> this.simulation == null ? 0 : this.simulation.getGameTurnTick());
		this.localUi = LocalUiLink.queued(this.sink, new LocalUiLink.RenderCaller() {
			@Override
			public <T> T call(final String why, final java.util.function.Supplier<T> call) {
				if (MapViewerSimThread.this.thread.isSimThread()) {
					SimThreadGuard.crossing("native", why);
				}
				return MapViewerSimThread.this.thread.callOnRender(() -> {
					MapViewerSimThread.this.peers.flush();
					return call.get();
				});
			}
		}, () -> this.simulation == null ? 0 : this.simulation.getGameTurnTick());
		this.peers = new MapViewerPeers(viewer);
		this.buffering = new BufferingRenderController(this.sink,
				new MapViewerSimWorld(viewer, this.sink, this.peers.queue(), this.thread::isSimThread));
		this.player = new RenderEventPlayer(this.real, new RenderEventPlayer.Resolver() {
			@Override
			public CUnit unit(final int handle) {
				MapViewerSimThread.this.peers.ensure(handle);
				final CUnit unit = MapViewerSimThread.this.units.get(handle);
				return unit != null ? unit : MapViewerSimThread.this.simulation.getUnit(handle);
			}

			@Override
			public CItem item(final int handle) {
				MapViewerSimThread.this.peers.ensure(handle);
				final CItem made = MapViewerSimThread.this.peers.item(handle);
				if (made != null) {
					return made;
				}
				final CWidget widget = MapViewerSimThread.this.simulation.getWidget(handle);
				return widget instanceof CItem ? (CItem) widget : null;
			}

			@Override
			public CDestructable destructable(final int handle) {
				MapViewerSimThread.this.peers.ensure(handle);
				final CDestructable destructable = MapViewerSimThread.this.destructables.get(handle);
				return destructable != null ? destructable : MapViewerSimThread.this.simulation.getDestructable(handle);
			}

			@Override
			public CWidget widget(final int handle) {
				final CUnit unit = unit(handle);
				if (unit != null) {
					return unit;
				}
				final CDestructable destructable = destructable(handle);
				return destructable != null ? destructable : MapViewerSimThread.this.simulation.getWidget(handle);
			}

			@Override
			public CProjectile projectile(final int id) {
				return MapViewerSimThread.this.buffering.projectile(id);
			}
		}, null, this.peers);
		this.player.setAnimationPlayer(new BufferedAnimationListener.Player(new BufferedAnimationListener.Player.Lookup() {
			@Override
			public CUnitAnimationListener listener(final int handle) {
				MapViewerSimThread.this.peers.ensure(handle);
				return MapViewerSimThread.this.listeners.get(handle);
			}

			@Override
			public CWidget widget(final int handle) {
				return MapViewerSimThread.this.simulation.getWidget(handle);
			}
		}));
	}

	/**
	 * Render thread (a destructable's peer was made): the events that name its handle can find the object and the real
	 * listener. One the simulation did not make, a map's own, gets its stand-in listener now.
	 */
	void destructablePeerCreated(final CDestructable destructable) {
		final int handle = destructable.getHandleId();
		this.destructables.put(handle, destructable);
		if (!destructable.hasSimulationAnimationListener()) {
			destructable.setSimulationAnimationListener(new BufferedAnimationListener(this.sink, handle));
		}
		final CUnitAnimationListener real = destructable.getPeerAnimationListener();
		if (real != null) {
			this.listeners.put(handle, real);
		}
	}

	/** Natives made while this wrapper is set are run on the render thread, which waits for the step. */
	BiFunction<String, JassFunction, JassFunction> nativeWrapper() {
		return (name, function) -> (arguments, globalScope, triggerScope) -> {
			if (this.thread.isSimThread()) {
				SimThreadGuard.crossing("native", name);
			}
			return this.thread.callOnRender(() -> {
				// the native may use the peers of what the simulation made since the last frame was played
				this.peers.flush();
				return function.call(arguments, globalScope, triggerScope);
			});
		};
	}

	/** How the script natives reach the local interface: commands as events, reads from published state. */
	LocalUiLink localUiLink() {
		return this.localUi;
	}

	/**
	 * What the simulation tells the interface about a player's commands, said on the render thread; the alerts about a
	 * unit are said on the simulation thread, where the unit may be read, and hand the interface their result.
	 */
	CommandErrorListener onRender(final CommandErrorListener listener) {
		return this.thread.onRender(CommandErrorListener.class, listener,
				new HashSet<>(Arrays.asList("unitAttacked", "heroDied")));
	}

	SimulationRenderController controller() {
		return this.buffering;
	}

	/** Render thread, once the simulation exists: the step end writes the frame. */
	void attach(final CSimulation simulation) {
		this.simulation = simulation;
		this.writer = new SimFrameWriter(this.ring, this.viewer.localPlayerIndex);
		this.writer.setUiPart(this::writeUi);
		this.writer.setStepHook(this.viewer.typePrewarm::onStep);
		this.writer.setProjectileIds(this.buffering::projectileId);
		simulation.setStepEndListener(this.writer);
		this.clock = new PresentationClockImpl(this.ring);
		this.viewer.setPresentation(this.clock);
	}

	/** Render thread, once per frame before the world is placed: moves the presentation time on. */
	void advancePresentation(final float seconds, final float timeScale, final boolean paused) {
		this.presenting = true;
		this.localUi.publish();
		this.clock.setTimeScale(timeScale);
		this.clock.setPaused(paused);
		this.clock.advance((long) (seconds * 1_000_000_000.0));
		if (this.free != null) {
			presentFrames();
		}
	}

	/** The number the frames give a missile (the one the events name it by). */
	int projectileId(final CProjectile projectile) {
		return this.buffering.projectileId(projectile);
	}

	private void writeUi(final CSimulation game, final SimFrame frame) {
		final UiFrameSource source = this.viewer.uiFrameSource();
		if (source != null) {
			source.writer().write(game, frame);
		}
	}

	/**
	 * The interface's order listener: commands go to the inbox and the simulation thread hands them to the target
	 * between steps, where the target would have taken them from the interface directly.
	 */
	CPlayerUnitOrderListener orderListener(final CPlayerUnitOrderListener target, final int playerIndex) {
		this.orderTarget = target;
		this.orders = new ThreadedOrderListener(this.inbox, playerIndex);
		return this.orders;
	}

	// The calls below are made by the render thread.

	/** Start of a frame, before anything asks whether the game is paused. */
	void beforeFrame() {
		if (this.free != null) {
			beforeFrameThreaded();
			return;
		}
		this.peers.beginFrame();
		modelWait();
		if ((this.writer != null) && (this.ring.publishedCount() == 0)) {
			// the presentation clock has nothing to show the world from until a frame exists
			this.thread.run(() -> this.writer.onStepEnd(this.simulation));
		}
		drainOrders();
	}

	void update() {
		this.thread.run(this.simulation::update);
		playFrames();
	}

	void turnCompleted(final GameTurnManager manager, final int tick) {
		this.localUi.runInterfaceCalls();
		this.thread.run(() -> {
			// what the step's own scripts sent through the interface's listener goes out with this step's end, as
			// the delaying listener would have had it
			drainOrdersNow();
			manager.turnCompleted(tick);
		});
		playFrames();
	}

	void whilePaused(final GameTurnManager manager) {
		this.thread.run(manager::whilePaused);
		playFrames();
	}

	void drainOrders() {
		if ((this.orders != null) || (this.inbox.pendingCount() > 0)) {
			this.thread.run(this::drainOrdersNow);
		}
		playFrames();
	}

	private void drainOrdersNow() {
		if ((this.orders != null) || ((this.simulation != null) && (this.inbox.pendingCount() > 0))) {
			final int tick = this.simulation.getGameTurnTick();
			if (this.orders != null) {
				this.orders.drain(tick, this.orderTarget, this::applyMessage);
			}
			else {
				this.inbox.drain(tick, this::applyMessage);
			}
		}
	}

	private void applyMessage(final OrderMessage message) {
		if (message.kind == OrderMessage.VISION) {
			if (this.writer != null) {
				this.writer.setVision(message.playerIndex, message.payload[0] != 0);
			}
		}
		else {
			throw new IllegalStateException("No handler for order message kind " + message.kind);
		}
	}

	/**
	 * Render thread: a replay's Replay Vision changed; the simulation thread takes it between steps and the frames
	 * from then on carry that player's fog and sight.
	 */
	void requestVision(final int player, final boolean fogShown) {
		final OrderMessage message = this.inbox.obtain();
		message.kind = OrderMessage.VISION;
		message.playerIndex = player;
		message.payload[0] = (byte) (fogShown ? 1 : 0);
		message.payloadLength = 1;
		this.inbox.submit(message);
	}

	/** Plays what the simulation recorded since the last time and gives the frames back. */
	void playFrames() {
		if ((this.free == null) && (this.sink.hasPending() || ((this.writer != null) && this.writer.hasPendingVision()))) {
			// events of a command or a script outside a step would otherwise wait for the next step
			this.writer.onStepEnd(this.simulation);
		}
		playFrames(this.ring.publishedCount());
	}

	/** Render thread: plays the published frames below the sequence that were not played yet. */
	private void playFrames(final long upTo) {
		if (this.playing) {
			return;
		}
		this.playing = true;
		try {
			playFramesBelow(upTo);
		}
		finally {
			this.playing = false;
		}
	}

	private void playFramesBelow(final long upTo) {
		final long published = Math.min(upTo, this.ring.publishedCount());
		for (long sequence = this.nextFrame; sequence < published; sequence++) {
			final SimFrame frame = this.ring.get(sequence);
			if (frame != null) {
				this.player.playFrame(frame);
				this.viewer.takePresentedFog(frame);
				forgetRemoved(frame);
				final UiFrameSource source = this.viewer.uiFrameSource();
				if (source != null) {
					source.present(frame.ui);
				}
			}
		}
		this.nextFrame = Math.max(this.nextFrame, published);
		if (this.presenting) {
			// the presentation clock still shows frames from before the newest one
			this.clock.releaseConsumed(this.nextFrame);
		}
		else {
			this.ring.release(this.nextFrame);
		}
	}

	private void forgetRemoved(final SimFrame frame) {
		for (int i = 0, n = frame.eventCount(); i < n; i++) {
			final RenderEvent event = frame.event(i);
			if (event.code == RenderCallCodes.UNIT_REMOVE) {
				this.units.remove(event.a);
				this.listeners.remove(event.a);
			}
			else if (event.code == RenderCallCodes.DESTRUCTABLE_REMOVE) {
				this.destructables.remove(event.a);
				this.listeners.remove(event.a);
			}
		}
	}

	/**
	 * Render thread (a unit's peer was made): the peer keeps the real listener. A unit the simulation made has its
	 * stand-in already; one the map made gets it now.
	 */
	void attachAnimationListener(final CUnit unit, final UnitAnimationListenerImpl real) {
		final int handle = unit.getHandleId();
		this.units.put(handle, unit);
		this.listeners.put(handle, real);
		if (!(unit.getUnitAnimationListener() instanceof BufferedAnimationListener)) {
			unit.setUnitAnimationListener(new BufferedAnimationListener(this.sink, handle));
			this.unseeded.add(handle);
		}
	}

	/** The tags the peer put on the real listener are the simulation side's starting point too, for a unit the map made. */
	void seedAnimationTags(final CUnit unit, final UnitAnimationListenerImpl real) {
		if (!this.unseeded.remove(unit.getHandleId())) {
			return;
		}
		final CUnitAnimationListener listener = unit.getUnitAnimationListener();
		if (listener instanceof BufferedAnimationListener) {
			((BufferedAnimationListener) listener).seedSecondaryTags(real.getSecondaryTags());
		}
	}

	// With the thread on (ON mode).

	boolean isThreaded() {
		return this.free != null;
	}

	private void beforeFrameThreaded() {
		this.free.start();
		this.free.serve();
		if ((this.writer != null) && (this.ring.publishedCount() == 0)) {
			// the presentation clock has nothing to show the world from until a frame exists
			this.thread.run(() -> this.writer.onStepEnd(this.simulation));
		}
		if (this.free.tryHold()) {
			try {
				// the peers built ahead of their events read the units the simulation made
				this.peers.beginFrame();
			}
			finally {
				this.free.release();
			}
		}
		modelWait();
	}

	private void modelWait() {
		if (this.clock != null) {
			this.clock.setModelWait(this.peers.waitingForModels());
		}
	}

	/** Render thread, end of a frame: what the simulation asked meanwhile. */
	void afterFrame() {
		if (this.free != null) {
			this.free.serve();
		}
	}

	/** Render thread: the events of the frames the presentation clock has reached, while no step runs. */
	private void presentFrames() {
		final SimFrame before = this.clock.frameBefore();
		final long upTo = before == null ? this.nextFrame : before.sequence + 1;
		if ((upTo > this.nextFrame) && this.free.tryHold()) {
			try {
				playFrames(upTo);
			}
			finally {
				this.free.release();
			}
		}
	}

	/** Render thread, before a call of the simulation is served: everything the steps so far made is played. */
	private void catchUp() {
		if (this.writer != null) {
			playFrames(this.ring.publishedCount());
		}
		this.peers.flush();
	}

	/**
	 * Render thread: the steps the frame's time made due go to the simulation thread; the steps still waiting are kept
	 * to about a second of game time.
	 */
	void grantSteps(final int steps, final float speed) {
		final int maxPending = Math.max(1, (int) Math.ceil((MAX_LAG_SECONDS * Math.max(1f, speed))
				/ WarsmashConstants.SIMULATION_STEP_TIME));
		this.free.grant(steps, maxPending);
	}

	/** Render thread, a game that is not drawn: grants the steps and waits until they are made. */
	void runGrantedSteps(final int steps) {
		this.free.start();
		this.free.grant(steps, Integer.MAX_VALUE);
		this.free.awaitGranted();
		// the events read the units the steps made, which no step may change meanwhile
		this.free.hold();
		try {
			playFrames(this.ring.publishedCount());
		}
		finally {
			this.free.release();
		}
	}

	/**
	 * Render thread, behind the loading screen: the game's first steps are made on the simulation thread, which the
	 * render thread waits for as it serves their calls. The frames they publish are kept for the presentation clock, which
	 * starts at the newest one, and the peers of what they made are built before the screen is shown.
	 *
	 * @return the number of steps made
	 */
	int playOpeningSteps(final int count) {
		this.free.start();
		if ((this.writer != null) && (this.ring.publishedCount() == 0)) {
			// the presentation clock needs the game as it was before the first step
			this.free.run(() -> this.writer.onStepEnd(this.simulation));
		}
		this.presenting = true;
		runGrantedSteps(count);
		this.free.hold();
		try {
			this.peers.flush();
		}
		finally {
			this.free.release();
		}
		final SimFrame newest = this.ring.latest();
		return newest == null ? 0 : newest.tick;
	}

	/** Render thread: drops the steps not yet made (a skip ahead makes its own). */
	void dropGrantedSteps() {
		this.free.dropGranted();
	}

	/** Render thread: runs the action if no step runs now, keeping the next one from starting meanwhile. */
	boolean tryWhileHeld(final Runnable action) {
		if (!this.free.tryHold()) {
			return false;
		}
		try {
			action.run();
		}
		finally {
			this.free.release();
		}
		return true;
	}

	/** Render thread: holds the simulation, waiting for a step that runs; {@link #release} follows. */
	void hold() {
		this.free.hold();
	}

	void release() {
		this.free.release();
	}

	boolean park(final long maxWaitMillis) {
		return this.free.park(maxWaitMillis);
	}

	void unpark() {
		this.free.unpark();
	}

	/** Any thread: a job the simulation thread runs between steps; the caller does not wait. */
	void post(final Runnable job) {
		this.free.post(job);
	}

	boolean isSimThread() {
		return this.thread.isSimThread();
	}

	/** Simulation thread: runs the call on the render thread and waits for it; elsewhere in place. */
	void callOnRender(final Runnable call) {
		this.thread.callOnRender(() -> {
			call.run();
			return null;
		});
	}

	/** Simulation thread: one step, if the game can make one now (see {@link SimThread.Stepper#step}). */
	private int stepOnThread(final int pendingSteps) {
		final War3MapViewer viewer = this.viewer;
		drainOrdersNow();
		publishPending();
		if (viewer.gameEnded || viewer.worldHidden || viewer.isFastForwarding()
				|| viewer.isGamePausedOnSimulation()) {
			return SimThread.BLOCKED;
		}
		if ((this.ring.capacity() - (this.ring.publishedCount() - this.ring.oldestHeld())) < 2) {
			// the render side still holds the frames: the step waits rather than find the ring full
			return SimThread.WAIT;
		}
		final GameTurnManager manager = viewer.gameTurnManager;
		final int latestTurn = manager.getLatestCompletedTurn();
		if (viewer.isGamePaused()) {
			return SimThread.BLOCKED;
		}
		if (latestTurn < this.simulation.getGameTurnTick()) {
			if (pendingSteps > 3) {
				manager.framesSkipped(pendingSteps);
				return SimThread.BLOCKED;
			}
			return SimThread.WAIT;
		}
		this.simulation.update();
		if (this.localUi.hasInterfaceCalls()) {
			// the interface's answer to the step (a script's selection) goes out with the step's end
			callOnRender(this.localUi::runInterfaceCalls);
		}
		drainOrdersNow();
		manager.turnCompleted(this.simulation.getGameTurnTick());
		publishPending();
		runSaveRequests();
		return SimThread.STEPPED;
	}

	/** Simulation thread, no step due: commands, a pause's work for the turn manager, saves. */
	private void idleOnThread() {
		if (this.simulation == null) {
			return;
		}
		drainOrdersNow();
		publishPending();
		final War3MapViewer viewer = this.viewer;
		if (viewer.isGamePaused() && !viewer.isFastForwarding() && !viewer.gameEnded
				&& (viewer.gameTurnManager != null)) {
			final long now = System.nanoTime();
			if ((now - this.lastWhilePaused) >= WHILE_PAUSED_NANOS) {
				this.lastWhilePaused = now;
				viewer.gameTurnManager.whilePaused();
				publishPending();
			}
		}
		if (this.localUi.hasInterfaceCalls()) {
			callOnRender(this.localUi::runInterfaceCalls);
			drainOrdersNow();
			publishPending();
		}
		runSaveRequests();
	}

	/** Simulation thread: a save is made, or a load handed on, by the render thread while the simulation waits. */
	private void runSaveRequests() {
		if ((this.viewer.pendingSave != null) || (this.viewer.pendingLoad != null)) {
			callOnRender(() -> MapViewerSaves.runPendingSaveRequests(this.viewer));
		}
	}

	/** Simulation thread: events made outside a step (a command, a pause's work) go out in a frame of their own. */
	private void publishPending() {
		if ((this.writer != null) && (this.sink.hasPending() || this.writer.hasPendingVision())) {
			this.writer.onStepEnd(this.simulation);
		}
	}


	void shutdown() {
		try {
			stopThread();
		}
		finally {
			SimThreadGuard.simulationStopped();
		}
	}

	private void stopThread() {
		if (this.free != null) {
			final boolean ended = this.free.shutdown(STOP_WAIT_MILLIS);
			Log.info("Sim thread stopped" + (ended ? "" : " (did not end in time)") + ", frames "
					+ this.ring.publishedCount() + ", " + this.peers.summary());
			return;
		}
		this.thread.stop();
		Log.info("SERIAL sim thread stopped, frames " + this.ring.publishedCount() + ", " + this.peers.summary());
	}
}
