package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.jass.OsKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;

/**
 * The way the script natives reach what only the local interface has: the camera, the cinematic filter, the messages,
 * the music. Without a simulation thread ({@link #direct}) a command runs at once on the interface and a read asks it,
 * exactly as before. With one ({@link #queued}) a command goes into the frame being built as an event and is run by the
 * render thread when the frame is played, and a read takes the latest {@link LocalUiSnapshot} the render thread
 * published, so the native never touches a render object.
 * <p>
 * A script that reads right after it changed the camera must see its own change (the original moves the camera in the
 * same call). From such a command until the render thread publishes its next snapshot, reads therefore go to the
 * render thread (which applies the commands still waiting first) instead of the snapshot; every other read is a copy
 * of a small object.
 * <p>
 * Simulation thread: {@link #send}, {@link #sendCamera}, {@link #onRender}, {@link #view}. Render thread:
 * {@link #publish}. A command must capture only values (strings, numbers, copies), and objects it looks up on the
 * render side by key.
 */
public final class LocalUiLink {
	/** Implemented by the viewer when it has a link; absent or null for a game without a simulation thread. */
	public interface Holder {
		LocalUiLink localUiLink();
	}

	/** Runs a call on the render thread and returns its result (the simulation thread waits for it). */
	public interface RenderCaller {
		<T> T call(String why, Supplier<T> call);
	}

	/** The local interface's state a script may read. */
	public interface View {
		float cameraField(CCameraField field);

		float targetX();

		float targetY();

		float targetZ();

		float eyeX();

		float eyeY();

		float eyeZ();

		float boundsX();

		float boundsY();

		float boundsWidth();

		float boundsHeight();

		int cameraType();

		boolean fieldControlledByInput(CCameraField field);

		boolean cineFilterDisplayed();
	}

	/** A command that is still waiting for the render thread to run it, by whichever of the two comes first. */
	private final class Command implements Runnable {
		private final Runnable action;
		private boolean done;

		Command(final Runnable action) {
			this.action = action;
		}

		@Override
		public void run() {
			LocalUiLink.this.waiting.remove(this);
			if (!this.done) {
				this.done = true;
				try {
					this.action.run();
				}
				catch (final RuntimeException exc) {
					// where the script made the call, the same error would have ended that trigger; here only the call fails
					Log.error(exc);
				}
			}
		}
	}

	private volatile WarsmashUI ui;
	private final RenderEventSink sink;
	private final RenderCaller renderCaller;
	private final LocalUiSnapshot.Exchange exchange = new LocalUiSnapshot.Exchange();
	private final ConcurrentLinkedQueue<Command> waiting = new ConcurrentLinkedQueue<>();
	private RenderCommands commands;
	private final LocalUiSnapshot capture = new LocalUiSnapshot();
	private final LocalUiSnapshot latest = new LocalUiSnapshot();
	private final LocalUiSnapshot inputRead = new LocalUiSnapshot();
	private volatile boolean keysWatched;
	private final View live = new LiveView();
	private final View crossing = new CrossingView();
	private final View snapshot = new SnapshotView();
	private volatile long published;
	private volatile long cleanAfter;
	/** Simulation thread writes: camera commands sent so far. */
	private volatile long cameraCommandsSent;
	/** Render thread writes: camera commands run so far. */
	private volatile long cameraCommandsRun;
	private final IntSupplier tick;
	/** Game tick up to which the camera is still moving by a script's command, so a snapshot would lag behind it. */
	private int movingUntilTick = Integer.MIN_VALUE;

	private LocalUiLink(final WarsmashUI ui, final RenderEventSink sink, final RenderCaller renderCaller,
			final IntSupplier tick) {
		this.tick = tick;
		this.ui = ui;
		this.sink = sink;
		this.renderCaller = renderCaller;
	}

	/** For a game whose simulation runs on the render thread. */
	public static LocalUiLink direct(final WarsmashUI ui) {
		return new LocalUiLink(ui, null, null, null);
	}

