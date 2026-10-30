package com.etheller.warsmash.input.touch;

/**
 * What the touch adapter needs to know about a screen beyond plain mouse input, so that gestures which mean different
 * things in different places do the right thing. Screens that do not implement it get plain mouse synthesis.
 */
public interface TouchTarget {
	/** Whether a button or other clickable interface frame is under the point, in screen pixels. */
	boolean isOverClickableUi(int screenX, int screenY);

	boolean isOverInventorySlot(int screenX, int screenY);

	boolean hasActiveCommand();

	/** Whether the active command shows a building ghost to place. */
	boolean isPlacingBuilding();

	/**
	 * @return the touch overlay slot under the point: 0 to 9 for control groups, 10 for Shift, 11 for the plus button, -1 for none
	 */
	int touchOverlaySlotAt(int screenX, int screenY);

	void selectControlGroupSlot(int slot);

	void assignControlGroupSlot(int slot, boolean add);

	/** Abandon a press in progress without performing it, for example a selection box. */
	void cancelPointerDrag();

	/**
	 * The finger has left the screen: a pointer no longer rests over the interface, so hover state and its tooltip
	 * end. A tooltip is shown only while a finger is held on a button.
	 */
	void endTouchHover();

	/**
	 * A one finger long press skips a cinematic on a touch screen, as Escape does on desktop.
	 *
	 * @return true if cinematic mode is on and the skip was sent
	 */
	boolean skipCinematic();
}
