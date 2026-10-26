package com.etheller.warsmash.input.touch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * Gives gestures their mouse meaning on whichever screen owns input: tap is a left click, a long press is a right
 * click (an order) the moment it is recognized, one finger drag is a left drag (selection box), two finger drag is a
 * middle drag (camera) and pinch steps are wheel steps. On an interface button a long press only shows the tooltip,
 * so it never toggles autocast by accident, and a two finger tap cancels an active command.
 * <p>
 * While a command waits for a target, a one finger drag aims instead of drawing a box: the cursor follows the finger
 * (raised above it for a building, so the ghost is not hidden under the finger) and releasing casts. A building ghost
 * is only put down on release; tapping the ghost builds there and tapping elsewhere moves it.
 */
public final class RtsTouchAdapter implements GestureListener {
	private static final String LOG_TAG = "WarsmashTouch";
	private final TouchSettings settings;
	private final boolean debugLog;
	private static final int SHIFT_SLOT = 10;
	private boolean aiming;
	private boolean overlayDrag;
	private boolean itemDrag;
	private boolean aimingBuilding;
	private float aimX;
	private float aimY;
	private boolean ghostPlaced;
	private float ghostX;
	private float ghostY;

	public RtsTouchAdapter(final TouchSettings settings, final boolean debugLog) {
		this.settings = settings;
		this.debugLog = debugLog;
	}

	private void aimAt(final InputProcessor processor, final float x, final float y) {
		this.aimX = x;
		this.aimY = this.aimingBuilding ? Math.max(0, y - this.settings.buildAimOffsetPx) : y;
		moveTo(processor, this.aimX, this.aimY);
	}

	private static InputProcessor processor() {
		return Gdx.input.getInputProcessor();
	}

	private static TouchTarget target() {
		final InputProcessor processor = processor();
		return processor instanceof TouchTarget ? (TouchTarget) processor : null;
	}

	private static void moveTo(final InputProcessor processor, final float x, final float y) {
		VirtualCursor.moveTo(Math.round(x), Math.round(y));
		processor.mouseMoved(Math.round(x), Math.round(y));
	}

	private static void click(final InputProcessor processor, final float x, final float y, final int button) {
		processor.touchDown(Math.round(x), Math.round(y), 0, button);
		processor.touchUp(Math.round(x), Math.round(y), 0, button);
	}

	@Override
	public void pointerAt(final float x, final float y) {
		final InputProcessor processor = processor();
		if (processor != null) {
			moveTo(processor, x, y);
		}
	}

	@Override
	public void tap(final float x, final float y, final boolean pen) {
		final InputProcessor processor = processor();
		final TouchTarget target = target();
		final int slot = (target == null) ? -1 : target.touchOverlaySlotAt(Math.round(x), Math.round(y));
		if (slot == SHIFT_SLOT) {
			return;
		}
		if (slot >= 0) {
			target.selectControlGroupSlot(slot);
			log("select group slot " + slot);
			return;
		}
		if ((processor != null) && (target != null) && target.isPlacingBuilding()
				&& !target.isOverClickableUi(Math.round(x), Math.round(y))) {
			if (this.ghostPlaced
					&& (Math.hypot(x - this.ghostX, y - this.ghostY) <= this.settings.ghostTapRadiusPx)) {
				moveTo(processor, this.ghostX, this.ghostY);
				click(processor, this.ghostX, this.ghostY, Input.Buttons.LEFT);
				this.ghostPlaced = target.isPlacingBuilding();
				log("tap on ghost -> build" + (this.ghostPlaced ? " refused, ghost kept" : ""));
			}
			else {
				this.ghostX = x;
				this.ghostY = y;
				this.ghostPlaced = true;
				moveTo(processor, x, y);
				log("ghost moved to " + (int) x + "," + (int) y);
			}
			return;
		}
		this.ghostPlaced = false;
		if (processor != null) {
			moveTo(processor, x, y);
			click(processor, x, y, Input.Buttons.LEFT);
			log("tap " + (int) x + "," + (int) y);
			if ((target != null) && !pen) {
				target.endTouchHover();
			}
		}
	}

	@Override
	public void secondaryTap(final float x, final float y) {
		final InputProcessor processor = processor();
		if (processor != null) {
			moveTo(processor, x, y);
			click(processor, x, y, Input.Buttons.RIGHT);
			log("pen button tap " + (int) x + "," + (int) y + " -> right click");
		}
	}

	@Override
	public void longPress(final float x, final float y) {
		final InputProcessor processor = processor();
		if (processor == null) {
			return;
		}
		moveTo(processor, x, y);
		final TouchTarget target = target();
		final int slot = (target == null) ? -1 : target.touchOverlaySlotAt(Math.round(x), Math.round(y));
		if ((slot >= 0) && (slot != SHIFT_SLOT)) {
			target.assignControlGroupSlot(slot, VirtualModifiers.isShiftDown());
			log("assign group slot " + slot);
			return;
		}
		if (slot == SHIFT_SLOT) {
			return;
		}
		if ((target != null) && target.isOverClickableUi(Math.round(x), Math.round(y))) {
			log("long press on interface: tooltip only");
			return;
		}
		click(processor, x, y, Input.Buttons.RIGHT);
		log("long press " + (int) x + "," + (int) y + " -> right click");
	}

	@Override
	public void longPressEnd(final float x, final float y) {
		final TouchTarget target = target();
		if (target != null) {
			target.endTouchHover();
		}
	}

