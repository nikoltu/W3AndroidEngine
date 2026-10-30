package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.input.touch.VirtualModifiers;
import com.etheller.warsmash.input.touch.VirtualModifiers.ShiftState;
import com.etheller.warsmash.parsers.fdf.GameUI;

/**
 * Touch-only column of square buttons on the right screen edge above the console, under the right thumb next to the
 * command card: the used control groups from the top down in key order 1..9, 0, then a plus button that fills the next
 * free group, and Shift at the bottom. On screens wider than 4:3 it sits in the side margin of the 4:3 interface, clear
 * of its frames. A group shows its key, the icon of its strongest unit and how many units it holds; Shift looks pressed
 * while it is held.
 */
final class ControlGroupBar {
	static final int SLOT_COUNT = 10;
	static final int SHIFT_SLOT = SLOT_COUNT;
	static final int PLUS_SLOT = SLOT_COUNT + 1;
	// Interface heights (FDF units of the 4:3 interface) the column spans: above the command card, below the
	// resource bar.
	private static final float COLUMN_BOTTOM = 0.175f;
	private static final float COLUMN_TOP = 0.555f;
	private static final float GAP = 0.004f;
	private static final float ICON_INSET = 0.12f;
	// The size upperbuttonbar.fdf gives Quests, Menu and Allies.
	private static final float SHIFT_WIDTH = 0.085f;
	private static final float SHIFT_HEIGHT = 0.022f;
	// Regions of the race's upper menu button texture, as upperbuttonbar.fdf cuts them: left, right, top, bottom.
	private static final float BUTTON_RIGHT = 0.6640625f;
	private static final float[] NORMAL = { 0.0f, 0.171875f };
	private static final float[] PUSHED = { 0.25f, 0.421875f };
	private static final float[] HIGHLIGHT = { 0.75f, 0.921875f };
	private static final Color TEXT = new Color(1.0f, 1.0f, 1.0f, 1.0f);
	private static final Color TEXT_HIGHLIGHT = new Color(1.0f, 0.83f, 0.07f, 1.0f);

	private final Viewport uiViewport;
	private final Vector2 temp = new Vector2();
	private final GlyphLayout glyphLayout = new GlyphLayout();

	private final Texture buttonTexture;
	private final Texture plusIcon;
	private final BitmapFont shiftFont;
	// What each row showed in the last render; empty groups have no row, so taps resolve through this.
	private final int[] rowSlots = new int[SHIFT_SLOT];
	private int rowCount;

	ControlGroupBar(final Viewport uiViewport, final Texture buttonTexture, final Texture plusIcon,
			final BitmapFont shiftFont) {
		this.uiViewport = uiViewport;
		this.buttonTexture = buttonTexture;
		this.plusIcon = plusIcon;
		this.shiftFont = shiftFont;
	}

	private float rowHeight() {
		final float span = GameUI.convertY(this.uiViewport, COLUMN_TOP - COLUMN_BOTTOM);
		return (span - (GameUI.convertY(this.uiViewport, GAP) * SHIFT_SLOT)) / (SHIFT_SLOT + 1);
	}

	private float columnRight() {
		final float interfaceWidth = ((ExtendViewport) this.uiViewport).getMinWorldWidth();
		return (interfaceWidth + this.uiViewport.getWorldWidth()) / 2;
	}

	/** Rows count from the top; Shift always takes the last one. */
	private float rowBottom(final int row) {
		final float top = GameUI.convertY(this.uiViewport, COLUMN_TOP);
		final float gap = GameUI.convertY(this.uiViewport, GAP);
		return top - ((row + 1) * rowHeight()) - (row * gap);
	}

	private float shiftHeight() {
		return GameUI.convertY(this.uiViewport, SHIFT_HEIGHT);
	}

	private float shiftWidth() {
		return GameUI.convertX(this.uiViewport, SHIFT_WIDTH);
	}