	/** The interface is bound by the first natives that are registered ({@link #of}); it does not exist before. */
	public static LocalUiLink queued(final RenderEventSink sink, final RenderCaller renderCaller,
			final IntSupplier tick) {
		return new LocalUiLink(null, sink, renderCaller, tick);
	}

	/**
	 * The one queue of commands for the render side that all natives share, so a call that needs a made object finds
	 * every command sent before it, whichever native sent it.
	 */
	public synchronized RenderCommands commands() {
		if (this.commands == null) {
			this.commands = new RenderCommands(this);
		}
		return this.commands;
	}

	/** The link of a viewer that has one, else a direct one. */
	public static LocalUiLink of(final Object viewer, final WarsmashUI ui) {
		if (viewer instanceof Holder) {
			final LocalUiLink link = ((Holder) viewer).localUiLink();
			if (link != null) {
				if (ui != null) {
					link.ui = ui;
				}
				return link;
			}
		}
		return direct(ui);
	}

	/**
	 * Render thread, right after a step: the interface does what the step's scripts asked of it now, so the commands
	 * that come of it reach the game with the step's end as they do when the simulation runs on the render thread.
	 */
	public void runInterfaceCalls() {
		final WarsmashUI target = this.ui;
		if ((target != null) && (this.sink != null)) {
			target.runSimulationCalls();
		}
	}

	/** Any thread: whether the interface has calls of the simulation thread waiting for {@link #runInterfaceCalls}. */
	public boolean hasInterfaceCalls() {
		final WarsmashUI target = this.ui;
		return (target != null) && (this.sink != null) && target.hasSimulationCalls();
	}

	public boolean isQueued() {
		return this.sink != null;
	}

	/** A command for the interface that scripts never see the effect of in a read (messages, music, panels). */
	public void send(final int code, final Runnable command) {
		if (this.sink == null) {
			command.run();
			return;
		}
		emit(code, command);
	}

	/**
	 * A command that changes what {@link #view} reports. Until the next snapshot, reads see the interface itself after
	 * this command.
	 */
	public void sendCamera(final Runnable command) {
		if (this.sink == null) {
			runAtStep(command);
			return;
		}
		this.cleanAfter = this.published + 1;
		final int issuedAt = this.tick.getAsInt();
		// a render frame may publish before it runs the command (the thread plays frames behind the steps), so the
		// snapshot says itself which commands it has seen
		final long serial = ++this.cameraCommandsSent;
		emit(RenderEvent.CAMERA_COMMAND, () -> {
			try {
				runAtStep(issuedAt, command);
			}
			finally {
				this.cameraCommandsRun = serial;
			}
		});
	}

	/** The command is done at the game step the script made it in, wherever the camera has got to by then. */
	private void runAtStep(final int issuedAt, final Runnable command) {
		final WarsmashUI target = this.ui;
		if (target == null) {
			command.run();
			return;
		}
		target.getCameraManager().runAtStep(issuedAt, command);
	}

	private void runAtStep(final Runnable command) {
		final WarsmashUI target = this.ui;
		if (target == null) {
			command.run();
			return;
		}
		target.getCameraManager().runAtSimulationStep(command);
	}

	/**
	 * A camera command that keeps moving the camera for this long (a timed pan, a field that moves over a duration, the
	 * shake): reads go to the render thread meanwhile, as a snapshot would show where the camera was at the frame start.
	 */
	public void sendCamera(final Runnable command, final float movesForSeconds) {
		if (this.sink != null) {
			final int steps = (int) Math.min(Integer.MAX_VALUE / 2, Math.ceil(movesForSeconds / WarsmashConstants.SIMULATION_STEP_TIME) + 2);
			this.movingUntilTick = Math.max(this.movingUntilTick, this.tick.getAsInt() + steps);
		}
		sendCamera(command);
	}

	private void emit(final int code, final Runnable action) {
		final Command command = new Command(action);
		this.waiting.add(command);
		final RenderEvent event = this.sink.begin(code);
		event.o0 = command;
		this.sink.emit(event);
	}

