package com.etheller.warsmash.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.etheller.warsmash.launcher.GlueText;
import com.etheller.warsmash.launcher.SkinArt;

/**
 * Drawing pieces of the launcher: game frames, buttons and bars put together from the art that the preparation step
 * cached, each with a plain fallback for art the game data does not have.
 */
final class LauncherKit {
	static final int GOLD = SkinArt.Palette.of(false).gold;
	static final int TEXT = SkinArt.Palette.of(false).text;
	static final int TEXT2 = SkinArt.Palette.of(false).text2;
	static final int CAPTION = SkinArt.Palette.CAPTION;
	static final int DISABLED = SkinArt.Palette.DISABLED;
	static final int BRONZE = SkinArt.Palette.of(false).line;
	static final int OK = SkinArt.Palette.OK;
	static final int OFF = SkinArt.Palette.OFF;

	private LauncherKit() {
	}

	/**
	 * A backdrop as the glue screens draw it: the eight pieces of an edge file around a tiled or plain background. The
	 * pieces come as one 3 x 3 image whose middle is empty.
	 */
	static final class FrameDrawable extends Drawable {
		private final Bitmap nine;
		private final float border;
		private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
		private final Paint fill = new Paint();
		private final Paint fallback = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Rect source = new Rect();
		private final RectF target = new RectF();
		private Bitmap tile;
		private float tileSize;
		private int fillColor;
		private int fallbackFill;
		private int fallbackLine = BRONZE;
		private boolean centre;
		private float fillInset = -1;

		FrameDrawable(final Bitmap nine, final float border) {
			this.nine = nine;
			this.border = border;
		}

		/** Also draws the middle of the 3 x 3 image, for frames that are whole pictures (the Reforged ones). */
		FrameDrawable centre() {
			this.centre = true;
			return this;
		}

		FrameDrawable line(final int color) {
			this.fallbackLine = color;
			return this;
		}

		FrameDrawable tiled(final Bitmap tile, final float size) {
			this.tile = tile;
			this.tileSize = size;
			return this;
		}

		FrameDrawable filled(final int color) {
			this.fillColor = color;
			return this;
		}

		/**
		 * Starts the background this far in instead of at the edge pieces' inner side, as the glue backdrops' background
		 * insets do: the inner part of an edge piece is see-through and would show a gap.
		 */
		FrameDrawable fillInset(final float inset) {
			this.fillInset = inset;
			return this;
		}

		/** Drawn when the edge file is missing: a plain box in the launcher palette. */
		FrameDrawable fallback(final int color) {
			this.fallbackFill = color;
			return this;
		}

