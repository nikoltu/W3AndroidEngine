package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * How script natives that draw or lay out things (frames, effects, doodads, the terrain fog) reach the render side.
 * A native that changes something and returns nothing is a {@link #send command}: with a simulation thread it goes into
 * the frame being built as an event and the render thread runs it when the frame is played; without one it runs at
 * once. A native whose result depends on the render side (a created frame, a read value) is a {@link #call}: the
 * render thread first runs the commands still waiting, so the script sees its own changes, then does the work.
 * <p>
 * Simulation thread: {@link #send}, {@link #call}. Render thread: the queued commands. A command captures only values
 * and render objects it finds by key.
 * <p>
 * A command must not throw for any value a script can give: the script's arguments are read and checked where the
 * native is called, and what the command does with them on the render side takes every case. Without the thread a
 * failing command would end the script's trigger, with it the render thread, so the two would play differently.
 */
public final class RenderCommands {
	private final class Command implements Runnable {
		private final Runnable action;
		private boolean done;

		Command(final Runnable action) {
			this.action = action;
		}

		@Override
		public void run() {
			RenderCommands.this.waiting.remove(this);
			if (!this.done) {
				this.done = true;
				this.action.run();
			}
		}
	}

	private final LocalUiLink link;
	private final ConcurrentLinkedQueue<Command> waiting = new ConcurrentLinkedQueue<>();

	public RenderCommands(final LocalUiLink link) {
		this.link = link;
	}

	public boolean isQueued() {
		return this.link.isQueued();
	}

	/** A change for the render side that nothing reads back at once. */
	public void send(final Runnable command) {
		if (!this.link.isQueued()) {
			command.run();
			return;
		}
		final Command queued = new Command(command);
		this.waiting.add(queued);
		this.link.send(RenderEvent.FRAME_COMMAND, queued);
	}

	/** Work whose result the script needs now; the commands sent before it have been run first. */
	public <T> T call(final String why, final Supplier<T> work) {
		if (!this.link.isQueued()) {
			return work.get();
		}
		return this.link.onRender(why, () -> {
			Command command;
			while ((command = this.waiting.peek()) != null) {
				command.run();
			}
			return work.get();
		});
	}
}