	/** Runs the call on the render thread when there is a simulation thread (the caller waits), else in place. */
	public <T> T onRender(final String why, final Supplier<T> call) {
		if (this.sink == null) {
			return call.get();
		}
		return this.renderCaller.call(why, call);
	}

	/** What a native reads: the interface itself, the render thread asked, or the latest snapshot. */
	public View view() {
		if (this.sink == null) {
			return this.live;
		}
		if ((this.published == 0) || (this.published < this.cleanAfter)
				|| (this.tick.getAsInt() < this.movingUntilTick)) {
			return this.crossing;
		}
		if (!this.exchange.read(this.latest) || (this.latest.cameraCommandsRun < this.cameraCommandsSent)) {
			return this.crossing;
		}
		return this.snapshot;
	}

	/** Render thread, once per frame after the camera moved: the state the next reads of scripts will see. */
	public void publish() {
		if (this.ui == null) {
			return;
		}
		final GameCameraManager camera = this.ui.getCameraManager();
		final LocalUiSnapshot s = this.capture;
		s.renderFrame = this.published + 1;
		s.cameraCommandsRun = this.cameraCommandsRun;
		s.targetX = camera.getNoisyTargetX();
		s.targetY = camera.getNoisyTargetY();
		s.targetZ = camera.getNoisyTargetZ();
		s.eyeX = camera.getEyeX();
		s.eyeY = camera.getEyeY();
		s.eyeZ = camera.getEyeZ();
		s.cameraX = s.targetX;
		s.cameraY = s.targetY;
		s.cameraZ = s.targetZ;
		final int fields = Math.min(CCameraField.VALUES.length, LocalUiSnapshot.CAMERA_FIELDS);
		int controlled = 0;
		for (int i = 0; i < fields; i++) {
			s.cameraFields[i] = camera.getField(CCameraField.VALUES[i]);
			if (camera.isFieldControlledByInput(CCameraField.VALUES[i])) {
				controlled |= 1 << i;
			}
		}
		s.fieldsControlledByInput = controlled;
		s.cameraDistance = s.cameraFields[CCameraField.TARGET_DISTANCE.ordinal()];
		s.cameraAngleOfAttack = s.cameraFields[CCameraField.ANGLE_OF_ATTACK.ordinal()];
		s.cameraRotation = s.cameraFields[CCameraField.ROTATION.ordinal()];
		s.cameraFieldOfView = s.cameraFields[CCameraField.FIELD_OF_VIEW.ordinal()];
		final Rectangle bounds = camera.getCameraBounds();
		s.boundsX = bounds == null ? 0 : bounds.x;
		s.boundsY = bounds == null ? 0 : bounds.y;
		s.boundsWidth = bounds == null ? 0 : bounds.width;
		s.boundsHeight = bounds == null ? 0 : bounds.height;
		s.cameraType = camera.getCameraType();
		s.cineFilterDisplayed = this.ui.getCineFilter().isDisplayed();
		captureInput(s);
		this.exchange.publish(s);
		this.published = s.renderFrame;
	}

	private void captureInput(final LocalUiSnapshot s) {
		final Graphics graphics = Gdx.graphics;
		s.screenWidth = graphics == null ? 0 : graphics.getWidth();
		s.screenHeight = graphics == null ? 0 : graphics.getHeight();
		final Input input = Gdx.input;
		if (input == null) {
			return;
		}
		s.pointerScreenX = input.getX();
		s.pointerScreenY = input.getY();
		int buttons = 0;
		for (int button = 0; button < 3; button++) {
			if (input.isButtonPressed(button)) {
				buttons |= 1 << button;
			}
		}
		s.mouseButtons = buttons;
		s.osMetaKeys = OsKeys.heldMetaKeys(input);
		if (this.keysWatched) {
			for (int key = 0; key < LocalUiSnapshot.KEYS; key++) {
				s.keysDown[key] = input.isKeyPressed(key);
			}
		}
		final CUnit hovered = this.ui.getMouseFocusUnit();
		s.hoveredUnitHandle = hovered == null ? 0 : hovered.getHandleId();
	}