	@Override
	public void holdTap(final float heldX, final float heldY) {
		final InputProcessor processor = processor();
		final TouchTarget target = target();
		if ((processor != null) && (target != null)
				&& target.isOverClickableUi(Math.round(heldX), Math.round(heldY))) {
			click(processor, heldX, heldY, Input.Buttons.RIGHT);
			log("hold and tap -> right click on button (autocast, item pick up)");
		}
	}

	@Override
	public void dragStart(final float x, final float y, final boolean pen) {
		final InputProcessor processor = processor();
		final TouchTarget target = target();
		if ((target != null) && (target.touchOverlaySlotAt(Math.round(x), Math.round(y)) >= 0)) {
			this.overlayDrag = true;
			return;
		}
		if ((processor != null) && (target != null) && target.isOverInventorySlot(Math.round(x), Math.round(y))) {
			moveTo(processor, x, y);
			click(processor, x, y, Input.Buttons.RIGHT);
			this.itemDrag = true;
			log("item picked up from slot");
			return;
		}
		if ((processor != null) && (target != null) && target.hasActiveCommand()
				&& !target.isOverClickableUi(Math.round(x), Math.round(y))) {
			this.aiming = true;
			this.aimingBuilding = target.isPlacingBuilding() && !pen;
			aimAt(processor, x, y);
			log("aim start" + (this.aimingBuilding ? " (building)" : ""));
			return;
		}
		if (processor != null) {
			moveTo(processor, x, y);
			processor.touchDown(Math.round(x), Math.round(y), 0, Input.Buttons.LEFT);
			log("drag start " + (int) x + "," + (int) y);
		}
	}

	@Override
	public void drag(final float x, final float y) {
		if (this.overlayDrag) {
			return;
		}
		final InputProcessor processor = processor();
		if ((processor != null) && (this.aiming || this.itemDrag)) {
			if (this.itemDrag) {
				moveTo(processor, x, y);
			}
			else {
				aimAt(processor, x, y);
			}
			return;
		}
		if (processor != null) {
			VirtualCursor.moveTo(Math.round(x), Math.round(y));
			processor.touchDragged(Math.round(x), Math.round(y), 0);
		}
	}

	@Override
	public void dragEnd(final float x, final float y) {
		if (this.overlayDrag) {
			this.overlayDrag = false;
			return;
		}
		final InputProcessor processor = processor();
		if ((processor != null) && this.itemDrag) {
			this.itemDrag = false;
			moveTo(processor, x, y);
			final TouchTarget target = target();
			final boolean onSlot = (target != null) && target.isOverInventorySlot(Math.round(x), Math.round(y));
			// A right click on a slot moves the carried item there; a left click elsewhere drops or gives it.
			click(processor, x, y, onSlot ? Input.Buttons.RIGHT : Input.Buttons.LEFT);
			log("item released " + (onSlot ? "on a slot" : "in the world"));
			return;
		}
		if ((processor != null) && this.aiming) {
			this.aiming = false;
			aimAt(processor, x, y);
			final TouchTarget target = target();
			if ((target != null) && target.isPlacingBuilding()) {
				this.ghostX = this.aimX;
				this.ghostY = this.aimY;
				this.ghostPlaced = true;
				log("ghost placed at " + (int) this.aimX + "," + (int) this.aimY);
			}
			else {
				click(processor, this.aimX, this.aimY, Input.Buttons.LEFT);
				log("aim release -> cast at " + (int) this.aimX + "," + (int) this.aimY);
			}
			return;
		}
		if (processor != null) {
			moveTo(processor, x, y);
			processor.touchUp(Math.round(x), Math.round(y), 0, Input.Buttons.LEFT);
			log("drag end " + (int) x + "," + (int) y);
		}
	}

	@Override
	public void panStart(final float x, final float y) {
		final InputProcessor processor = processor();
		if (processor != null) {
			processor.touchDown(Math.round(x), Math.round(y), 0, Input.Buttons.MIDDLE);
			log("pan start");
		}
	}

	@Override
	public void pan(final float x, final float y) {
		final InputProcessor processor = processor();
		if (processor != null) {
			processor.touchDragged(Math.round(x), Math.round(y), 0);
		}
	}

	@Override
	public void panEnd(final float x, final float y) {
		final InputProcessor processor = processor();
		if (processor != null) {
			processor.touchUp(Math.round(x), Math.round(y), 0, Input.Buttons.MIDDLE);
			log("pan end");
		}
	}

	@Override
	public void zoomStep(final int direction) {
		final InputProcessor processor = processor();
		if (processor != null) {
			processor.scrolled(0, -direction);
			log("zoom " + direction);
		}
	}

	@Override
	public void twoFingerTap(final float x, final float y) {
		final InputProcessor processor = processor();
		final TouchTarget target = target();
		if ((processor != null) && (target != null) && target.hasActiveCommand()) {
			click(processor, x, y, Input.Buttons.RIGHT);
			log("two finger tap -> cancel command");
		}
	}

	@Override
	public void cancel() {
		this.aiming = false;
		this.itemDrag = false;
		this.overlayDrag = false;
		final TouchTarget target = target();
		if (target != null) {
			target.cancelPointerDrag();
		}
		log("cancel");
	}

	private void log(final String message) {
		if (this.debugLog) {
			Gdx.app.log(LOG_TAG, message);
		}
	}
}
