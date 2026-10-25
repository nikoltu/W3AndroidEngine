package com.etheller.warsmash.input.touch;

/**
 * Gestures recognized from raw pointers, with no meaning attached yet; an adapter decides what each one does on the
 * current screen. Coordinates are screen pixels, top left origin.
 */
public interface GestureListener {
	/** The finger or pen moved while still undecided, so hover effects can follow it. */
	void pointerAt(float x, float y);

	void tap(float x, float y, boolean pen);

	/** A pen tap with the pen button held, which acts like a right mouse click. */
	void secondaryTap(float x, float y);

	/** Fires as soon as the press has been held long enough, not on release. */
	void longPress(float x, float y);

	void longPressEnd(float x, float y);

	/** A second finger tapped while the first one holds a long press; reported at the held finger. */
	void holdTap(float heldX, float heldY);

	void dragStart(float x, float y, boolean pen);

	void drag(float x, float y);

	void dragEnd(float x, float y);

	void panStart(float x, float y);

	void pan(float x, float y);

	void panEnd(float x, float y);

	/** One zoom step: positive when the fingers spread apart, negative when they pinch together. */
	void zoomStep(int direction);

	void twoFingerTap(float x, float y);

	/** The gesture in progress was aborted, for example by the system or a palm; nothing should be carried out. */
	void cancel();
}
