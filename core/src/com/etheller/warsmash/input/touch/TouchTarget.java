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
	 * @return the touch overlay slot under the point: 0 to 9 for control groups, 10 for Shift, -1 for none
	 */
	int touchOverlaySlotAt(int screenX, int screenY);

	void selectControlGroupSlot(int slot);

	void assignControlGroupSlot(int slot, boolean add);

	/** Abandon a press in progress without performing it, for example a selection box. */
	void cancelPointerDrag();
}