		@Override
		public void draw(final Canvas canvas) {
			final Rect bounds = getBounds();
			final float b = Math.min(this.border, Math.min(bounds.width(), bounds.height()) / 2f);
			final float f = this.fillInset >= 0 ? Math.min(this.fillInset, b) : b;
			final RectF inner = new RectF(bounds.left + f, bounds.top + f, bounds.right - f, bounds.bottom - f);
			if (this.fillColor != 0) {
				this.fill.setShader(null);
				this.fill.setColor(this.fillColor);
				canvas.drawRect(inner, this.fill);
			}
			if (this.tile != null) {
				final BitmapShader shader = new BitmapShader(this.tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
				final Matrix matrix = new Matrix();
				final float scale = this.tileSize / this.tile.getWidth();
				matrix.setScale(scale, scale);
				matrix.postTranslate(inner.left, inner.top);
				shader.setLocalMatrix(matrix);
				this.fill.setColor(Color.WHITE);
				this.fill.setShader(shader);
				this.fill.setFilterBitmap(true);
				canvas.drawRect(inner, this.fill);
			}
			if (this.nine == null) {
				if (this.fallbackFill != 0) {
					this.fallback.setStyle(Paint.Style.FILL);
					this.fallback.setColor(this.fallbackFill);
					canvas.drawRect(new RectF(bounds), this.fallback);
				}
				this.fallback.setStyle(Paint.Style.STROKE);
				this.fallback.setStrokeWidth(Math.max(2, b / 6));
				this.fallback.setColor(this.fallbackLine);
				final RectF edge = new RectF(bounds);
				edge.inset(b / 6, b / 6);
				canvas.drawRect(edge, this.fallback);
				return;
			}
			final int s = this.nine.getWidth() / 3;
			final float x0 = bounds.left;
			final float x1 = bounds.left + b;
			final float x2 = bounds.right - b;
			final float x3 = bounds.right;
			final float y0 = bounds.top;
			final float y1 = bounds.top + b;
			final float y2 = bounds.bottom - b;
			final float y3 = bounds.bottom;
			piece(canvas, 0, 0, s, x0, y0, x1, y1);
			piece(canvas, 1, 0, s, x1, y0, x2, y1);
			piece(canvas, 2, 0, s, x2, y0, x3, y1);
			piece(canvas, 0, 1, s, x0, y1, x1, y2);
			if (this.centre) {
				piece(canvas, 1, 1, s, x1, y1, x2, y2);
			}
			piece(canvas, 2, 1, s, x2, y1, x3, y2);
			piece(canvas, 0, 2, s, x0, y2, x1, y3);
			piece(canvas, 1, 2, s, x1, y2, x2, y3);
			piece(canvas, 2, 2, s, x2, y2, x3, y3);
		}

		private void piece(final Canvas canvas, final int column, final int row, final int s, final float left,
				final float top, final float right, final float bottom) {
			if ((right <= left) || (bottom <= top)) {
				return;
			}
			this.source.set(column * s, row * s, (column + 1) * s, (row + 1) * s);
			this.target.set(left, top, right, bottom);
			canvas.drawBitmap(this.nine, this.source, this.target, this.paint);
		}

		@Override
		public void setAlpha(final int alpha) {
			this.paint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(final ColorFilter colorFilter) {
			this.paint.setColorFilter(colorFilter);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}

	/** An image scaled to cover its bounds, cropped around a horizontal focus point (0 = left edge, 1 = right). */
	static final class CoverDrawable extends Drawable {
		private final Bitmap bitmap;
		private final float focus;
		private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
		private final int scrim;

		CoverDrawable(final Bitmap bitmap, final float focus, final int scrim) {
			this.bitmap = bitmap;
			this.focus = focus;
			this.scrim = scrim;
		}

		@Override
		public void draw(final Canvas canvas) {
			final Rect bounds = getBounds();
			if (this.bitmap == null) {
				canvas.drawColor(Color.BLACK);
				return;
			}
			final float scale = Math.max(bounds.width() / (float) this.bitmap.getWidth(),
					bounds.height() / (float) this.bitmap.getHeight());
			final float visibleW = bounds.width() / scale;
			final float visibleH = bounds.height() / scale;
			final float left = (this.bitmap.getWidth() - visibleW) * this.focus;
			final float top = (this.bitmap.getHeight() - visibleH) / 2;
			final Rect source = new Rect(Math.round(left), Math.round(top), Math.round(left + visibleW),
					Math.round(top + visibleH));
			canvas.save();
			canvas.clipRect(bounds);
			canvas.drawBitmap(this.bitmap, source, bounds, this.paint);
			if (this.scrim != 0) {
				canvas.drawColor(this.scrim);
			}
			canvas.restore();
		}

		@Override
		public void setAlpha(final int alpha) {
			this.paint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(final ColorFilter colorFilter) {
			this.paint.setColorFilter(colorFilter);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.OPAQUE;
		}
	}

	/**
	 * The loading bar of the glue screens: the tiled bar background, the fill repeated along the bar and cut at the
	 * progress, and the glass over both.
	 */
	static final class BarDrawable extends Drawable {
		private final Bitmap background;
		private final Bitmap fill;
		private final Bitmap glass;
		private final float edge;
		private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
		private float progress;

		BarDrawable(final Bitmap background, final Bitmap fill, final Bitmap glass, final float edge) {
			this.background = background;
			this.fill = fill;
			this.glass = glass;
			this.edge = edge;
		}

		void setProgress(final float progress) {
			this.progress = Math.max(0, Math.min(1, progress));
			invalidateSelf();
		}

		@Override
		public void draw(final Canvas canvas) {
			final Rect bounds = getBounds();
			canvas.drawColor(Color.BLACK);
			final RectF inner = new RectF(bounds.left + this.edge, bounds.top + this.edge, bounds.right - this.edge,
					bounds.bottom - this.edge);
			this.paint.setAlpha(255);
			if (this.background != null) {
				drawTiled(canvas, this.background, inner, this.background.getHeight(), inner.right);
			}
			else {
				this.paint.setShader(null);
				this.paint.setColor(Color.rgb(0x05, 0x06, 0x0C));
				canvas.drawRect(inner, this.paint);
			}
			final float right = inner.left + (inner.width() * this.progress);
			if (this.fill != null) {
				drawTiled(canvas, this.fill, inner, inner.height(), right);
			}
			else {
				this.paint.setShader(null);
				this.paint.setColor(Color.rgb(0x10, 0x6D, 0x94));
				canvas.drawRect(inner.left, inner.top, right, inner.bottom, this.paint);
			}
			if (this.glass != null) {
				this.paint.setShader(null);
				this.paint.setAlpha(153);
				canvas.drawBitmap(this.glass, null, inner, this.paint);
			}
		}

		private void drawTiled(final Canvas canvas, final Bitmap bitmap, final RectF area, final float tileHeight,
				final float right) {
			if (right <= area.left) {
				return;
			}
			final BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
			final Matrix matrix = new Matrix();
			final float scale = tileHeight / bitmap.getHeight();
			matrix.setScale(scale, scale);
			matrix.postTranslate(area.left, area.top);
			shader.setLocalMatrix(matrix);
			this.paint.setColor(Color.WHITE);
			this.paint.setShader(shader);
			canvas.drawRect(area.left, area.top, right, area.bottom, this.paint);
			this.paint.setShader(null);
		}

		@Override
		public void setAlpha(final int alpha) {
		}

		@Override
		public void setColorFilter(final ColorFilter colorFilter) {
			this.paint.setColorFilter(colorFilter);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.OPAQUE;
		}
	}

	/** The glue click sounds: the big one for main actions, the mouse click for the rest. */
	interface Clicks {
		void play(boolean main);
	}

	static Clicks clicks;

	/**
	 * A blue glue screen button (GlueScreen-Button1): normal, pressed and disabled backdrops, gold text, and the text
	 * pushed down while pressed as ButtonPushedTextOffset does.
	 */
	static final class GlueButton extends TextView {
		/** A button that destroys (Delete) or confirms (the page's main action), as the design colours them. */
		static final int TONE_PLAIN = 0;
		static final int TONE_DANGER = 1;
		static final int TONE_CONFIRM = 2;
		private static final int DANGER_TEXT = SkinArt.Palette.DANGER_TEXT;
		private final SkinCache.Art art;
		private final float height;
		private final float push;
		private int gold;
		private boolean white;
		private boolean main;

		GlueButton(final Context context, final SkinCache.Art art, final float height, final float textSize) {
			super(context);
			this.art = art;
			this.height = height;
			setBackground(backdrop(art, height, TONE_PLAIN));
			this.gold = restText(art);
			setGravity(Gravity.CENTER);
			setMaxLines(1);
			setIncludeFontPadding(false);
			setTypeface(art.font());
			setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			setTextColor(this.gold);
			setShadowLayer(1, textSize * 0.06f, textSize * 0.06f, Color.BLACK);
			setPadding(0, 0, 0, 0);
			this.push = height * 0.03f;
		}

		/** The text of a button at rest: gold on the classic button, light parchment on the Reforged one. */
		static int restText(final SkinCache.Art art) {
			return SkinArt.Palette.of(art.reforged()).buttonText;
		}

		/**
		 * The glue button's backdrop: normal, pressed and disabled. The Reforged skin has red and green buttons; the
		 * classic one keeps the blue backdrop, its confirming button pressed down as the original's default one.
		 */
		static Drawable backdrop(final SkinCache.Art art, final float height, final int tone) {
			// The Reforged buttons are whole pictures with a wider rim.
			final float border = art.reforged() ? Math.max(8, height * 0.3f) : Math.max(4, height * 0.22f);
			final float tile = height * 0.6f;
			String normal = SkinArt.BUTTON_BORDER;
			String normalBackground = SkinArt.BUTTON_BACKGROUND;
			int normalFallback = Color.rgb(0x14, 0x23, 0x9A);
			String down = SkinArt.BUTTON_BORDER_DOWN;
			if (art.reforged() && (tone != TONE_PLAIN)) {
				final boolean red = tone == TONE_DANGER;
				if (art.has(red ? SkinArt.BUTTON_RED : SkinArt.BUTTON_GREEN)) {
					normal = red ? SkinArt.BUTTON_RED : SkinArt.BUTTON_GREEN;
					down = red ? SkinArt.BUTTON_RED_DOWN : SkinArt.BUTTON_GREEN_DOWN;
				}
				normalFallback = red ? Color.rgb(0x8A, 0x1E, 0x14) : Color.rgb(0x3C, 0x8A, 0x2E);
			}
			else if (!art.reforged() && (tone == TONE_CONFIRM)) {
				normal = SkinArt.BUTTON_BORDER_DOWN;
				normalBackground = SkinArt.BUTTON_BACKGROUND_DOWN;
				normalFallback = Color.rgb(0x00, 0x00, 0x39);
			}
			final StateListDrawable states = new StateListDrawable();
			states.addState(new int[] { -android.R.attr.state_enabled }, button(art, SkinArt.BUTTON_BORDER_DISABLED,
					SkinArt.BUTTON_BACKGROUND_DISABLED, border, tile, Color.rgb(0x0B, 0x0B, 0x0D)));
			// A selected button (the page shown, the chosen tab) keeps the pressed look.
			for (final int state : new int[] { android.R.attr.state_pressed, android.R.attr.state_selected }) {
				states.addState(new int[] { state }, button(art, down, SkinArt.BUTTON_BACKGROUND_DOWN, border, tile,
						Color.rgb(0x00, 0x00, 0x39)));
			}
			states.addState(new int[0], button(art, normal, normalBackground, border, tile, normalFallback));
			return states;
		}

		/** Red for a destroying button, green for the confirming one (classic: its text colour and pressed look). */
		void setTone(final int tone) {
			setBackground(backdrop(this.art, this.height, tone));
			this.gold = (tone == TONE_DANGER) && !this.art.reforged() ? DANGER_TEXT : restText(this.art);
			this.white = tone == TONE_CONFIRM;
			refreshDrawableState();
		}

		/** The page's main action clicks with the big button sound. */
		void setMain(final boolean main) {
			this.main = main;
		}

		@Override
		public boolean performClick() {
			if (clicks != null) {
				clicks.play(this.main);
			}
			return super.performClick();
		}

		private static FrameDrawable button(final SkinCache.Art art, final String border, final String background,
				final float size, final float tile, final int fallback) {
			final FrameDrawable drawable = new FrameDrawable(art.bitmap(border), size)
					.tiled(art.bitmap(background), tile).fallback(fallback);
			return art.reforged() ? drawable.centre() : drawable;
		}

		@Override
		protected void drawableStateChanged() {
			super.drawableStateChanged();
			setTextColor(isEnabled() ? (isPressed() || isSelected() || this.white ? Color.WHITE : this.gold) : DISABLED);
			invalidate();
		}

		@Override
		protected void onDraw(final Canvas canvas) {
			// Only the text moves; the backdrop is drawn before onDraw and stays.
			if (isPressed() || isSelected()) {
				canvas.save();
				canvas.translate(this.push * 0.7f, this.push);
				super.onDraw(canvas);
				canvas.restore();
			}
			else {
				super.onDraw(canvas);
			}
		}
	}

	/** The glue check box: its background, and the check over it when checked; plain drawn boxes without the art. */
	static final class CheckDrawable extends Drawable {
		private final Bitmap background;
		private final Bitmap check;
		private final int line;
		private final int mark;
		private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
		private boolean checked;

		CheckDrawable(final SkinCache.Art art) {
			this.background = art.bitmap(SkinArt.CHECKBOX);
			this.check = art.bitmap(SkinArt.CHECKBOX_CHECK);
			this.line = art.line();
			this.mark = art.gold();
		}

		void setChecked(final boolean checked) {
			this.checked = checked;
			invalidateSelf();
		}

		@Override
		public void draw(final Canvas canvas) {
			final RectF bounds = new RectF(getBounds());
			if (this.background != null) {
				canvas.drawBitmap(this.background, null, bounds, this.paint);
			}
			else {
				this.paint.setStyle(Paint.Style.FILL);
				this.paint.setColor(Color.rgb(0x05, 0x06, 0x0C));
				canvas.drawRect(bounds, this.paint);
				this.paint.setStyle(Paint.Style.STROKE);
				this.paint.setStrokeWidth(Math.max(2, bounds.width() / 16));
				this.paint.setColor(this.line);
				canvas.drawRect(bounds, this.paint);
			}
			if (!this.checked) {
				return;
			}
			if (this.check != null) {
				canvas.drawBitmap(this.check, null, bounds, this.paint);
			}
			else {
				this.paint.setStyle(Paint.Style.STROKE);
				this.paint.setStrokeWidth(Math.max(3, bounds.width() / 9));
				this.paint.setColor(this.mark);
				final float w = bounds.width();
				canvas.drawLine(bounds.left + (w * 0.22f), bounds.top + (w * 0.52f), bounds.left + (w * 0.42f),
						bounds.top + (w * 0.74f), this.paint);
				canvas.drawLine(bounds.left + (w * 0.42f), bounds.top + (w * 0.74f), bounds.left + (w * 0.8f),
						bounds.top + (w * 0.26f), this.paint);
			}
		}

		@Override
		public void setAlpha(final int alpha) {
			this.paint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(final ColorFilter colorFilter) {
			this.paint.setColorFilter(colorFilter);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}

	/**
	 * The glue radio button. The Reforged interface has none of its own, so there, as without the art, it is a ring in
	 * the skin's line colour with a gold dot.
	 */
	static final class RadioDrawable extends Drawable {
		private final Bitmap background;
		private final Bitmap button;
		private final int line;
		private final int mark;
		private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
		private boolean checked;
		private boolean enabled = true;

		RadioDrawable(final SkinCache.Art art) {
			this.background = art.reforged() ? null : art.bitmap(SkinArt.RADIO);
			this.button = art.reforged() ? null : art.bitmap(SkinArt.RADIO_BUTTON);
			this.line = art.line();
			this.mark = art.gold();
		}

		void set(final boolean checked, final boolean enabled) {
			this.checked = checked;
			this.enabled = enabled;
			// The disabled glue controls are drawn greyed out and darker.
			if (enabled) {
				this.paint.setColorFilter(null);
			}
			else {
				final ColorMatrix grey = new ColorMatrix();
				grey.setSaturation(0);
				grey.postConcat(new ColorMatrix(new float[] { 0.6f, 0, 0, 0, 0, 0, 0.6f, 0, 0, 0, 0, 0,
					0.6f, 0, 0, 0, 0, 0, 1, 0 }));
				this.paint.setColorFilter(new ColorMatrixColorFilter(grey));
			}
			invalidateSelf();
		}

		@Override
		public void draw(final Canvas canvas) {
			final RectF bounds = new RectF(getBounds());
			if (this.background != null) {
				canvas.drawBitmap(this.background, null, bounds, this.paint);
				if (this.checked && (this.button != null)) {
					canvas.drawBitmap(this.button, null, bounds, this.paint);
				}
				return;
			}
			final float r = bounds.width() / 2;
			this.paint.setStyle(Paint.Style.FILL);
			this.paint.setColor(Color.argb(0x99, 0, 0, 0));
			canvas.drawCircle(bounds.centerX(), bounds.centerY(), r, this.paint);
			this.paint.setStyle(Paint.Style.STROKE);
			this.paint.setStrokeWidth(Math.max(3, r / 8));
			this.paint.setColor(this.enabled ? this.line : OFF);
			canvas.drawCircle(bounds.centerX(), bounds.centerY(), r - (r / 16), this.paint);
			if (this.checked) {
				this.paint.setStyle(Paint.Style.FILL);
				this.paint.setColor(this.enabled ? this.mark : DISABLED);
				canvas.drawCircle(bounds.centerX(), bounds.centerY(), r * 0.56f, this.paint);
			}
		}

		@Override
		public void setAlpha(final int alpha) {
			this.paint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(final ColorFilter colorFilter) {
			this.paint.setColorFilter(colorFilter);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}

	/** The glue slider: its framed groove and the scroll bar knob, which a finger drags anywhere along the view. */
	static final class Slider extends View {
		interface Listener {
			void changed(int value, boolean done);
		}

		private final Drawable groove;
		private final Bitmap knob;
		private final int knobColor;
		private final float grooveHeight;
		private final float knobSize;
		private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
		private Listener listener;
		private int value;

		Slider(final Context context, final SkinCache.Art art, final float grooveHeight, final float knobSize) {
			super(context);
			final Bitmap border = art.reforged() ? null : art.bitmap(SkinArt.SLIDER_BORDER);
			this.groove = new FrameDrawable(border, Math.max(3, grooveHeight * 0.3f))
					.tiled(art.reforged() ? null : art.bitmap(SkinArt.SLIDER_BACKGROUND), grooveHeight)
					.fallback(Color.rgb(0x0B, 0x0A, 0x09)).line(art.line());
			this.knob = art.bitmap(SkinArt.KNOB);
			this.knobColor = art.gold();
			this.grooveHeight = grooveHeight;
			this.knobSize = knobSize;
			setClickable(true);
		}

		void setListener(final Listener listener) {
			this.listener = listener;
		}

		void setValue(final int value) {
			this.value = Math.max(0, Math.min(100, value));
			invalidate();
		}

		private float travel() {
			return getWidth() - this.knobSize;
		}

		@Override
		protected void onDraw(final Canvas canvas) {
			final float top = (getHeight() - this.grooveHeight) / 2;
			this.groove.setBounds(Math.round(this.knobSize / 4), Math.round(top),
					Math.round(getWidth() - (this.knobSize / 4)), Math.round(top + this.grooveHeight));
			this.groove.draw(canvas);
			final float left = (travel() * this.value) / 100f;
			final RectF knob = new RectF(left, (getHeight() - this.knobSize) / 2, left + this.knobSize,
					(getHeight() + this.knobSize) / 2);
			if (this.knob != null) {
				// The Reforged knob is taller than wide; it keeps its shape inside the square.
				final float aspect = this.knob.getWidth() / (float) this.knob.getHeight();
				if (aspect < 0.95f) {
					final float w = knob.height() * aspect;
					knob.inset((knob.width() - w) / 2, 0);
				}
				canvas.drawBitmap(this.knob, null, knob, this.paint);
			}
			else {
				this.paint.setStyle(Paint.Style.FILL);
				this.paint.setColor(this.knobColor);
				canvas.drawRect(knob.left + (knob.width() * 0.3f), knob.top, knob.right - (knob.width() * 0.3f),
						knob.bottom, this.paint);
			}
		}

		@Override
		public boolean onTouchEvent(final MotionEvent event) {
			if (!isEnabled()) {
				return false;
			}
			switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				getParent().requestDisallowInterceptTouchEvent(true);
				//$FALL-THROUGH$
			case MotionEvent.ACTION_MOVE:
			case MotionEvent.ACTION_UP:
				final float at = event.getX() - (this.knobSize / 2);
				final int next = Math.round(Math.max(0, Math.min(1, at / Math.max(1, travel()))) * 100);
				final boolean done = event.getActionMasked() == MotionEvent.ACTION_UP;
				if ((next != this.value) || done) {
					setValue(next);
					if (this.listener != null) {
						this.listener.changed(next, done);
					}
				}
				return true;
			case MotionEvent.ACTION_CANCEL:
				if (this.listener != null) {
					this.listener.changed(this.value, true);
				}
				return true;
			default:
				return true;
			}
		}
	}

	/** A number in a dark blue disc with a bronze rim, as the numbered steps and problems of Help show. */
	static Drawable disc() {
		final GradientDrawable disc = new GradientDrawable();
		disc.setShape(GradientDrawable.OVAL);
		disc.setColor(Color.rgb(0x00, 0x00, 0x39));
		disc.setStroke(3, BRONZE);
		return disc;
	}

	/**
	 * The Help pictograms, drawn rather than taken from any game: a hand and the gesture's mark in gold, laid out on a
	 * 120 x 116 grid.
	 */
	static final class Pictogram extends Drawable {
		enum Kind {
			TAP, DOUBLE, DRAG, HOLD, SHIFT, TWO, PINCH, MINIMAP, COMPUTER, ZIP, FOLDER
		}

		private static final int MARK = Color.rgb(0xFC, 0xD3, 0x12);
		private static final int HAND = Color.rgb(0xE6, 0xE8, 0xEF);
		private final Kind kind;
		private final Typeface font;
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

		Pictogram(final Kind kind, final Typeface font) {
			this.kind = kind;
			this.font = font;
		}

		@Override
		public void draw(final Canvas canvas) {
			final Rect bounds = getBounds();
			final float unit = Math.min(bounds.width() / 120f, bounds.height() / 116f);
			canvas.save();
			canvas.translate(bounds.left + ((bounds.width() - (120 * unit)) / 2),
					bounds.top + ((bounds.height() - (116 * unit)) / 2) + (6 * unit));
			canvas.scale(unit, unit);
			this.paint.setStrokeCap(Paint.Cap.ROUND);
			this.paint.setStrokeJoin(Paint.Join.ROUND);
			switch (this.kind) {
			case COMPUTER:
				stroke(MARK, 5);
				canvas.drawRect(18, 8, 102, 66, this.paint);
				canvas.drawLine(60, 66, 60, 84, this.paint);
				canvas.drawLine(36, 88, 84, 88, this.paint);
				stroke(HAND, 4);
				canvas.drawRect(44, 22, 76, 52, this.paint);
				canvas.drawLine(52, 22, 52, 52, this.paint);
				break;
			case ZIP:
				stroke(MARK, 5);
				canvas.drawRoundRect(new RectF(64, 0, 104, 96), 8, 8, this.paint);
				canvas.drawLine(78, 84, 90, 84, this.paint);
				stroke(HAND, 5);
				canvas.drawRect(10, 26, 42, 66, this.paint);
				canvas.drawLine(26, 26, 26, 66, this.paint);
				stroke(MARK, 5);
				canvas.drawLine(46, 46, 60, 46, this.paint);
				canvas.drawLine(52, 38, 60, 46, this.paint);
				canvas.drawLine(52, 54, 60, 46, this.paint);
				break;
			case FOLDER:
				stroke(MARK, 5);
				final Path folder = new Path();
				folder.moveTo(12, 24);
				folder.lineTo(46, 24);
				folder.lineTo(54, 34);
				folder.lineTo(108, 34);
				folder.lineTo(108, 90);
				folder.lineTo(12, 90);
				folder.close();
				canvas.drawPath(folder, this.paint);
				stroke(Color.rgb(0x6F, 0xCF, 0x6F), 7);
				canvas.drawLine(40, 62, 54, 76, this.paint);
				canvas.drawLine(54, 76, 82, 48, this.paint);
				break;
			default:
				gesture(canvas);
				hand(canvas);
				break;
			}
			canvas.restore();
		}

		private void stroke(final int color, final float width) {
			this.paint.setStyle(Paint.Style.STROKE);
			this.paint.setColor(color);
			this.paint.setStrokeWidth(width);
			this.paint.setPathEffect(null);
		}

		private void ring(final Canvas canvas, final float r) {
			stroke(MARK, 4);
			canvas.drawCircle(45, 30, r, this.paint);
		}

		private void arrow(final Canvas canvas, final float from, final float to) {
			stroke(MARK, 5);
			canvas.drawLine(from, 30, to, 30, this.paint);
			canvas.drawLine(to - 10, 22, to, 30, this.paint);
			canvas.drawLine(to - 10, 38, to, 30, this.paint);
		}

		private void label(final Canvas canvas, final String text, final float x, final float y, final float size) {
			this.paint.setStyle(Paint.Style.FILL);
			this.paint.setColor(MARK);
			this.paint.setTypeface(this.font);
			this.paint.setTextSize(size);
			canvas.drawText(text, x, y, this.paint);
		}

		private void gesture(final Canvas canvas) {
			switch (this.kind) {
			case TAP:
				ring(canvas, 12);
				break;
			case DOUBLE:
				ring(canvas, 9);
				ring(canvas, 16);
				label(canvas, "2x", 66, 22, 16);
				break;
			case DRAG:
				ring(canvas, 9);
				arrow(canvas, 58, 100);
				stroke(MARK, 3);
				this.paint.setPathEffect(new DashPathEffect(new float[] { 5, 4 }, 0));
				canvas.drawRect(60, 10, 104, 50, this.paint);
				this.paint.setPathEffect(null);
				break;
			case HOLD:
				this.paint.setStyle(Paint.Style.FILL);
				this.paint.setColor(Color.argb(0x59, 0xFC, 0xD3, 0x12));
				canvas.drawCircle(45, 30, 14, this.paint);
				ring(canvas, 14);
				label(canvas, "1s", 66, 22, 16);
				break;
			case SHIFT:
				stroke(MARK, 4);
				canvas.drawRoundRect(new RectF(70, 8, 110, 38), 4, 4, this.paint);
				// An arrow drawn as the Shift key's sign; the game font has no such glyph.
				final Path shift = new Path();
				shift.moveTo(90, 13);
				shift.lineTo(100, 24);
				shift.lineTo(94, 24);
				shift.lineTo(94, 33);
				shift.lineTo(86, 33);
				shift.lineTo(86, 24);
				shift.lineTo(80, 24);
				shift.close();
				stroke(MARK, 2.5f);
				canvas.drawPath(shift, this.paint);
				ring(canvas, 10);
				break;
			case TWO:
				ring(canvas, 9);
				stroke(MARK, 4);
				canvas.drawCircle(68, 30, 9, this.paint);
				arrow(canvas, 80, 106);
				break;
			case PINCH:
				stroke(MARK, 5);
				canvas.drawLine(30, 14, 44, 28, this.paint);
				canvas.drawLine(44, 18, 44, 28, this.paint);
				canvas.drawLine(44, 28, 34, 28, this.paint);
				canvas.drawLine(100, 14, 86, 28, this.paint);
				canvas.drawLine(86, 18, 86, 28, this.paint);
				canvas.drawLine(86, 28, 96, 28, this.paint);
				break;
			case MINIMAP:
				stroke(MARK, 4);
				canvas.drawRect(62, 4, 108, 50, this.paint);
				stroke(HAND, 3);
				canvas.drawRect(74, 16, 88, 26, this.paint);
				ring(canvas, 10);
				break;
			default:
				break;
			}
		}

		private void hand(final Canvas canvas) {
			stroke(HAND, 6);
			final Path finger = new Path();
			finger.moveTo(38, 92);
			finger.lineTo(38, 46);
			finger.quadTo(38, 38, 45, 38);
			finger.quadTo(52, 38, 52, 46);
			finger.lineTo(52, 70);
			canvas.drawPath(finger, this.paint);
			final Path palm = new Path();
			palm.moveTo(52, 64);
			palm.quadTo(52, 56, 59, 56);
			palm.quadTo(66, 56, 66, 64);
			palm.lineTo(66, 74);
			palm.moveTo(66, 66);
			palm.quadTo(66, 60, 72, 60);
			palm.quadTo(78, 60, 78, 68);
			palm.lineTo(78, 84);
			palm.quadTo(78, 100, 62, 104);
			palm.lineTo(46, 104);
			palm.quadTo(38, 102, 34, 94);
			palm.lineTo(24, 76);
			palm.quadTo(22, 70, 28, 68);
			palm.quadTo(33, 67, 38, 74);
			canvas.drawPath(palm, this.paint);
		}

		@Override
		public void setAlpha(final int alpha) {
			this.paint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(final ColorFilter colorFilter) {
			this.paint.setColorFilter(colorFilter);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}

	/** A colour code's colour, opaque and, where it would vanish on the launcher's dark pages, lightened. */
	private static int legible(final int colour) {
		int r = Color.red(colour);
		int g = Color.green(colour);
		int b = Color.blue(colour);
		if (((0.2126f * r) + (0.7152f * g) + (0.0722f * b)) < 90) {
			r += (255 - r) * 0.6f;
			g += (255 - g) * 0.6f;
			b += (255 - b) * 0.6f;
		}
		return Color.rgb(r, g, b);
	}

	/**
	 * Glue strings mark the shortcut letter with a colour code ("|CffffffffC|Rampaign"); the codes become colour spans
	 * and the rest of the text keeps the view's colour.
	 */
	static CharSequence glueText(final String value) {
		final SpannableStringBuilder text = new SpannableStringBuilder();
		for (final GlueText.Run run : GlueText.runs(value)) {
			final int start = text.length();
			text.append(run.text);
			if (run.colour != 0) {
				text.setSpan(new ForegroundColorSpan(legible(run.colour)), start, text.length(),
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
		return text;
	}
}