	/** What this machine's keyboard, mouse and window are doing, for the natives that ask. */
	public LocalInputView input() {
		if (this.sink == null) {
			return LiveInputView.INSTANCE;
		}
		if (!this.keysWatched) {
			// the keys are kept up to date from the next frame on; this first answer asks the devices
			this.keysWatched = true;
			return this.crossingInput;
		}
		if ((this.published == 0) || !this.exchange.read(this.inputRead)) {
			return this.crossingInput;
		}
		return this.snapshotInput;
	}

	private final LocalInputView crossingInput = new LocalInputView() {
		private <T> T read(final Supplier<T> read) {
			return LocalUiLink.this.renderCaller.call("input", read);
		}

		@Override
		public boolean key(final int gdxKey) {
			return read(() -> LiveInputView.INSTANCE.key(gdxKey));
		}

		@Override
		public boolean mouseButton(final int gdxButton) {
			return read(() -> LiveInputView.INSTANCE.mouseButton(gdxButton));
		}

		@Override
		public int mouseX() {
			return read(LiveInputView.INSTANCE::mouseX);
		}

		@Override
		public int mouseY() {
			return read(LiveInputView.INSTANCE::mouseY);
		}

		@Override
		public int metaKeys() {
			return read(LiveInputView.INSTANCE::metaKeys);
		}

		@Override
		public int width() {
			return read(LiveInputView.INSTANCE::width);
		}

		@Override
		public int height() {
			return read(LiveInputView.INSTANCE::height);
		}

		@Override
		public int hoveredUnitHandle() {
			return read(() -> {
				final CUnit hovered = LocalUiLink.this.ui == null ? null : LocalUiLink.this.ui.getMouseFocusUnit();
				return hovered == null ? 0 : hovered.getHandleId();
			});
		}
	};

	private final LocalInputView snapshotInput = new LocalInputView() {
		@Override
		public boolean key(final int gdxKey) {
			return (gdxKey >= 0) && (gdxKey < LocalUiSnapshot.KEYS) && LocalUiLink.this.inputRead.keysDown[gdxKey];
		}

		@Override
		public boolean mouseButton(final int gdxButton) {
			return (LocalUiLink.this.inputRead.mouseButtons & (1 << gdxButton)) != 0;
		}

		@Override
		public int mouseX() {
			return (int) LocalUiLink.this.inputRead.pointerScreenX;
		}

		@Override
		public int mouseY() {
			return (int) LocalUiLink.this.inputRead.pointerScreenY;
		}

		@Override
		public int metaKeys() {
			return LocalUiLink.this.inputRead.osMetaKeys;
		}

		@Override
		public int width() {
			return LocalUiLink.this.inputRead.screenWidth;
		}

		@Override
		public int height() {
			return LocalUiLink.this.inputRead.screenHeight;
		}

		@Override
		public int hoveredUnitHandle() {
			return LocalUiLink.this.inputRead.hoveredUnitHandle;
		}
	};

	/** Reads the interface itself; only on the render thread or while it waits for the caller. */
	private final class LiveView implements View {
		private GameCameraManager camera() {
			final GameCameraManager camera = LocalUiLink.this.ui.getCameraManager();
			camera.syncToSimulationStep();
			return camera;
		}

		@Override
		public float cameraField(final CCameraField field) {
			return camera().getField(field);
		}

		@Override
		public float targetX() {
			return camera().getNoisyTargetX();
		}

		@Override
		public float targetY() {
			return camera().getNoisyTargetY();
		}

		@Override
		public float targetZ() {
			return camera().getNoisyTargetZ();
		}

		@Override
		public float eyeX() {
			return camera().getEyeX();
		}

		@Override
		public float eyeY() {
			return camera().getEyeY();
		}

		@Override
		public float eyeZ() {
			return camera().getEyeZ();
		}

		@Override
		public float boundsX() {
			return camera().getCameraBounds().getX();
		}

		@Override
		public float boundsY() {
			return camera().getCameraBounds().getY();
		}

		@Override
		public float boundsWidth() {
			return camera().getCameraBounds().getWidth();
		}

		@Override
		public float boundsHeight() {
			return camera().getCameraBounds().getHeight();
		}

