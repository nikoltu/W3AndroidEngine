package com.etheller.warsmash.desktop.launcher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import com.etheller.warsmash.launcher.GlueText;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.SmallCaps;

/**
 * An immediate mode toolkit drawing the launcher's widgets with the skin's art in board coordinates (design pixels,
 * y down). Widgets are drawn and asked for their input in one call each frame.
 */
final class Gui {
	static final int TONE_NORMAL = 0;
	static final int TONE_DOWN = 1;
	static final int TONE_CONFIRM = 2;
	static final int TONE_DANGER = 3;
	static final int TONE_OFF = 4;

	static final int HOVER = 1;
	static final int HELD = 2;
	static final int CLICK = 4;
	static final int RIGHT = 8;
	static final int DOUBLE = 16;

	private static final float TOOLTIP_SECONDS = 0.5f;

	final SpriteBatch batch = new SpriteBatch();
	private final Texture white;
	private final TextureRegion whiteRegion;
	private final GlyphLayout layout = new GlyphLayout();
	private final Matrix4 projection = new Matrix4();
	private final Color color = new Color();
	/** Whether text is drawn in the bold face; see {@link #bold}. */
	private boolean bold;
	private final float[] vertices = new float[20];

	LauncherArt art;
	LauncherBoard b;
	SkinArt.Palette palette;
	int screenW;
	int screenH;

	float mouseX = -1;
	float mouseY = -1;
	boolean justPressed;
	boolean justReleased;
	boolean rightReleased;
	boolean mouseDown;
	int wheel;
	private Object pressedId;
	private Object hoverId;
	private Object lastHoverId;
	private float hoverTime;
	private long lastClickTime;
	private Object lastClickId;

	/** Widgets drawn on a lower layer than the top one take no input (a dialog or an open menu covers them). */
	int layer;
	int topLayer;
	private final Deque<float[]> clips = new ArrayDeque<>();
	private String tooltip;

	/** The menu that is open: its items and where its button is. */
	private Object menuId;
	private String[] menuItems;
	private int[] menuColours;
	private int menuSelected;
	private float[] menuAt;
	private float menuText;
	private IntConsumer menuChosen;
	private int menuScroll;

	/** The text field that takes the keys. */
	Object focus;
	final StringBuilder typed = new StringBuilder();
	final List<Integer> keys = new ArrayList<>();
	private final Map<Object, Float> scrolls = new HashMap<>();
	private Object dragged;
	/** Where the caret of each text field is, and the field whose whole text is selected. */
	private final Map<Object, Integer> carets = new HashMap<>();
	private Object selectedAll;

	/** Text field edits in {@link #typed}, besides the characters typed: control characters as on a terminal. */
	static final char EDIT_BACKSPACE = '\b';
	static final char EDIT_DELETE = 127;
	static final char EDIT_SELECT_ALL = 1;
	static final char EDIT_COPY = 3;
	static final char EDIT_CUT = 24;
	static final char EDIT_LEFT = 17;
	static final char EDIT_RIGHT = 18;
	static final char EDIT_HOME = 19;
	static final char EDIT_END = 20;

	/** The widget the keyboard is on (Tab and the arrows move it, Enter and Space press it). */
	private Object keyFocus;
	private boolean keyFocusShown;
	private boolean keyPress;
	private int navigation;
	private boolean navigationBack;
	private final List<float[]> focusPlaces = new ArrayList<>();
	private final List<Object> focusIds = new ArrayList<>();
	private float[] focusRing;
	private int menuCursor = -1;
	private Object keyStepped;
	private final Deque<Object> listIds = new ArrayDeque<>();
	private Object listId;
	private boolean menuContext;
	private boolean[] menuEnabled;

	Gui() {
		final Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixmap.setColor(Color.WHITE);
		pixmap.fill();
		this.white = new Texture(pixmap);
		pixmap.dispose();
		this.whiteRegion = new TextureRegion(this.white);
	}

	void begin(final LauncherArt art, final LauncherBoard board, final int screenW, final int screenH) {
		this.art = art;
		this.b = board;
		this.palette = SkinArt.Palette.of(art != null && art.reforged);
		this.screenW = screenW;
		this.screenH = screenH;
		this.projection.setToOrtho(0, board.width, board.height, 0, -1, 1);
		this.batch.setProjectionMatrix(this.projection);
		this.batch.enableBlending();
		this.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		this.batch.begin();
		this.layer = 0;
		this.hoverId = null;
		this.tooltip = null;
		this.topLayer = this.menuId != null ? 99 : this.topLayer;
		this.focusPlaces.clear();
		this.focusIds.clear();
		this.focusRing = null;
	}

	void end() {
		if (this.focusRing != null) {
			drawFocusRing(this.focusRing);
		}
		drawMenu();
		moveKeyFocus();
		if ((this.hoverId != null) && Objects.equals(this.hoverId, this.lastHoverId)) {
			this.hoverTime += Gdx.graphics.getDeltaTime();
		}
		else {
			this.hoverTime = 0;
		}
		this.lastHoverId = this.hoverId;
		if ((this.tooltip != null) && (this.hoverTime >= TOOLTIP_SECONDS)) {
			drawTooltip(this.tooltip);
		}
		this.batch.end();
		if (this.justReleased && (this.dragged != null)) {
			this.dragged = null;
		}
		if (this.justReleased) {
			this.pressedId = null;
		}
		this.justPressed = false;
		this.justReleased = false;
		this.rightReleased = false;
		this.wheel = 0;
		this.typed.setLength(0);
		this.keys.clear();
		this.keyPress = false;
		this.navigation = 0;
		this.keyStepped = null;
	}

	// ---------------------------------------------------------------- keyboard focus

	/** Tab (back with Shift) or an arrow: the keyboard moves to the next widget, shown with a ring. */
	void navigate(final int keycode, final boolean back) {
		if ((this.menuId != null) && ((keycode == Input.Keys.UP) || (keycode == Input.Keys.DOWN))) {
			moveMenuCursor(keycode == Input.Keys.UP ? -1 : 1);
			return;
		}
		this.navigation = keycode;
		this.navigationBack = back;
	}

	/** The keyboard is on a widget that is shown, so Enter and Space press it rather than the page's main action. */
	boolean keyFocusShown() {
		return (this.keyFocusShown && (this.keyFocus != null)) || (this.menuId != null);
	}

	/** Enter or Space on the widget the keyboard is on, or on the open menu's item. */
	void keyPress() {
		this.keyPress = true;
	}

	/** The mouse moved: the ring goes until the keyboard is used again. */
	void hideKeyFocus() {
		this.keyFocusShown = false;
	}