	private float shiftBottom() {
		return rowBottom(SHIFT_SLOT) - (shiftHeight() / 2);
	}

	/**
	 * @return the slot under the screen point: 0 to 9 for the groups in key order 1..9, 0, {@link #SHIFT_SLOT} for
	 *         Shift, {@link #PLUS_SLOT} for the plus button, -1 for none
	 */
	int slotAt(final int screenX, final int screenY) {
		this.temp.set(screenX, screenY);
		this.uiViewport.unproject(this.temp);
		final float size = rowHeight();
		final float right = columnRight();
		final float shiftBottom = shiftBottom();
		if ((this.temp.x <= right) && (this.temp.x >= (right - shiftWidth())) && (this.temp.y >= shiftBottom)
				&& (this.temp.y <= (shiftBottom + shiftHeight()))) {
			return SHIFT_SLOT;
		}
		if ((this.temp.x > right) || (this.temp.x < (right - size))) {
			return -1;
		}
		for (int row = 0; row < this.rowCount; row++) {
			final float bottom = rowBottom(row);
			if ((this.temp.y >= bottom) && (this.temp.y <= (bottom + size))) {
				return this.rowSlots[row];
			}
		}
		return -1;
	}

	/** Group number used by the keyboard for a slot: slots 0..8 are keys 1..9, slot 9 is key 0. */
	static int groupOfSlot(final int slot) {
		return (slot + 1) % SLOT_COUNT;
	}

	void render(final SpriteBatch batch, final BitmapFont font, final int[] groupSizes, final Texture[] groupIcons) {
		final float size = rowHeight();
		final float x = columnRight() - size;
		final float inset = size * ICON_INSET;
		this.rowCount = 0;
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			final int group = groupOfSlot(slot);
			if (groupSizes[group] == 0) {
				continue;
			}
			final float bottom = rowBottom(this.rowCount);
			this.rowSlots[this.rowCount++] = slot;
			if (groupIcons[group] != null) {
				batch.draw(groupIcons[group], x, bottom, size, size);
			}
			final String count = Integer.toString(groupSizes[group]);
			this.glyphLayout.setText(font, count);
			drawShadowed(batch, font, count, (x + size) - inset - this.glyphLayout.width,
					bottom + inset + this.glyphLayout.height);
			drawShadowed(batch, font, Integer.toString(group), x + inset, (bottom + size) - inset);
		}
		if (this.rowCount < SLOT_COUNT) {
			final float bottom = rowBottom(this.rowCount);
			this.rowSlots[this.rowCount++] = PLUS_SLOT;
			if (this.plusIcon != null) {
				batch.draw(this.plusIcon, x, bottom, size, size);
			}
		}
		final boolean shiftHeld = VirtualModifiers.getShiftState() == ShiftState.HELD;
		final float shiftBottom = shiftBottom();
		final float shiftWidth = shiftWidth();
		final float shiftHeight = shiftHeight();
		final float shiftX = columnRight() - shiftWidth;
		drawRegion(batch, shiftHeld ? PUSHED : NORMAL, shiftX, shiftBottom, shiftWidth, shiftHeight);
		if (shiftHeld) {
			batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
			drawRegion(batch, HIGHLIGHT, shiftX, shiftBottom, shiftWidth, shiftHeight);
			batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		}
		drawCentered(batch, this.shiftFont, "Shift", shiftHeld ? TEXT_HIGHLIGHT : TEXT, shiftX, shiftBottom, shiftWidth,
				shiftHeight);
		this.shiftFont.setColor(Color.WHITE);
		font.setColor(Color.WHITE);
	}

	private void drawShadowed(final SpriteBatch batch, final BitmapFont font, final String text, final float x,
			final float y) {
		font.setColor(Color.BLACK);
		font.draw(batch, text, x + 1, y - 1);
		font.setColor(TEXT);
		font.draw(batch, text, x, y);
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