		@Override
		public int cameraType() {
			return camera().getCameraType();
		}

		@Override
		public boolean fieldControlledByInput(final CCameraField field) {
			return camera().isFieldControlledByInput(field);
		}

		@Override
		public boolean cineFilterDisplayed() {
			return LocalUiLink.this.ui.getCineFilter().isDisplayed();
		}
	}

	/** Each read is made by the render thread after it has run the commands that wait for it. */
	private final class CrossingView implements View {
		private <T> T read(final Supplier<T> read) {
			return LocalUiLink.this.renderCaller.call("camera", () -> {
				Command command;
				while ((command = LocalUiLink.this.waiting.peek()) != null) {
					command.run();
				}
				return read.get();
			});
		}

		@Override
		public float cameraField(final CCameraField field) {
			return read(() -> LocalUiLink.this.live.cameraField(field));
		}

		@Override
		public float targetX() {
			return read(LocalUiLink.this.live::targetX);
		}

		@Override
		public float targetY() {
			return read(LocalUiLink.this.live::targetY);
		}

		@Override
		public float targetZ() {
			return read(LocalUiLink.this.live::targetZ);
		}

		@Override
		public float eyeX() {
			return read(LocalUiLink.this.live::eyeX);
		}

		@Override
		public float eyeY() {
			return read(LocalUiLink.this.live::eyeY);
		}

		@Override
		public float eyeZ() {
			return read(LocalUiLink.this.live::eyeZ);
		}

		@Override
		public float boundsX() {
			return read(LocalUiLink.this.live::boundsX);
		}

		@Override
		public float boundsY() {
			return read(LocalUiLink.this.live::boundsY);
		}

		@Override
		public float boundsWidth() {
			return read(LocalUiLink.this.live::boundsWidth);
		}

		@Override
		public float boundsHeight() {
			return read(LocalUiLink.this.live::boundsHeight);
		}

		@Override
		public int cameraType() {
			return read(LocalUiLink.this.live::cameraType);
		}

		@Override
		public boolean fieldControlledByInput(final CCameraField field) {
			return read(() -> LocalUiLink.this.live.fieldControlledByInput(field));
		}

		@Override
		public boolean cineFilterDisplayed() {
			return read(LocalUiLink.this.live::cineFilterDisplayed);
		}
	}

	/** The copy {@link #view} took of the latest published snapshot. */
	private final class SnapshotView implements View {
		@Override
		public float cameraField(final CCameraField field) {
			final int index = field.ordinal();
			return index < LocalUiSnapshot.CAMERA_FIELDS ? LocalUiLink.this.latest.cameraFields[index] : 0;
		}

		@Override
		public float targetX() {
			return LocalUiLink.this.latest.targetX;
		}

		@Override
		public float targetY() {
			return LocalUiLink.this.latest.targetY;
		}

		@Override
		public float targetZ() {
			return LocalUiLink.this.latest.targetZ;
		}

		@Override
		public float eyeX() {
			return LocalUiLink.this.latest.eyeX;
		}

		@Override
		public float eyeY() {
			return LocalUiLink.this.latest.eyeY;
		}

		@Override
		public float eyeZ() {
			return LocalUiLink.this.latest.eyeZ;
		}

		@Override
		public float boundsX() {
			return LocalUiLink.this.latest.boundsX;
		}

		@Override
		public float boundsY() {
			return LocalUiLink.this.latest.boundsY;
		}

		@Override
		public float boundsWidth() {
			return LocalUiLink.this.latest.boundsWidth;
		}

		@Override
		public float boundsHeight() {
			return LocalUiLink.this.latest.boundsHeight;
		}

		@Override
		public int cameraType() {
			return LocalUiLink.this.latest.cameraType;
		}

		@Override
		public boolean fieldControlledByInput(final CCameraField field) {
			return (LocalUiLink.this.latest.fieldsControlledByInput & (1 << field.ordinal())) != 0;
		}

		@Override
		public boolean cineFilterDisplayed() {
			return LocalUiLink.this.latest.cineFilterDisplayed;
		}
	}
}