	/** An arrow the focused widget uses itself, as a slider does; it does not move the keyboard. */
	int takeArrow(final Object id) {
		if ((this.navigation == 0) || !this.keyFocusShown || !Objects.equals(this.keyFocus, id)) {
			return 0;
		}
		if (this.navigation == Input.Keys.LEFT) {
			this.navigation = 0;
			return -1;
		}
		if (this.navigation == Input.Keys.RIGHT) {
			this.navigation = 0;
			return 1;
		}
		return 0;
	}

	/** The keyboard is on this widget, with its ring shown. */
	boolean keyOn(final Object id) {
		return this.keyFocusShown && Objects.equals(this.keyFocus, id);
	}

	private void moveKeyFocus() {
		final int key = this.navigation;
		if ((key == 0) || (this.menuId != null)) {
			return;
		}
		final List<Object> ids = this.focusIds;
		if (ids.isEmpty()) {
			return;
		}
		final int at = ids.indexOf(this.keyFocus);
		if (!this.keyFocusShown || (at < 0)) {
			if (at < 0) {
				this.keyFocus = ids.get(this.navigationBack ? ids.size() - 1 : 0);
			}
		}
		else if (key == Input.Keys.TAB) {
			this.keyFocus = ids.get(Math.floorMod(at + (this.navigationBack ? -1 : 1), ids.size()));
		}
		else {
			final float[] from = this.focusPlaces.get(at);
			final float fx = from[0] + (from[2] / 2);
			final float fy = from[1] + (from[3] / 2);
			float best = Float.MAX_VALUE;
			Object found = null;
			for (int i = 0; i < ids.size(); i++) {
				if (i == at) {
					continue;
				}
				final float[] to = this.focusPlaces.get(i);
				final float dx = (to[0] + (to[2] / 2)) - fx;
				final float dy = (to[1] + (to[3] / 2)) - fy;
				final float along;
				final float across;
				switch (key) {
				case Input.Keys.UP:
					along = -dy;
					across = dx;
					break;
				case Input.Keys.DOWN:
					along = dy;
					across = dx;
					break;
				case Input.Keys.LEFT:
					along = -dx;
					across = dy;
					break;
				default:
					along = dx;
					across = dy;
					break;
				}
				if (along <= 1) {
					continue;
				}
				final float score = along + (2.5f * Math.abs(across));
				if (score < best) {
					best = score;
					found = ids.get(i);
				}
			}
			if (found != null) {
				this.keyFocus = found;
			}
		}
		this.keyFocusShown = true;
	}

	private void drawFocusRing(final float[] place) {
		final float line = Math.max(3, 4 / Math.max(0.1f, this.b.scale));
		outline(place[0] - line, place[1] - line, place[2] + (2 * line), place[3] + (2 * line), line,
				this.palette.gold | 0xFF000000);
	}

	void dispose() {
		this.batch.dispose();
		this.white.dispose();
	}

	// ---------------------------------------------------------------- input

	void mouseMoved(final int screenX, final int screenY) {
		this.mouseX = screenX / this.b.scale;
		this.mouseY = screenY / this.b.scale;
	}

	void mousePressed(final int screenX, final int screenY, final int button) {
		mouseMoved(screenX, screenY);
		if (button == Input.Buttons.LEFT) {
			this.justPressed = true;
			this.mouseDown = true;
		}
	}

	void mouseReleased(final int screenX, final int screenY, final int button) {
		mouseMoved(screenX, screenY);
		if (button == Input.Buttons.LEFT) {
			this.justReleased = true;
			this.mouseDown = false;
		}
		else if (button == Input.Buttons.RIGHT) {
			this.rightReleased = true;
		}
	}

	/** Widget input: HOVER, HELD, CLICK, RIGHT and DOUBLE as flags. */
	int hit(final Object id, final float x, final float y, final float w, final float h) {
		if (this.layer < this.topLayer) {
			return 0;
		}
		final boolean keyed = focusable(id, x, y, w, h);
		if (keyed && this.keyPress && (this.menuId == null)) {
			this.keyPress = false;
			return HOVER | CLICK;
		}
		if (!inside(this.mouseX, this.mouseY, x, y, w, h) || !inClip()) {
			return keyed ? HOVER : 0;
		}
		this.hoverId = id;
		int state = HOVER;
		if (this.justPressed) {
			this.pressedId = id;
		}
		if (this.mouseDown && Objects.equals(this.pressedId, id)) {
			state |= HELD;
		}
		if (this.justReleased && Objects.equals(this.pressedId, id)) {
			state |= CLICK;
			final long now = System.currentTimeMillis();
			if (Objects.equals(this.lastClickId, id) && ((now - this.lastClickTime) < 400)) {
				state |= DOUBLE;
				this.lastClickId = null;
			}
			else {
				this.lastClickId = id;
				this.lastClickTime = now;
			}
		}
		if (this.rightReleased) {
			state |= RIGHT;
		}
		return state;
	}

	/**
	 * Notes a widget the keyboard can reach, in drawing order; true when the keyboard is on it. A row scrolled out of
	 * its list is reachable too, and the list scrolls to it when the keyboard gets there.
	 */
	private boolean focusable(final Object id, final float x, final float y, final float w, final float h) {
		final float[] clip = this.clips.peek();
		this.focusIds.add(id);
		this.focusPlaces.add(new float[] { x, y, w, h });
		if (!this.keyFocusShown || !Objects.equals(id, this.keyFocus)) {
			return false;
		}
		if ((clip != null) && (this.listId != null) && ((y < clip[1]) || ((y + h) > (clip[1] + clip[3])))) {
			// The row is out of its list's view: scroll so that it shows from the next frame.
			final float offset = this.scrolls.getOrDefault(this.listId, 0f);
			final float rowTop = (y - clip[1]) + offset;
			this.scrolls.put(this.listId, y < clip[1] ? rowTop : (rowTop + h) - clip[3]);
		}
		this.focusRing = new float[] { x, y, w, h };
		return true;
	}

	/** The mouse is over this place and the place takes input. */
	boolean over(final float x, final float y, final float w, final float h) {
		return (this.layer >= this.topLayer) && inside(this.mouseX, this.mouseY, x, y, w, h) && inClip();
	}

	private static boolean inside(final float px, final float py, final float x, final float y, final float w,
			final float h) {
		return (px >= x) && (py >= y) && (px < (x + w)) && (py < (y + h));
	}

	private boolean inClip() {
		final float[] clip = this.clips.peek();
		return (clip == null) || inside(this.mouseX, this.mouseY, clip[0], clip[1], clip[2], clip[3]);
	}

