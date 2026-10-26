package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.input.touch.VirtualModifiers;
import com.etheller.warsmash.input.touch.VirtualModifiers.ShiftState;
import com.etheller.warsmash.parsers.fdf.GameUI;

/**
 * Touch-only row of control group buttons led by the Shift button, spanning the console's unit display
 * (portrait and info panel) just above its top edge. A group shows the icon of its strongest unit and how many units
 * it holds; Shift looks pressed while it is held. Positions are in FDF units of the 4:3 interface.
 */
final class ControlGroupBar {
	static final int SLOT_COUNT = 10;
	static final int SHIFT_SLOT = SLOT_COUNT;
	private static final int SHIFT_WIDTH_IN_SLOTS = 2;
	private static final float LEFT = 0.181f;
	private static final float BOTTOM = 0.1253f;
	private static final float WIDTH = 0.329f;
	private static final float HEIGHT = 0.0374f;
	private static final float GAP = 0.0015f;
	private static final float ICON_INSET = 0.12f;
	// Regions of the race's upper menu button texture, as upperbuttonbar.fdf cuts them: left, right, top, bottom.
	private static final float BUTTON_RIGHT = 0.6640625f;
	private static final float[] NORMAL = { 0.0f, 0.171875f };
	private static final float[] PUSHED = { 0.25f, 0.421875f };
	private static final float[] DISABLED = { 0.5f, 0.671875f };
	private static final float[] HIGHLIGHT = { 0.75f, 0.921875f };
	private static final Color TEXT = new Color(1.0f, 1.0f, 1.0f, 1.0f);
	private static final Color TEXT_DISABLED = new Color(0.4f, 0.4f, 0.4f, 1.0f);
	private static final Color TEXT_HIGHLIGHT = new Color(1.0f, 0.83f, 0.07f, 1.0f);

	private final Viewport uiViewport;
	private final Vector2 temp = new Vector2();
	private final GlyphLayout glyphLayout = new GlyphLayout();

	private final Texture buttonTexture;

	ControlGroupBar(final Viewport uiViewport, final Texture buttonTexture) {
		this.uiViewport = uiViewport;
		this.buttonTexture = buttonTexture;
	}

	private float slotWidth() {
		return GameUI.convertX(this.uiViewport, (WIDTH - (GAP * SLOT_COUNT)) / (SLOT_COUNT + SHIFT_WIDTH_IN_SLOTS));
	}

	private float widthOf(final int slot) {
		return slot == SHIFT_SLOT ? slotWidth() * SHIFT_WIDTH_IN_SLOTS : slotWidth();
	}

	private float slotX(final int slot) {
		final float left = GameUI.convertX(this.uiViewport, LEFT);
		if (slot == SHIFT_SLOT) {
			return left;
		}
		final float gap = GameUI.convertX(this.uiViewport, GAP);
		return left + widthOf(SHIFT_SLOT) + gap + (slot * (slotWidth() + gap));
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

	void render(final SpriteBatch batch, final BitmapFont font, final int[] groupSizes, final Texture[] groupIcons) {
		final float bottom = GameUI.convertY(this.uiViewport, BOTTOM);
		final float height = GameUI.convertY(this.uiViewport, HEIGHT);
		final boolean shiftHeld = VirtualModifiers.getShiftState() == ShiftState.HELD;
		for (int slot = 0; slot <= SHIFT_SLOT; slot++) {
			final float x = slotX(slot);
			final float width = widthOf(slot);
			if (slot == SHIFT_SLOT) {
				drawRegion(batch, shiftHeld ? PUSHED : NORMAL, x, bottom, width, height);
				if (shiftHeld) {
					batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
					drawRegion(batch, HIGHLIGHT, x, bottom, width, height);
					batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
				}
				drawCentered(batch, font, "Shift", shiftHeld ? TEXT_HIGHLIGHT : TEXT, x, bottom, width, height);
				continue;
			}
			final int group = groupOfSlot(slot);
			final int size = groupSizes[group];
			if (size == 0) {
				drawRegion(batch, DISABLED, x, bottom, width, height);
				drawCentered(batch, font, Integer.toString(group), TEXT_DISABLED, x, bottom, width, height);
				continue;
			}
			drawRegion(batch, NORMAL, x, bottom, width, height);
			final float inset = Math.min(width, height) * ICON_INSET;
			final float iconSize = Math.min(width, height) - (2 * inset);
			final float iconX = x + ((width - iconSize) / 2);
			final float iconY = bottom + ((height - iconSize) / 2);
			if (groupIcons[group] != null) {
				batch.draw(groupIcons[group], iconX, iconY, iconSize, iconSize);
			}
			final String count = Integer.toString(size);
			this.glyphLayout.setText(font, count);
			final float countX = (iconX + iconSize) - this.glyphLayout.width;
			final float countY = iconY + this.glyphLayout.height + (inset * 0.5f);
			font.setColor(Color.BLACK);
			font.draw(batch, count, countX + 1, countY - 1);
			font.setColor(TEXT);
			font.draw(batch, count, countX, countY);
		}
		font.setColor(Color.WHITE);
	}

	private void drawCentered(final SpriteBatch batch, final BitmapFont font, final String text, final Color color,
			final float x, final float y, final float width, final float height) {
		font.setColor(color);
		this.glyphLayout.setText(font, text);
		font.draw(batch, text, x + ((width - this.glyphLayout.width) / 2), y + ((height + this.glyphLayout.height) / 2));
	}

	private void drawRegion(final SpriteBatch batch, final float[] region, final float x, final float y,
			final float width, final float height) {
		if (this.buttonTexture != null) {
			batch.draw(this.buttonTexture, x, y, width, height, 0, region[1], BUTTON_RIGHT, region[0]);
		}
	}
}
