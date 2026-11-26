package com.etheller.warsmash.android;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import com.etheller.warsmash.input.touch.CapturedMouse;

/**
 * Takes the mouse's pointer from the system once a mouse has been seen, so that it moves the game's own cursor
 * instead: the system's pointer pushed against the top or the side of the screen asks for the status and navigation
 * bars, which hides the game under them and stops the camera from scrolling there. Fingers and pens are not affected.
 */
final class AndroidMouseCapture {
	private final View view;
	private boolean wanted;

	AndroidMouseCapture(final View view) {
		this.view = view;
		view.setOnCapturedPointerListener(this::onCapturedPointer);
	}

	/** A mouse moved over the game: capture its pointer. A touchpad keeps the system's pointer, whose motion it reports as absolute. */
	void mouseSeen(final MotionEvent event) {
		final InputDevice device = event.getDevice();
		if ((device == null) || ((device.getSources() & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD)) {
			return;
		}
		this.wanted = true;
		request();
	}

	/** Called when the window gains focus again; the system drops the capture whenever focus is lost. */
	void request() {
		if (this.wanted && !this.view.hasPointerCapture() && this.view.hasWindowFocus()) {
			this.view.requestFocus();
			this.view.requestPointerCapture();
		}
	}

	void captureChanged(final boolean captured) {
		CapturedMouse.setActive(captured);
	}

	private boolean onCapturedPointer(final View captured, final MotionEvent event) {
		if ((event.getSource() & InputDevice.SOURCE_MOUSE_RELATIVE) != InputDevice.SOURCE_MOUSE_RELATIVE) {
			return false;
		}
		CapturedMouse.post(event.getX(), event.getY(), event.getButtonState() & 7,
				event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getAxisValue(MotionEvent.AXIS_VSCROLL));
		return true;
	}
}