	/**
	 * The tooltip of the place the mouse is over. A place without a control has no hover of its own, so the tip names
	 * it, which is also what restarts the wait when the mouse moves to another such place.
	 */
	void tip(final String text) {
		this.tooltip = text;
		if ((text != null) && (this.hoverId == null)) {
			this.hoverId = text;
		}
	}

	// ---------------------------------------------------------------- clipping

	void pushClip(final float x, final float y, final float w, final float h) {
		final float[] outer = this.clips.peek();
		float x0 = x;
		float y0 = y;
		float x1 = x + w;
		float y1 = y + h;
		if (outer != null) {
			x0 = Math.max(x0, outer[0]);
			y0 = Math.max(y0, outer[1]);
			x1 = Math.min(x1, outer[0] + outer[2]);
			y1 = Math.min(y1, outer[1] + outer[3]);
		}
		final float[] clip = { x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0) };
		this.clips.push(clip);
		scissor(clip);
	}

	void popClip() {
		this.clips.pop();
		final float[] clip = this.clips.peek();
		if (clip == null) {
			this.batch.flush();
			Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		}
		else {
			scissor(clip);
		}
	}

	private void scissor(final float[] clip) {
		this.batch.flush();
		final float s = this.b.scale;
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
		final int x = Math.round(clip[0] * s);
		final int top = Math.round(clip[1] * s);
		final int w = Math.round(clip[2] * s);
		final int h = Math.round(clip[3] * s);
		Gdx.gl.glScissor(x, this.screenH - top - h, Math.max(0, w), Math.max(0, h));
	}

	// ---------------------------------------------------------------- drawing

	void fill(final float x, final float y, final float w, final float h, final int argb) {
		setColor(argb, 1);
		this.batch.draw(this.whiteRegion, x, y, w, h);
		this.batch.setColor(Color.WHITE);
	}

	/** A gradient from one colour to another, down or to the right. */
	void gradient(final float x, final float y, final float w, final float h, final int from, final int to,
			final boolean down) {
		final float c0 = packed(from);
		final float c1 = packed(to);
		final float[] v = this.vertices;
		final float u0 = this.whiteRegion.getU();
		final float v0 = this.whiteRegion.getV();
		put(v, 0, x, y, down ? c0 : c0, u0, v0);
		put(v, 5, x, y + h, down ? c1 : c0, u0, v0);
		put(v, 10, x + w, y + h, down ? c1 : c1, u0, v0);
		put(v, 15, x + w, y, down ? c0 : c1, u0, v0);
		this.batch.draw(this.white, v, 0, 20);
	}

	private static void put(final float[] v, final int at, final float x, final float y, final float colour,
			final float u, final float vv) {
		v[at] = x;
		v[at + 1] = y;
		v[at + 2] = colour;
		v[at + 3] = u;
		v[at + 4] = vv;
	}

	/** A colour without its hue, darkened and half seen, for what cannot be chosen yet. */
	static int muted(final int argb) {
		final int grey = Math.round(((((argb >> 16) & 0xFF) * 0.299f) + (((argb >> 8) & 0xFF) * 0.587f)
				+ ((argb & 0xFF) * 0.114f)) * 0.8f);
		return ((((argb >>> 24) & 0xFF) / 2) << 24) | (grey << 16) | (grey << 8) | grey;
	}

	private float packed(final int argb) {
		this.color.set(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
				((argb >>> 24) & 0xFF) / 255f);
		return this.color.toFloatBits();
	}

	private void setColor(final int argb, final float alpha) {
		this.batch.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
				(((argb >>> 24) & 0xFF) / 255f) * alpha);
	}

	void image(final TextureRegion region, final float x, final float y, final float w, final float h) {
		image(region, x, y, w, h, 1);
	}

	void image(final TextureRegion region, final float x, final float y, final float w, final float h,
			final float alpha) {
		if (region == null) {
			return;
		}
		this.batch.setColor(1, 1, 1, alpha);
		this.batch.draw(region, x, y, w, h);
		this.batch.setColor(Color.WHITE);
	}

	void image(final String name, final float x, final float y, final float w, final float h) {
		image(this.art.image(name), x, y, w, h, 1);
	}

	void image(final String name, final float x, final float y, final float w, final float h, final float alpha) {
		image(this.art.image(name), x, y, w, h, alpha);
	}

	/** A picture filling the place, cropped around a horizontal focus (0 left, 1 right). */
	void cover(final TextureRegion region, final float x, final float y, final float w, final float h,
			final float focus) {
		if (region == null) {
			return;
		}
		final float imageW = region.getRegionWidth();
		final float imageH = region.getRegionHeight();
		final float scale = Math.max(w / imageW, h / imageH);
		final float shownW = w / scale;
		final float shownH = h / scale;
		final float left = (imageW - shownW) * focus;
		final float top = (imageH - shownH) / 2;
		// The launcher's regions are flipped, so their own x and y are of the far corner.
		final float originX = Math.min(region.getU(), region.getU2()) * region.getTexture().getWidth();
		final float originY = Math.min(region.getV(), region.getV2()) * region.getTexture().getHeight();
		final TextureRegion part = new TextureRegion(region.getTexture(), Math.round(originX + left),
				Math.round(originY + top), Math.round(shownW), Math.round(shownH));
		part.flip(false, true);
		this.batch.draw(part, x, y, w, h);
	}

	/** A picture as large as fits, centred. */
	void contain(final TextureRegion region, final float x, final float y, final float w, final float h,
			final float alpha) {
		if (region == null) {
			return;
		}
		final float scale = Math.min(w / region.getRegionWidth(), h / region.getRegionHeight());
		final float shownW = region.getRegionWidth() * scale;
		final float shownH = region.getRegionHeight() * scale;
		image(region, x + ((w - shownW) / 2), y + ((h - shownH) / 2), shownW, shownH, alpha);
	}

	/** A picture repeated in tiles of a size. */
	void tiled(final TextureRegion region, final float x, final float y, final float w, final float h,
			final float tileW, final float tileH) {
		if (region == null) {
			return;
		}
		final Texture texture = region.getTexture();
		texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
		this.batch.draw(texture, x, y, w, h, 0, 0, w / tileW, h / tileH);
	}

	/** A frame of nine parts with corners of a size; the middle is stretched unless the frame has none. */
	void nine(final LauncherArt.Frame frame, final float x, final float y, final float w, final float h,
			final float border) {
		nine(frame, x, y, w, h, border, 1);
	}

	void nine(final LauncherArt.Frame frame, final float x, final float y, final float w, final float h,
			final float border, final float alpha) {
		if (frame == null) {
			return;
		}
		final float e = Math.min(border, Math.min(w, h) / 2);
		final float[] xs = { x, x + e, (x + w) - e, x + w };
		final float[] ys = { y, y + e, (y + h) - e, y + h };
		this.batch.setColor(1, 1, 1, alpha);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				if ((row == 1) && (column == 1) && !frame.centre) {
					continue;
				}
				this.batch.draw(frame.parts[(row * 3) + column], xs[column], ys[row], xs[column + 1] - xs[column],
						ys[row + 1] - ys[row]);
			}
		}
		this.batch.setColor(Color.WHITE);
	}

	/** A plain frame line when the skin lacks its frame art. */
	void outline(final float x, final float y, final float w, final float h, final float line, final int argb) {
		fill(x, y, w, line, argb);
		fill(x, (y + h) - line, w, line, argb);
		fill(x, y, line, h, argb);
		fill((x + w) - line, y, line, h, argb);
	}

	/** Additive drawing, for highlights. */
	void additive(final boolean on) {
		this.batch.setBlendFunction(GL20.GL_SRC_ALPHA, on ? GL20.GL_ONE : GL20.GL_ONE_MINUS_SRC_ALPHA);
	}

	// ---------------------------------------------------------------- text

	/**
	 * Sets the face the text is drawn in from here on: MasterFontBold of the Reforged interface for headings and
	 * names, else the skin's text font.
	 */
	void bold(final boolean on) {
		this.bold = on;
	}

	private BitmapFont sized(final float size) {
		return sized(size, null);
	}

	private BitmapFont sized(final float size, final String text) {
		final int pixels = Math.max(6, Math.round(size * this.b.scale));
		final BitmapFont font = this.art.font(pixels, this.bold, text);
		font.getData().setScale(1f / this.b.scale);
		return font;
	}

	/**
	 * A line in small capitals, as the Reforged style sheets set them: the small letters are capitals of
	 * {@link SmallCaps#SCALE} of the size, standing on the same line. Placed and cut as {@link #text}.
	 */
	void smallCaps(final String value, final float x, final float y, final float w, final float h,
			final float size, final int argb, final int align) {
		if ((value == null) || value.isEmpty()) {
			return;
		}
		final List<SmallCaps.Run> runs = SmallCaps.runs(GlueText.plain(value));
		final float smaller = size * SmallCaps.SCALE;
		final float[] widths = new float[runs.size()];
		final float total = smallCapsWidths(runs, size, widths);
		final float lift = (sized(size).getCapHeight() - sized(smaller).getCapHeight()) / 2;
		float left = align == Align.center ? x + ((w - total) / 2) : align == Align.right ? (x + w) - total : x;
		for (int i = 0; i < widths.length; i++) {
			final SmallCaps.Run run = runs.get(i);
			text(run.text, left, run.small ? y + lift : y, widths[i] + 1, h, run.small ? smaller : size, argb, Align.left);
			left += widths[i];
		}
	}

	/** The width of a text in small capitals in design pixels; each run's own width goes in the array. */
	private float smallCapsWidths(final List<SmallCaps.Run> runs, final float size, final float[] widths) {
		final float smaller = size * SmallCaps.SCALE;
		float total = 0;
		for (int i = 0; i < widths.length; i++) {
			final float at = runs.get(i).small ? smaller : size;
			widths[i] = runWidth(runs.get(i).text, at);
			total += widths[i];
		}
		return total;
	}

	/**
	 * The width of a run with the spaces at its ends, which a laid out line drops. The letters are measured as they
	 * stand, not between signs, which the font kerns against them.
	 */
	private float runWidth(final String run, final float size) {
		int start = 0;
		int end = run.length();
		while ((start < end) && (run.charAt(start) == ' ')) {
			start++;
		}
		while ((end > start) && (run.charAt(end - 1) == ' ')) {
			end--;
		}
		final int spaces = run.length() - (end - start);
		final float space = spaces == 0 ? 0 : width("I I", size) - (2 * width("I", size));
		return (end > start ? width(run.substring(start, end), size) : 0) + (spaces * space);
	}

	/** The width of a text set in small capitals. */
	float smallCapsWidth(final String value, final float size) {
		final List<SmallCaps.Run> runs = SmallCaps.runs(GlueText.plain(value));
		return smallCapsWidths(runs, size, new float[runs.size()]);
	}

	/** The width of a text in design pixels. */
	float width(final String text, final float size) {
		final String plain = GlueText.plain(text);
		this.layout.setText(sized(size, plain), plain);
		return this.layout.width;
	}

	/**
	 * A line of glue text in its colours, vertically centred in the height, aligned left, centre or right, cut with
	 * an ellipsis when it is too wide; true when it was cut.
	 */
	boolean text(final String value, final float x, final float y, final float w, final float h, final float size,
			final int argb, final int align) {
		if ((value == null) || value.isEmpty()) {
			return false;
		}
		final String plain = GlueText.plain(value);
		final BitmapFont font = sized(size, plain);
		this.layout.setText(font, plain);
		final boolean cut = this.layout.width > (w + 0.5f);
		final float top = y + ((h - font.getCapHeight()) / 2) - (font.getAscent() * 0.15f);
		if (cut) {
			this.color.set(argbColor(argb));
			drawShadowed(font, plain, x, top, w, align, true, argb);
			return true;
		}
		final List<GlueText.Run> runs = GlueText.runs(value);
		float left = x;
		if (align != Align.left) {
			left = align == Align.center ? x + ((w - this.layout.width) / 2) : (x + w) - this.layout.width;
		}
		if (runs.size() == 1) {
			drawShadowed(font, plain, left, top, 0, Align.left, false,
					runs.get(0).colour != 0 ? runs.get(0).colour : argb);
			return false;
		}
		// Coloured runs are drawn as one markup line, so that the letters keep their spacing across the colours.
		final StringBuilder marked = new StringBuilder();
		for (final GlueText.Run run : runs) {
			final int colour = run.colour != 0 ? run.colour : argb;
			marked.append(String.format(java.util.Locale.ROOT, "[#%06X%02X]", colour & 0xFFFFFF, (colour >>> 24) & 0xFF))
					.append(run.text.replace("[", "[[")).append("[]");
		}
		final float shadow = Math.max(1 / this.b.scale, font.getCapHeight() * 0.1f);
		font.setColor(0, 0, 0, ((argb >>> 24) & 0xFF) / 255f * 0.9f);
		font.draw(this.batch, plain, left + shadow, top + shadow);
		font.setColor(Color.WHITE);
		font.getData().markupEnabled = true;
		try {
			font.draw(this.batch, marked, left, top);
		}
		finally {
			font.getData().markupEnabled = false;
		}
		return false;
	}

	private void drawShadowed(final BitmapFont font, final String text, final float x, final float y, final float w,
			final int align, final boolean truncate, final int argb) {
		final float shadow = Math.max(1 / this.b.scale, font.getCapHeight() * 0.1f);
		font.setColor(0, 0, 0, ((argb >>> 24) & 0xFF) / 255f * 0.9f);
		if (truncate) {
			font.draw(this.batch, text, x + shadow, y + shadow, 0, text.length(), w, align, false, "\u2026");
		}
		else {
			font.draw(this.batch, text, x + shadow, y + shadow);
		}
		font.setColor(argbColor(argb));
		if (truncate) {
			font.draw(this.batch, text, x, y, 0, text.length(), w, align, false, "\u2026");
		}
		else {
			font.draw(this.batch, text, x, y);
		}
		font.setColor(Color.WHITE);
	}

	private Color argbColor(final int argb) {
		return this.color.set(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
				((argb >>> 24) & 0xFF) / 255f);
	}

	/** Wrapped text from the top of the place, at most as many lines as fit. */
	void lines(final String value, final float x, final float y, final float w, final float h, final float size,
			final int argb, final int align) {
		if ((value == null) || value.isEmpty()) {
			return;
		}
		final String plain = GlueText.plain(value);
		final BitmapFont font = sized(size, plain);
		pushClip(x - size, y, w + (2 * size), h);
		font.setColor(0, 0, 0, 0.9f);
		final float shadow = Math.max(1 / this.b.scale, size * 0.06f);
		font.draw(this.batch, plain, x + shadow, y + shadow, w, align, true);
		font.setColor(argbColor(argb));
		font.draw(this.batch, plain, x, y, w, align, true);
		font.setColor(Color.WHITE);
		popClip();
	}

	// ---------------------------------------------------------------- widgets

	/** A glue button: the skin's frame, its background and the label; true when clicked. */
	boolean button(final Object id, final String label, final float x, final float y, final float w, final float h,
			final float size, final int tone) {
		final boolean off = tone == TONE_OFF;
		final int state = off ? 0 : hit(id, x, y, w, h);
		final boolean held = (state & HELD) != 0;
		drawButton(x, y, w, h, held ? TONE_DOWN : tone);
		if ((state & HOVER) != 0) {
			additive(true);
			// The pressed look and the colored buttons are dark: their light has to be stronger to show.
			drawButton(x, y, w, h, held ? TONE_DOWN : tone, tone == TONE_NORMAL ? 0.22f : 0.5f);
			additive(false);
		}
		final int textColour = off ? SkinArt.Palette.DISABLED
				: (tone == TONE_DOWN) || held || (tone == TONE_CONFIRM) || (tone == TONE_DANGER) ? 0xFFFFFFFF
						: this.palette.buttonText;
		final float dy = held ? h * 0.03f : 0;
		final boolean cut = text(label, x + (h * 0.2f), y + dy, w - (h * 0.4f), h, size, textColour, Align.center);
		if (cut && ((state & HOVER) != 0)) {
			tip(GlueText.plain(label));
		}
		return (state & CLICK) != 0;
	}

	void drawButton(final float x, final float y, final float w, final float h, final int tone) {
		drawButton(x, y, w, h, tone, 1);
	}

	void drawButton(final float x, final float y, final float w, final float h, final int tone,
			final float alpha) {
		if (this.art.reforged) {
			final String name;
			switch (tone) {
			case TONE_DOWN:
				name = SkinArt.BUTTON_BORDER_DOWN;
				break;
			case TONE_OFF:
				name = SkinArt.BUTTON_BORDER_DISABLED;
				break;
			case TONE_CONFIRM:
				name = SkinArt.BUTTON_GREEN;
				break;
			case TONE_DANGER:
				name = SkinArt.BUTTON_RED;
				break;
			default:
				name = SkinArt.BUTTON_BORDER;
				break;
			}
			nine(this.art.frame(name), x, y, w, h, Math.max(8, h * 0.3f), alpha);
			return;
		}
		final String suffix = tone == TONE_OFF ? "disabled" : (tone == TONE_NORMAL) ? "" : "down";
		final float border = Math.max(4, h * 0.22f);
		final TextureRegion background = this.art.image(suffix.isEmpty() ? SkinArt.BUTTON_BACKGROUND
				: suffix.equals("down") ? SkinArt.BUTTON_BACKGROUND_DOWN : SkinArt.BUTTON_BACKGROUND_DISABLED);
		this.batch.setColor(1, 1, 1, alpha);
		final float inset = border * 0.35f;
		tiled(background, x + inset, y + inset, w - (2 * inset), h - (2 * inset), h * 0.6f, h * 0.6f);
		this.batch.setColor(Color.WHITE);
		nine(this.art.frame(suffix.isEmpty() ? SkinArt.BUTTON_BORDER
				: suffix.equals("down") ? SkinArt.BUTTON_BORDER_DOWN : SkinArt.BUTTON_BORDER_DISABLED), x, y, w, h,
				border, alpha);
	}

	/** A panel: the skin's panel frame on a dark fill. */
	void panel(final float x, final float y, final float w, final float h, final float frame) {
		final float inset = frame * 0.35f;
		fill(x + inset, y + inset, w - (2 * inset), h - (2 * inset), this.art.reforged ? 0xDB0E0C0A : 0xC7000000);
		final LauncherArt.Frame art = this.art.frame(SkinArt.PANEL_BORDER);
		if (art != null) {
			nine(art, x, y, w, h, frame);
		}
		else {
			outline(x, y, w, h, 3, this.palette.line);
		}
	}

	/** An edit box frame, the frame of the campaign boxes and of text fields. */
	void edit(final float x, final float y, final float w, final float h, final float edge, final int fill) {
		if (this.art.reforged) {
			if (fill != 0) {
				fill(x + (edge * 0.4f), y + (edge * 0.4f), w - (edge * 0.8f), h - (edge * 0.8f), fill);
			}
			nine(this.art.frame(SkinArt.EDIT_BORDER), x, y, w, h, edge);
			return;
		}
		final float inset = edge * 0.4f;
		if (fill != 0) {
			fill(x + inset, y + inset, w - (2 * inset), h - (2 * inset), fill);
		}
		else {
			final float tile = this.b.tablet ? 110 : 60;
			tiled(this.art.image(SkinArt.EDIT_BACKGROUND), x + inset, y + inset, w - (2 * inset), h - (2 * inset),
					tile, tile);
		}
		nine(this.art.frame(SkinArt.EDIT_BORDER), x, y, w, h, edge);
	}

	/** A dialog frame on its background. */
	void dialog(final float x, final float y, final float w, final float h) {
		final float edge = this.b.tablet ? 48 : 32;
		final float inset = edge * 0.4f;
		fill(x + inset, y + inset, w - (2 * inset), h - (2 * inset), 0xFF03040A);
		final float tile = this.b.tablet ? 256 : 128;
		final TextureRegion background = this.art.image(SkinArt.DIALOG_BACKGROUND);
		if (background != null) {
			tiled(background, x + inset, y + inset, w - (2 * inset), h - (2 * inset), tile,
					(tile * background.getRegionHeight()) / Math.max(1, background.getRegionWidth()));
		}
		nine(this.art.frame(SkinArt.DIALOG_BORDER), x, y, w, h, edge);
	}

	/** A check box; true when clicked. */
	boolean check(final Object id, final float x, final float y, final float s, final boolean on) {
		final int state = hit(id, x, y, s, s);
		image(SkinArt.CHECKBOX, x, y, s, s);
		if (on) {
			image(SkinArt.CHECKBOX_CHECK, x, y, s, s);
		}
		if ((state & HOVER) != 0) {
			fill(x, y, s, s, 0x22FFFFFF);
		}
		return (state & CLICK) != 0;
	}

	/** A radio button; true when clicked. */
	boolean radio(final Object id, final float x, final float y, final float s, final boolean on, final float hitW) {
		final int state = hit(id, x, y, hitW, s);
		if (this.art.reforged) {
			final float r = s / 2;
			fill(x + (s * 0.1f), y + (s * 0.1f), s * 0.8f, s * 0.8f, 0x99000000);
			outline(x + (s * 0.05f), y + (s * 0.05f), s * 0.9f, s * 0.9f, s * 0.05f, this.palette.line);
			if (on) {
				fill((x + r) - (r * 0.45f), (y + r) - (r * 0.45f), r * 0.9f, r * 0.9f, this.palette.gold);
			}
		}
		else {
			image(SkinArt.RADIO, x, y, s, s);
			if (on) {
				image(SkinArt.RADIO_BUTTON, x, y, s, s);
			}
		}
		return (state & CLICK) != 0;
	}

	/** A slider over 0..100; gives the value, changed while it is dragged. */
	int slider(final Object id, final float x, final float y, final float w, final float control, final int value) {
		final int state = hit(id, x, y, w, control);
		final float grooveH = control * 0.5f;
		final float grooveY = y + ((control - grooveH) / 2);
		if (((state & HELD) != 0) && this.justPressed) {
			this.dragged = id;
		}
		int next = value;
		if (Objects.equals(this.dragged, id) && this.mouseDown) {
			next = Math.round(Math.max(0, Math.min(1, (this.mouseX - x) / w)) * 100);
		}
		final int step = takeArrow(id);
		if (step != 0) {
			next = Math.max(0, Math.min(100, value + (step * 5)));
			this.keyStepped = id;
		}
		final float v = next / 100f;
		if (this.art.reforged) {
			fill(x, grooveY, w, grooveH, 0xFF0A0908);
			outline(x, grooveY, w, grooveH, 2, 0xFF3A3631);
			final float knobH = control * 0.8f;
			final float knobW = (knobH * 98) / 106;
			image(SkinArt.KNOB, (x + (grooveH / 4) + ((w - (grooveH / 2)) * v)) - (knobW / 2),
					y + ((control - knobH) / 2), knobW, knobH);
		}
		else {
			final float inset = grooveH * 0.2f;
			tiled(this.art.image(SkinArt.SLIDER_BACKGROUND), x + inset, grooveY + inset, w - (2 * inset),
					grooveH - (2 * inset), grooveH * 0.4f, grooveH * 0.4f);
			nine(this.art.frame(SkinArt.SLIDER_BORDER), x, grooveY, w, grooveH, grooveH * 0.35f);
			final float knobW = control * 0.55f;
			image(SkinArt.KNOB, x + ((w - knobW) * v), y + ((control - knobW) / 2), knobW, knobW);
		}
		return next;
	}

	/** The slider being dragged was let go this frame. */
	boolean released(final Object id) {
		return (this.justReleased && Objects.equals(this.dragged, id)) || Objects.equals(this.keyStepped, id);
	}

	/**
	 * A drop-down menu: the glue button, the chosen item and the pull-down arrow; a click opens the list. The wheel
	 * over a closed menu does not change it.
	 */
	void dropdown(final Object id, final String[] items, final int selected, final float x, final float y,
			final float w, final float h, final float size, final boolean enabled, final int[] swatches,
			final IntConsumer chosen) {
		final int state = enabled ? hit(id, x, y, w, h) : 0;
		final boolean open = Objects.equals(this.menuId, id);
		drawButton(x, y, w, h, !enabled ? TONE_OFF : open ? TONE_DOWN : TONE_NORMAL);
		if ((state & HOVER) != 0) {
			additive(true);
			drawButton(x, y, w, h, TONE_NORMAL, 0.2f);
			additive(false);
		}
		final float rim = this.art.reforged ? Math.max(8, h * 0.3f) : Math.max(4, h * 0.22f);
		final float arrow = Math.min(h, 72) * 0.42f;
		if ((swatches != null) && (selected >= 0) && (selected < swatches.length)) {
			final float sw = w * 0.6f;
			final float sh = Math.max(8, (h - (2 * rim)) * 0.55f);
			fill(x + ((w - sw) / 2) - (arrow / 2), y + ((h - sh) / 2), sw, sh, swatches[selected]);
		}
		else if ((selected >= 0) && (selected < items.length)) {
			// A longer item shrinks to the button rather than being cut (Computer (Insane) in a narrow slot).
			final float room = w - (2 * rim) - (arrow * 2.2f);
			final float natural = width(items[selected], size);
			final float fitted = natural > room && room > 0 ? Math.max(size * 0.65f, size * room / natural) : size;
			final boolean cut = text(items[selected], x + rim + 4, y, room, h, fitted,
					!enabled ? SkinArt.Palette.DISABLED : this.art.reforged ? this.palette.text : this.palette.gold,
					Align.left);
			if (cut && ((state & HOVER) != 0)) {
				tip(GlueText.plain(items[selected]));
			}
		}
		// The Reforged menu has its own gold chevron, with a disabled picture instead of a faded one.
		final boolean chevron = this.art.reforged && this.art.has(SkinArt.CHEVRON_DOWN);
		image(this.art.image(!chevron ? SkinArt.PULLDOWN_ARROW
				: enabled || !this.art.has(SkinArt.CHEVRON_DOWN_DISABLED) ? SkinArt.CHEVRON_DOWN
					: SkinArt.CHEVRON_DOWN_DISABLED),
			(x + w) - rim - (arrow * 1.25f), y + ((h - arrow) / 2), arrow, arrow, enabled || chevron ? 1 : 0.5f);
		if ((state & CLICK) != 0) {
			if (open) {
				this.menuId = null;
			}
			else {
				this.menuId = id;
				this.menuItems = items;
				this.menuColours = swatches;
				this.menuSelected = selected;
				this.menuAt = new float[] { x, y, w, h };
				this.menuText = size;
				this.menuChosen = chosen;
				this.menuScroll = 0;
				this.menuCursor = selected;
				this.menuContext = false;
				this.menuEnabled = null;
			}
		}
	}

	/**
	 * A context menu at the mouse: its items, those that can be chosen (null for all), and what a choice does. It
	 * closes like a drop-down menu: a choice, a click elsewhere or Escape.
	 */
	void contextMenu(final Object id, final String[] items, final boolean[] enabled, final IntConsumer chosen) {
		final float size = this.b.tablet ? 34 : 24;
		float widest = 0;
		for (final String item : items) {
			widest = Math.max(widest, width(item, size));
		}
		final float edge = this.b.tablet ? 14 : 8;
		this.menuId = id;
		this.menuItems = items;
		this.menuColours = null;
		this.menuSelected = -1;
		this.menuEnabled = enabled;
		this.menuContext = true;
		this.menuAt = new float[] { this.mouseX, this.mouseY, widest + (2 * edge) + 72, this.b.tablet ? 80 : 60 };
		this.menuText = size;
		this.menuChosen = chosen;
		this.menuScroll = 0;
		this.menuCursor = -1;
		this.topLayer = 99;
	}

	private boolean menuItemOpen(final int i) {
		return (this.menuEnabled == null) || ((i < this.menuEnabled.length) && this.menuEnabled[i]);
	}

	private void moveMenuCursor(final int delta) {
		final int count = this.menuItems.length;
		int at = this.menuCursor;
		for (int tries = 0; tries < count; tries++) {
			at = at < 0 ? (delta > 0 ? 0 : count - 1) : Math.max(0, Math.min(count - 1, at + delta));
			if (menuItemOpen(at)) {
				this.menuCursor = at;
				return;
			}
			if (((at == 0) && (delta < 0)) || ((at == (count - 1)) && (delta > 0))) {
				return;
			}
		}
	}

	boolean menuOpen() {
		return this.menuId != null;
	}

	void closeMenu() {
		this.menuId = null;
	}

	/** The open menu's list, over everything: below its button, or above it when it would leave the board. */
	private void drawMenu() {
		if (this.menuId == null) {
			return;
		}
		this.layer = 99;
		this.topLayer = 99;
		float x = this.menuAt[0];
		final float w = this.menuAt[2];
		final float h = this.menuAt[3];
		final float itemH = this.menuContext ? h : Math.max(this.b.tablet ? 88 : 60, h * 0.8f);
		final float edge = this.b.tablet ? 14 : 8;
		final int shown = Math.min(this.menuItems.length, Math.max(1, (int) ((this.b.height * 0.6f) / itemH)));
		final float listH = (shown * itemH) + (2 * edge);
		float y;
		if (this.menuContext) {
			x = Math.max(4, Math.min(x, this.b.width - w - 4));
			y = this.menuAt[1];
			if ((y + listH) > (this.b.height - 8)) {
				y = Math.max(4, y - listH);
			}
		}
		else {
			y = this.menuAt[1] + h + 4;
			if ((y + listH) > (this.b.height - 8)) {
				y = this.menuAt[1] - 4 - listH;
			}
		}
		edit(x, y, w, listH, edge, 0xF2000000);
		if (over(x, y, w, listH) && (this.wheel != 0)) {
			this.menuScroll = Math.max(0, Math.min(this.menuItems.length - shown, this.menuScroll + this.wheel));
		}
		if (this.menuCursor >= 0) {
			this.menuScroll = Math.max(Math.min(this.menuScroll, this.menuCursor), this.menuCursor - shown + 1);
		}
		boolean clickedInside = false;
		int chosenItem = -1;
		for (int row = 0; row < shown; row++) {
			final int i = row + this.menuScroll;
			final float iy = y + edge + (row * itemH);
			final boolean open = menuItemOpen(i);
			final int state = open ? hit("menu" + i, x + edge, iy, w - (2 * edge), itemH) : 0;
			if ((state & HOVER) != 0) {
				this.menuCursor = -1;
			}
			final boolean lit = ((state & HOVER) != 0) || (i == this.menuCursor);
			if (lit || (i == this.menuSelected)) {
				image(this.art.image(SkinArt.ROW_HIGHLIGHT), x + edge, iy, w - (2 * edge), itemH, lit ? 1 : 0.6f);
			}
			if (this.menuColours != null) {
				fill(x + edge + 16, iy + (itemH * 0.25f), w - (2 * edge) - 32, itemH * 0.5f, this.menuColours[i]);
			}
			else {
				text(this.menuItems[i], x + edge + 16, iy, w - (2 * edge) - 24, itemH, this.menuText,
						!open ? SkinArt.Palette.DISABLED : i == this.menuSelected ? this.palette.gold : this.palette.text,
						Align.left);
			}
			if ((state & CLICK) != 0) {
				clickedInside = true;
				chosenItem = i;
			}
		}
		if (this.keyPress && (this.menuCursor >= 0) && menuItemOpen(this.menuCursor)) {
			chosenItem = this.menuCursor;
			this.keyPress = false;
		}
		if (chosenItem >= 0) {
			final IntConsumer chosen = this.menuChosen;
			this.menuId = null;
			chosen.accept(chosenItem);
		}
		else if (!clickedInside && (this.justReleased || this.rightReleased)
				&& !inside(this.mouseX, this.mouseY, x, y, w, listH)) {
			this.menuId = null;
		}
		this.layer = 0;
	}

	/**
	 * A scrolled list's place: the wheel scrolls it and the original scroll bar shows when it is longer than the
	 * place. Gives the scroll offset; draw the rows below it moved up by that and close with {@link #endList}.
	 */
	float beginList(final Object id, final float x, final float y, final float w, final float h,
			final float contentH) {
		float offset = this.scrolls.getOrDefault(id, 0f);
		final float most = Math.max(0, contentH - h);
		if (over(x, y, w, h) && (this.wheel != 0)) {
			offset += this.wheel * (this.b.tablet ? 120 : 60);
		}
		offset = Math.max(0, Math.min(most, offset));
		this.scrolls.put(id, offset);
		if (most > 0) {
			final float barW = this.b.tablet ? 14 : 8;
			final float barX = (x + w) - barW;
			fill(barX, y, barW, h, 0x66000000);
			final float knobH = Math.max(barW * 3, (h * h) / contentH);
			final float knobY = y + (((h - knobH) * offset) / most);
			fill(barX + 2, knobY, barW - 4, knobH, this.palette.line | 0xFF000000);
			fill(barX + 2, knobY, barW - 4, 2, this.palette.gold);
		}
		pushClip(x, y, w, h);
		this.listIds.push(id);
		this.listId = id;
		return offset;
	}

	void endList() {
		popClip();
		this.listIds.pop();
		this.listId = this.listIds.peek();
	}

	/** Scrolls a list so that a row is in view. */
	void reveal(final Object id, final float rowY, final float rowH, final float listH) {
		final float offset = this.scrolls.getOrDefault(id, 0f);
		if (rowY < offset) {
			this.scrolls.put(id, rowY);
		}
		else if ((rowY + rowH) > (offset + listH)) {
			this.scrolls.put(id, (rowY + rowH) - listH);
		}
	}

	/**
	 * A one line text field with a caret: typing, Backspace and Delete, the arrows, Home and End, Ctrl+A (all), Ctrl+C,
	 * Ctrl+X and Ctrl+V; Enter and Escape are left to the caller in {@link #keys}.
	 */
	void field(final Object id, final StringBuilder text, final float x, final float y, final float w, final float h,
			final float size, final int maxLength, final String placeholder) {
		final int state = hit(id, x, y, w, h);
		final float pad = this.b.tablet ? 22 : 12;
		int caret = Math.max(0, Math.min(text.length(), this.carets.getOrDefault(id, text.length())));
		if ((state & CLICK) != 0) {
			if (!Objects.equals(this.focus, id)) {
				this.selectedAll = null;
			}
			this.focus = id;
			if (inside(this.mouseX, this.mouseY, x, y, w, h)) {
				caret = caretAt(text, this.mouseX - x - pad, size);
				this.selectedAll = (state & DOUBLE) != 0 ? id : null;
			}
		}
		final boolean focused = Objects.equals(this.focus, id);
		if (focused) {
			caret = applyEdits(id, text, caret, maxLength);
		}
		this.carets.put(id, caret);
		edit(x, y, w, h, this.b.tablet ? 14 : 8, 0xD9000000);
		final boolean all = focused && Objects.equals(this.selectedAll, id) && (text.length() > 0);
		if (all) {
			fill(x + pad, y + (h * 0.18f), Math.min(w - (2 * pad), width(text.toString(), size)), h * 0.64f,
					0xAA3A5A8C);
		}
		if (text.length() == 0) {
			text(placeholder, x + pad, y, w - (2 * pad), h, size, this.palette.text2, Align.left);
		}
		else {
			text(text.toString(), x + pad, y, w - (2 * pad), h, size, this.palette.text, Align.left);
		}
		if (focused && !all && (((System.currentTimeMillis() / 530) % 2) == 0)) {
			final float caretX = x + pad + Math.min(w - (2 * pad), caret == 0 ? 0 : width(text.substring(0, caret), size));
			fill(caretX + 2, y + (h * 0.2f), Math.max(2, size * 0.08f), h * 0.6f, this.palette.text);
		}
	}

	/** The edits typed this frame applied to a field's text; gives the caret. */
	private int applyEdits(final Object id, final StringBuilder text, final int from, final int maxLength) {
		int caret = from;
		for (int i = 0; i < this.typed.length(); i++) {
			final char c = this.typed.charAt(i);
			final boolean all = Objects.equals(this.selectedAll, id);
			switch (c) {
			case EDIT_SELECT_ALL:
				this.selectedAll = id;
				continue;
			case EDIT_COPY:
				if (all) {
					Gdx.app.getClipboard().setContents(text.toString());
				}
				continue;
			case EDIT_CUT:
				if (all) {
					Gdx.app.getClipboard().setContents(text.toString());
					text.setLength(0);
					caret = 0;
					this.selectedAll = null;
				}
				continue;
			case EDIT_LEFT:
				caret = all ? 0 : Math.max(0, caret - 1);
				break;
			case EDIT_RIGHT:
				caret = all ? text.length() : Math.min(text.length(), caret + 1);
				break;
			case EDIT_HOME:
				caret = 0;
				break;
			case EDIT_END:
				caret = text.length();
				break;
			case EDIT_BACKSPACE:
			case EDIT_DELETE:
				if (all) {
					text.setLength(0);
					caret = 0;
				}
				else if ((c == EDIT_BACKSPACE) && (caret > 0)) {
					text.deleteCharAt(--caret);
				}
				else if ((c == EDIT_DELETE) && (caret < text.length())) {
					text.deleteCharAt(caret);
				}
				break;
			default:
				if ((c < ' ') || (c == 127)) {
					continue;
				}
				if (all) {
					text.setLength(0);
					caret = 0;
				}
				if (text.length() < maxLength) {
					text.insert(caret++, c);
				}
				break;
			}
			this.selectedAll = null;
		}
		return caret;
	}

	/** The caret's place nearest to a point of the text, from its left. */
	private int caretAt(final StringBuilder text, final float at, final float size) {
		int best = text.length();
		float bestDistance = Float.MAX_VALUE;
		for (int i = 0; i <= text.length(); i++) {
			final float distance = Math.abs((i == 0 ? 0 : width(text.substring(0, i), size)) - at);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	private void drawTooltip(final String text) {
		final float size = this.b.tablet ? 30 : 22;
		final float pad = size * 0.5f;
		final float w = Math.min(this.b.width * 0.75f, width(text, size) + (2 * pad));
		final float h = size * 1.8f;
		float x = this.mouseX + (size * 0.8f);
		float y = this.mouseY + (size * 1.2f);
		if ((x + w) > this.b.width) {
			x = this.b.width - w - 4;
		}
		if ((y + h) > this.b.height) {
			y = this.mouseY - h - 4;
		}
		fill(x, y, w, h, 0xF0000000);
		outline(x, y, w, h, 2, this.palette.line | 0xFF000000);
		text(text, x + pad, y, w - (2 * pad), h, size, this.palette.text, Align.left);
	}
}
