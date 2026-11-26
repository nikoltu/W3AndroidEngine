package com.etheller.warsmash.input.touch;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * A mouse whose pointer the platform has captured: it reports only how far it moved, so the pointer is kept here, at
 * the screen edges like a desktop cursor, and never reaches the system's edge areas that pull in its own bars. Samples
 * arrive on the platform's thread and are replayed on the render thread, where they drive whichever screen owns libGDX
 * input as the mouse would.
 */
public final class CapturedMouse {
	private static final int BUTTON_PRIMARY = 1;
	private static final int BUTTON_SECONDARY = 2;
	private static final int BUTTON_TERTIARY = 4;

	private static final class Sample {
		final float dx;
		final float dy;
		final int buttons;
		final float scrollX;
		final float scrollY;

		Sample(final float dx, final float dy, final int buttons, final float scrollX, final float scrollY) {
			this.dx = dx;
			this.dy = dy;
			this.buttons = buttons;
			this.scrollX = scrollX;
			this.scrollY = scrollY;
		}
	}

	private static final Queue<Sample> samples = new ConcurrentLinkedQueue<>();
	private static volatile boolean active;
	private static volatile boolean activeChanged;
	private static float x;
	private static float y;
	private static int buttons;

	private CapturedMouse() {
	}

	/** The platform started or stopped delivering the mouse as relative motion. */
	public static void setActive(final boolean captured) {
		if (active != captured) {
			active = captured;
			activeChanged = true;
			VirtualCursor.setMouseCaptured(captured);
		}
	}

	public static boolean isActive() {
		return active;
	}

	/**
	 * @param dx      relative motion in screen pixels
	 * @param buttons the buttons held, as the platform's primary (1), secondary (2) and tertiary (4) bits
	 * @param scrollX wheel steps, positive to the right
	 * @param scrollY wheel steps, positive upwards
	 */
	public static void post(final float dx, final float dy, final int buttons, final float scrollX,
			final float scrollY) {
		samples.add(new Sample(dx, dy, buttons, scrollX, scrollY));
	}

	/** Replays what arrived since the last frame; render thread only. */
	public static void drain() {
		if (activeChanged) {
			activeChanged = false;
			if (active) {
				x = VirtualCursor.isMouseOver() ? VirtualCursor.getMouseX() : (Gdx.graphics.getWidth() / 2f);
				y = VirtualCursor.isMouseOver() ? VirtualCursor.getMouseY() : (Gdx.graphics.getHeight() / 2f);
			}
			else {
				samples.clear();
				releaseAll();
				return;
			}
		}
		Sample sample;
		while ((sample = samples.poll()) != null) {
			replay(sample);
		}
	}

	private static void replay(final Sample sample) {
		final InputProcessor processor = Gdx.input.getInputProcessor();
		VirtualCursor.setFingerInput(false);
		final int maxX = Math.max(0, Gdx.graphics.getWidth() - 1);
		final int maxY = Math.max(0, Gdx.graphics.getHeight() - 1);
		final boolean moved = (sample.dx != 0) || (sample.dy != 0);
		x = Math.max(0, Math.min(maxX, x + sample.dx));
		y = Math.max(0, Math.min(maxY, y + sample.dy));
		final int screenX = Math.round(x);
		final int screenY = Math.round(y);
		VirtualCursor.moveTo(screenX, screenY);
		VirtualCursor.mouseAt(screenX, screenY);
		if (processor == null) {
			buttons = sample.buttons;
			return;
		}
		if (moved) {
			if (buttons != 0) {
				processor.touchDragged(screenX, screenY, 0);
			}
			else {
				processor.mouseMoved(screenX, screenY);
			}
		}
		final int changed = buttons ^ sample.buttons;
		for (int bit = 1; bit <= BUTTON_TERTIARY; bit <<= 1) {
			if ((changed & bit) != 0) {
				if ((sample.buttons & bit) != 0) {
					processor.touchDown(screenX, screenY, 0, libGdxButton(bit));
				}
				else {
					processor.touchUp(screenX, screenY, 0, libGdxButton(bit));
				}
			}
		}
		buttons = sample.buttons;
		if ((sample.scrollX != 0) || (sample.scrollY != 0)) {
			processor.scrolled(-Math.signum(sample.scrollX), -Math.signum(sample.scrollY));
		}
	}

	private static void releaseAll() {
		final InputProcessor processor = Gdx.input.getInputProcessor();
		if ((processor != null) && (buttons != 0)) {
			final int screenX = Math.round(x);
			final int screenY = Math.round(y);
			for (int bit = 1; bit <= BUTTON_TERTIARY; bit <<= 1) {
				if ((buttons & bit) != 0) {
					processor.touchUp(screenX, screenY, 0, libGdxButton(bit));
				}
			}
		}
		buttons = 0;
	}

	private static int libGdxButton(final int bit) {
		switch (bit) {
		case BUTTON_SECONDARY:
			return Input.Buttons.RIGHT;
		case BUTTON_TERTIARY:
			return Input.Buttons.MIDDLE;
		case BUTTON_PRIMARY:
		default:
			return Input.Buttons.LEFT;
		}
	}
}
