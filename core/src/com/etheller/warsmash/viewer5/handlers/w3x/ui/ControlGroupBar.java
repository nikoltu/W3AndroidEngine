package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.input.touch.VirtualModifiers;
import com.etheller.warsmash.input.touch.VirtualModifiers.ShiftState;
import com.etheller.warsmash.parsers.fdf.GameUI;

/**
 * Touch-only row of control group buttons with the Shift button at its right end, drawn just above the console's
 * unit info area so the 1.31 console itself stays untouched. Positions are in FDF units of the 4:3 interface.
 */
final class ControlGroupBar {
	static final int SLOT_COUNT = 10;
	static final int SHIFT_SLOT = SLOT_COUNT;
	private static final int SHIFT_WIDTH_IN_SLOTS = 2;
	private static final float LEFT = 0.305f;
	private static final float BOTTOM = 0.141f;
	private static final float WIDTH = 0.198f;
	private static final float HEIGHT = 0.026f;
	private static final float GAP = 0.002f;
	private static final Color GROUP = new Color(0.12f, 0.28f, 0.78f, 0.85f);
	private static final Color GROUP_EMPTY = new Color(0.12f, 0.28f, 0.78f, 0.3f);
	private static final Color SHIFT_OFF = new Color(0.35f, 0.35f, 0.35f, 0.75f);
	private static final Color SHIFT_ON = new Color(0.85f, 0.6f, 0.1f, 0.9f);

	private final Viewport uiViewport;
	private final Vector2 temp = new Vector2();
	private final GlyphLayout glyphLayout = new GlyphLayout();

	ControlGroupBar(final Viewport uiViewport) {
		this.uiViewport = uiViewport;
	}

	private float slotWidth() {
		return GameUI.convertX(this.uiViewport, (WIDTH - (GAP * SLOT_COUNT)) / (SLOT_COUNT + SHIFT_WIDTH_IN_SLOTS));
	}

	private float widthOf(final int slot) {
		return slot == SHIFT_SLOT ? slotWidth() * SHIFT_WIDTH_IN_SLOTS : slotWidth();
	}

	private float slotX(final int slot) {
		return GameUI.convertX(this.uiViewport, LEFT) + (slot * (slotWidth() + GameUI.convertX(this.uiViewport, GAP)));
	}

	/**
	 * @return the slot under the screen point: 0 to 9 for the groups in key order 1..9, 0, {@link #SHIFT_SLOT} for
	 *         Shift, -1 for none
	 */
	int slotAt(final int screenX, final int screenY) {
		this.temp.set(screenX, screenY);
		this.uiViewport.unproject(this.temp);
		final float bottom = GameUI.convertY(this.uiViewport, BOTTOM);
		if ((this.temp.y < bottom) || (this.temp.y > (bottom + GameUI.convertY(this.uiViewport, HEIGHT)))) {
			return -1;
		}
		for (int slot = 0; slot <= SHIFT_SLOT; slot++) {
			final float x = slotX(slot);
			if ((this.temp.x >= x) && (this.temp.x <= (x + widthOf(slot)))) {
				return slot;
			}
		}
		return -1;
	}

	/** Group number used by the keyboard for a slot: slots 0..8 are keys 1..9, slot 9 is key 0. */
	static int groupOfSlot(final int slot) {
		return (slot + 1) % SLOT_COUNT;
	}

	void render(final SpriteBatch batch, final ShapeRenderer shapeRenderer, final BitmapFont font,
			final int[] groupSizes) {
		final float bottom = GameUI.convertY(this.uiViewport, BOTTOM);
		final float height = GameUI.convertY(this.uiViewport, HEIGHT);
		final ShiftState shift = VirtualModifiers.getShiftState();
		batch.end();
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
		shapeRenderer.begin(ShapeType.Filled);
		for (int slot = 0; slot <= SHIFT_SLOT; slot++) {
			if (slot == SHIFT_SLOT) {
				shapeRenderer.setColor(shift == ShiftState.OFF ? SHIFT_OFF : SHIFT_ON);
			}
			else {
				shapeRenderer.setColor(groupSizes[groupOfSlot(slot)] > 0 ? GROUP : GROUP_EMPTY);
			}
			shapeRenderer.rect(slotX(slot), bottom, widthOf(slot), height);
		}
		shapeRenderer.end();
		if (shift == ShiftState.LOCKED) {
			shapeRenderer.begin(ShapeType.Line);
			shapeRenderer.setColor(Color.WHITE);
			shapeRenderer.rect(slotX(SHIFT_SLOT), bottom, widthOf(SHIFT_SLOT), height);
			shapeRenderer.end();
		}
		batch.begin();
		font.setColor(Color.WHITE);
		for (int slot = 0; slot <= SHIFT_SLOT; slot++) {
			final String label;
			if (slot == SHIFT_SLOT) {
				label = shift == ShiftState.LOCKED ? "SHIFT" : "Shift";
			}
			else {
				final int size = groupSizes[groupOfSlot(slot)];
				label = size > 0 ? groupOfSlot(slot) + ":" + size : Integer.toString(groupOfSlot(slot));
			}
			this.glyphLayout.setText(font, label);
			font.draw(batch, label, slotX(slot) + ((widthOf(slot) - this.glyphLayout.width) / 2),
					bottom + ((height + this.glyphLayout.height) / 2));
		}
	}
}
