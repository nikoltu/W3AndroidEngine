package com.etheller.warsmash.android;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.MapIssue;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.SmallCaps;

/**
 * The views the launcher's pages are made of: placed in the design's pixels of the activity's board and drawn
 * with the skin's art it prepared.
 */
abstract class LauncherViews {
	static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
	static final int ERROR = Color.rgb(0xE0, 0x56, 0x4E);

	final LauncherActivity activity;

	LauncherViews(final LauncherActivity activity) {
		this.activity = activity;
	}

	String getString(final int id) {
		return this.activity.getString(id);
	}

	String getString(final int id, final Object... formatArgs) {
		return this.activity.getString(id, formatArgs);
	}

	/** What a map's issue is told with: the sentence, or the few words that stand beside the map in a list. */
	String issueText(final MapIssue issue, final boolean tag) {
		final String name = tag ? issue.tag : issue.message;
		final int id = this.activity.getResources().getIdentifier(name, "string", this.activity.getPackageName());
		return id != 0 ? this.activity.getString(id) : issue.english;
	}

	int px(final float design) {
		return this.activity.px(design);
	}

	/** A tappable view that clicks like the glue buttons: big for the main menu choices, the mouse click else. */
	static void onTap(final View view, final boolean main, final View.OnClickListener listener) {
		view.setOnClickListener(v -> {
			if (LauncherKit.clicks != null) {
				LauncherKit.clicks.play(main);
			}
			listener.onClick(v);
		});
	}

	void place(final FrameLayout parent, final View view, final float x, final float y, final float w,
			final float h) {
		final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				w < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : px(w), h < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : px(h));
		params.leftMargin = px(x);
		params.topMargin = px(y);
		parent.addView(view, params);
	}

	TextView text(final CharSequence value, final float size, final int color) {
		final TextView view = new TextView(this.activity);
		view.setText(value);
		view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * this.activity.scale);
		view.setTextColor(color);
		view.setTypeface(this.activity.art != null ? this.activity.art.font() : null);
		view.setIncludeFontPadding(false);
		view.setSingleLine(true);
		view.setEllipsize(TextUtils.TruncateAt.END);
		view.setShadowLayer(1, size * this.activity.scale * 0.07f, size * this.activity.scale * 0.07f, Color.BLACK);
		return view;
	}

	/**
	 * A text wider than its box shrinks, down to the given part of its size, before it is cut with an ellipsis. The
	 * width is only known once the box is laid out, so the size follows the layout.
	 */
	TextView fitWidth(final TextView view, final float smallest) {
		final float full = view.getTextSize();
		view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
			final int room = right - left - view.getPaddingLeft() - view.getPaddingRight();
			final CharSequence shown = view.getText();
			if ((room <= 0) || (shown.length() == 0)) {
				return;
			}
			final TextPaint paint = new TextPaint(view.getPaint());
			paint.setTextSize(full);
			final float wide = paint.measureText(shown, 0, shown.length());
			final float size = wide <= room ? full : Math.max(full * smallest, full * room / wide);
			if (Math.abs(size - view.getTextSize()) > 0.5f) {
				view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
			}
		});
		return view;
	}

	/** The text in MasterFontBold, as the Reforged interface sets its headings and names. */
	TextView boldText(final CharSequence value, final float size, final int color) {
		final TextView view = text(value, size, color);
		view.setTypeface(this.activity.art != null ? this.activity.art.fontBold() : null);
		return view;
	}

	/** The text in small capitals: every small letter a capital of {@link SmallCaps#SCALE} of the size. */
	static CharSequence smallCaps(final CharSequence value) {
		final StringBuilder capitals = new StringBuilder();
		final List<SmallCaps.Run> runs = SmallCaps.runs(value.toString());
		for (final SmallCaps.Run run : runs) {
			capitals.append(run.text);
		}
		final SpannableString spanned = new SpannableString(capitals);
		int at = 0;
		for (final SmallCaps.Run run : runs) {
			if (run.small) {
				spanned.setSpan(new RelativeSizeSpan(SmallCaps.SCALE), at, at + run.text.length(),
						Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			at += run.text.length();
		}
		return spanned;
	}

	/** A few lines of text that wrap within their width. */
	TextView lines(final CharSequence value, final float size, final int color, final int maxLines) {
		final TextView view = text(value, size, color);
		view.setSingleLine(false);
		view.setMaxLines(maxLines);
		view.setLineSpacing(0, 1.15f);
		return view;
	}

	LauncherKit.GlueButton button(final CharSequence label, final float height, final float textSize,
			final View.OnClickListener listener) {
		final LauncherKit.GlueButton button = new LauncherKit.GlueButton(this.activity, this.activity.art, px(height),
				textSize * this.activity.scale);
		button.setText(label);
		button.setOnClickListener(listener);
		return button;
	}

	String glue(final String key, final String fallback) {
		return this.activity.art.string(key, fallback);
	}

	/** The game data's text for the key; when it has none, the launcher's own text in the game language. */
	String glue(final String key) {
		return glue(key, this.activity.glueFallback(key));
	}

	/** A vertical or horizontal gradient with stops, which GradientDrawable cannot place. */
	static Drawable gradient(final boolean vertical, final int[] colors, final float[] stops) {
		final PaintDrawable drawable = new PaintDrawable();
		drawable.setShape(new RectShape());
		drawable.setShaderFactory(new ShapeDrawable.ShaderFactory() {
			@Override
			public Shader resize(final int width, final int height) {
				return new LinearGradient(0, 0, vertical ? 0 : width, vertical ? height : 0, colors, stops,
						Shader.TileMode.CLAMP);
			}
		});
		return drawable;
	}

	Drawable panelFrame(final float border, final int fill) {
		return new LauncherKit.FrameDrawable(this.activity.art.bitmap(SkinArt.PANEL_BORDER), px(border)).filled(fill)
				.fillInset(px(border * 0.35f)).fallback(Color.argb(0xEB, 0x03, 0x04, 0x0A))
				.line(this.activity.art.line());
	}

	FrameLayout.LayoutParams frameParams(final float x, final float y, final float w, final float h) {
		final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(w), px(h));
		params.leftMargin = px(x);
		params.topMargin = px(y);
		return params;
	}

	/** A row of tab buttons over the page content, as wide as six would be; the chosen one stays pressed. */
	float addTabs(final FrameLayout content, final int[] labels, final int selected,
			final IntConsumer open) {
		final LauncherBoard b = this.activity.board;
		final int slots = Math.max(6, labels.length);
		final float tabW = (b.contentW - ((slots - 1) * b.tabGap)) / slots;
		for (int i = 0; i < labels.length; i++) {
			final int tab = i;
			final LauncherKit.GlueButton button = button(getString(labels[i]), b.tabH, b.tabText,
					v -> open.accept(tab));
			button.setSelected(i == selected);
			place(content, button, i * (tabW + b.tabGap), 0, tabW, b.tabH);
		}
		return b.tabH + (b.tablet ? 40 : 14);
	}

	FrameLayout content(final FrameLayout view, final float extraHeight) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout content = new FrameLayout(this.activity);
		place(view, content, b.contentX, b.contentY, b.contentW, b.contentH + extraHeight);
		return content;
	}

	/** The page's frame while the catalog reads what the page lists. */
	int loadingPage(final int rail, final int kind) {
		final FrameLayout view = this.activity.homePage.shell(rail);
		final FrameLayout content = content(view, 0);
		this.activity.pageNote = text(getString(R.string.game_loading_list), this.activity.board.body,
				this.activity.art.text2());
		place(content, this.activity.pageNote, 0, 0, this.activity.board.contentW, -1);
		this.activity.setPage(view, kind);
		return this.activity.generation;
	}

	void unreadable() {
		if (this.activity.pageNote != null) {
			this.activity.pageNote.setTextColor(ERROR);
			this.activity.pageNote.setText(R.string.game_unreadable);
		}
	}

	/** A panel in the skin's frame at a place of the content. */
	FrameLayout framed(final FrameLayout parent, final float x, final float y, final float w, final float h) {
		final FrameLayout panel = new FrameLayout(this.activity);
		panel.setBackground(panelFrame(this.activity.board.pageFrame,
				this.activity.art.reforged() ? Color.argb(0xDB, 0x0E, 0x0C, 0x0A) : Color.argb(0xC7, 0, 0, 0)));
		place(parent, panel, x, y, w, h);
		return panel;
	}

	/** The edit box frame of the difficulty box and the drop-down menus. */
	Drawable editFrame(final float border) {
		final LauncherKit.FrameDrawable frame = new LauncherKit.FrameDrawable(
				this.activity.art.bitmap(SkinArt.EDIT_BORDER), px(border))
						.tiled(this.activity.art.bitmap(SkinArt.EDIT_BACKGROUND),
								px(this.activity.board.tablet ? 110 : 60))
						.fillInset(px(border * 0.4f)).fallback(Color.argb(0xCC, 0x05, 0x06, 0x0C))
						.line(this.activity.art.line());
		return this.activity.art.reforged() ? frame.centre() : frame;
	}

	LinearLayout scrollList(final FrameLayout parent, final float x, final float y, final float w,
			final float h) {
		final ScrollView scroll = new ScrollView(this.activity);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.setVerticalScrollBarEnabled(true);
		final LinearLayout rows = new LinearLayout(this.activity);
		rows.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(rows, new FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		place(parent, scroll, x, y, w, h);
		return rows;
	}

	/** Scrolls a list so that its chosen row is in view. */
	static void reveal(final View row) {
		row.post(() -> {
			final ScrollView scroll = (ScrollView) row.getParent().getParent();
			final int visible = scroll.getHeight();
			if ((row.getTop() < scroll.getScrollY()) || ((row.getBottom()) > (scroll.getScrollY() + visible))) {
				scroll.scrollTo(0, Math.max(0, row.getTop() - ((visible - row.getHeight()) / 2)));
			}
		});
	}

	/** The chosen row of a list carries the skin's row highlight. */
	void markRow(final View row, final boolean chosen) {
		if (!chosen) {
			row.setBackground(null);
			return;
		}
		final Bitmap glow = this.activity.art.bitmap(SkinArt.ROW_HIGHLIGHT);
		row.setBackground(glow != null ? new BitmapDrawable(this.activity.getResources(), glow)
				: new ColorDrawable(Color.argb(0x40, 0xFC, 0xD3, 0x12)));
	}

	/** The heading of a section of a map list: the folder's name in gold, not tappable. */
	View headingRow(final String title, final float rowH) {
		final LinearLayout row = new LinearLayout(this.activity);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(px(6), 0, px(8), 0);
		final TextView view = fitWidth(text(title, Math.min(rowH * 0.86f, 84) * 0.46f, this.activity.art.gold()), 0.7f);
		final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		params.leftMargin = px(18);
		row.addView(view, params);
		row.setContentDescription(title);
		return row;
	}

	ImageView image(final String name, final ImageView.ScaleType scaleType) {
		final ImageView view = new ImageView(this.activity);
		view.setScaleType(scaleType);
		view.setImageBitmap(this.activity.art.bitmap(name));
		return view;
	}

	/** The action bar: 0 is Back, 1 the secondary place, 2 the main action. */
	LauncherKit.GlueButton action(final FrameLayout view, final int slot, final CharSequence label,
			final View.OnClickListener listener) {
		final LauncherBoard b = this.activity.board;
		final float[] place = b.actionSlot(slot);
		final float x = place[0];
		final float w = place[1];
		final LauncherKit.GlueButton button = button(label, b.backH, b.tablet ? 36 : 30, listener);
		button.setMain(slot == 2);
		if (slot == 2) {
			button.setTone(LauncherKit.GlueButton.TONE_CONFIRM);
		}
		place(view, button, x, b.backY, w, b.backH);
		return button;
	}

	CharSequence glueLabel(final String key, final String fallback) {
		return LauncherKit.glueText(glue(key, fallback));
	}

	CharSequence glueLabel(final String key) {
		return LauncherKit.glueText(glue(key));
	}

	String[] glueList(final String[][] table, final int keyColumn) {
		final String[] labels = new String[table.length];
		for (int i = 0; i < table.length; i++) {
			labels[i] = glue(table[i][keyColumn]);
		}
		return labels;
	}

	/** A drop-down menu as the glue screens have it: the glue button, the chosen item and the pull-down arrow. */
	View dropdown(final String[] items, final int selected, final boolean enabled, final float height,
			final float textSize, final IntConsumer chosen) {
		return dropdown(items, selected, enabled, height, textSize, chosen, null);
	}

	/** @param colors with them the menu shows a colour (a player colour menu), its items named in their colour */
	View dropdown(final String[] items, final int selected, final boolean enabled, final float height,
			final float textSize, final IntConsumer chosen, final int[] colors) {
		final FrameLayout box = new FrameLayout(this.activity);
		box.setBackground(LauncherKit.GlueButton.backdrop(this.activity.art, px(height),
				LauncherKit.GlueButton.TONE_PLAIN));
		// Inside the button's rim, as the button's own text sits.
		final float rim = this.activity.art.reforged() ? Math.max(8, height * 0.3f) : Math.max(4, height * 0.22f);
		final float arrowBox = Math.min(height, 72);
		final float arrowSize = arrowBox * 0.42f;
		final TextView label = text(LauncherKit.glueText(items[selected]), textSize,
				enabled ? LauncherKit.GlueButton.restText(this.activity.art) : LauncherKit.DISABLED);
		label.setGravity(Gravity.CENTER_VERTICAL);
		// A longer item shrinks to the button rather than being cut.
		final int fullSize = Math.round(textSize * this.activity.scale);
		label.setAutoSizeTextTypeUniformWithConfiguration(Math.max(1, Math.round(fullSize * 0.55f)), fullSize, 1,
				TypedValue.COMPLEX_UNIT_PX);
		final FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(MATCH, MATCH);
		labelParams.leftMargin = px(rim + 4);
		labelParams.rightMargin = px(Math.max(rim + 4, arrowBox * 0.8f));
		box.addView(label, labelParams);
		final View swatch = colors != null ? new View(this.activity) : null;
		if (swatch != null) {
			// The colour menu shows only the colour, in the middle of the button, without the arrow.
			label.setVisibility(View.GONE);
			swatch.setBackgroundColor(colors[selected]);
			swatch.setAlpha(enabled ? 1f : 0.5f);
			// Sized from the button, not its height: a narrow column (the LAN lobby's) keeps the colour inside the rim.
			final FrameLayout.LayoutParams swatchParams = new FrameLayout.LayoutParams(MATCH,
					px(Math.max(8, (height - (2 * rim)) * 0.55f)), Gravity.CENTER);
			final float inset = Math.max(4, rim * 0.6f);
			swatchParams.leftMargin = px(inset);
			swatchParams.rightMargin = px(inset);
			box.addView(swatch, swatchParams);
		}
		else {
			// The Reforged menu has its own gold chevron, with a disabled picture instead of a faded one.
			final boolean chevron = this.activity.art.reforged() && this.activity.art.has(SkinArt.CHEVRON_DOWN);
			final ImageView arrow = image(!chevron ? SkinArt.PULLDOWN_ARROW
					: enabled || !this.activity.art.has(SkinArt.CHEVRON_DOWN_DISABLED) ? SkinArt.CHEVRON_DOWN
						: SkinArt.CHEVRON_DOWN_DISABLED, ImageView.ScaleType.FIT_CENTER);
			arrow.setAlpha(enabled || chevron ? 1f : 0.4f);
			final FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(px(arrowSize), px(arrowSize),
					Gravity.END | Gravity.CENTER_VERTICAL);
			arrowParams.rightMargin = px((arrowBox * 0.75f) - arrowSize);
			box.addView(arrow, arrowParams);
		}
		box.setEnabled(enabled);
		box.setContentDescription(LauncherKit.glueText(items[selected]).toString());
		// A long item that the button had to shrink is read in full on a long press.
		box.setTooltipText(LauncherKit.glueText(items[selected]));
		if (enabled && (chosen != null)) {
			final int[] current = { selected };
			onTap(box, false,
					v -> this.activity.dialogs.showChoices(box, items, current[0], textSize, colors, choice -> {
						current[0] = choice;
						label.setText(LauncherKit.glueText(items[choice]));
						if (swatch != null) {
							swatch.setBackgroundColor(colors[choice]);
						}
						box.setContentDescription(LauncherKit.glueText(items[choice]).toString());
						box.setTooltipText(LauncherKit.glueText(items[choice]));
						chosen.accept(choice);
					}));
		}
		return box;
	}

	/** A check box row: the whole row is the touch target. */
	View checkLine(final CharSequence label, final boolean value, final float height, final float size,
			final Consumer<Boolean> changed) {
		final LinearLayout row = new LinearLayout(this.activity);
		row.setGravity(Gravity.CENTER_VERTICAL);
		final LauncherKit.CheckDrawable check = new LauncherKit.CheckDrawable(this.activity.art);
		check.setChecked(value);
		final View box = new View(this.activity);
		box.setBackground(check);
		final float boxSize = Math.min(height * 0.6f, this.activity.board.control);
		row.addView(box, new LinearLayout.LayoutParams(px(boxSize), px(boxSize)));
		final TextView name = text(label, size, this.activity.art.text());
		final LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		nameParams.leftMargin = px(this.activity.board.tablet ? 18 : 12);
		row.addView(name, nameParams);
		final boolean[] on = { value };
		onTap(row, false, v -> {
			on[0] = !on[0];
			check.setChecked(on[0]);
			changed.accept(on[0]);
		});
		row.setContentDescription(label.toString());
		return row;
	}

	/** The minimap's frame: a black border with a grey line round it. */
	Drawable minimapFrame(final float border) {
		final GradientDrawable frame = new GradientDrawable();
		frame.setColor(Color.BLACK);
		frame.setStroke(Math.max(1, px(3)), Color.rgb(0x55, 0x55, 0x55));
		return frame;
	}

	/** A fact of Map Info: the gold label on the left, its value on the right; returns the value. */
	TextView fact(final FrameLayout parent, final String label, final String value, final float x,
			final float y, final float w, final float size) {
		place(parent, text(LauncherKit.glueText(label), size, this.activity.art.gold()), x, y, w * 0.5f, -1);
		final TextView shown = text(value, size, this.activity.art.text());
		shown.setGravity(Gravity.END);
		place(parent, shown, x + (w * 0.4f), y, w * 0.6f, -1);
		return shown;
	}

	/** The original edit box with a profile name: one line, at most the name's length. */
	EditText nameField(final float size) {
		final EditText field = new EditText(this.activity);
		field.setBackground(editFrame(this.activity.board.tablet ? 14 : 8));
		field.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * this.activity.scale);
		field.setTextColor(this.activity.art.text());
		field.setHintTextColor(LauncherKit.DISABLED);
		field.setHint(glue("NAME", getString(R.string.profile_name)));
		field.setTypeface(this.activity.art.font());
		field.setSingleLine(true);
		field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		field.setFilters(new InputFilter[] { new InputFilter.LengthFilter(Profiles.MAX_NAME) });
		final int pad = px(this.activity.board.tablet ? 24 : 14);
		field.setPadding(pad, 0, pad, 0);
		field.setGravity(Gravity.CENTER_VERTICAL);
		return field;
	}

	/** Runs the check given after every change of the field's text. */
	static void afterTextChanged(final EditText field, final Runnable check) {
		field.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(final CharSequence text, final int start, final int count, final int after) {
			}

			@Override
			public void onTextChanged(final CharSequence text, final int start, final int before, final int count) {
			}

			@Override
			public void afterTextChanged(final Editable text) {
				check.run();
			}
		});
	}

	/** The keyboard's Done taps the confirming button while it is enabled. */
	static void doneTaps(final EditText field, final View confirm) {
		field.setOnEditorActionListener((v, action, event) -> {
			if ((action == EditorInfo.IME_ACTION_DONE) && confirm.isEnabled()) {
				confirm.performClick();
			}
			return false;
		});
	}

	private static final long HINT_MILLIS = 3000;
	private TextView hint;

	/** A short tip beside a view, which goes by itself; long-press or tap on a mark shows it. */
	void showHint(final View anchor, final String note) {
		hideHint();
		final LauncherBoard b = this.activity.board;
		final int[] at = new int[2];
		anchor.getLocationInWindow(at);
		final int[] origin = new int[2];
		this.activity.root.getLocationInWindow(origin);
		// The tip stays inside the panel the choice is in.
		View panel = anchor;
		while ((panel.getParent() instanceof View) && !(panel instanceof ScrollView)) {
			panel = (View) panel.getParent();
		}
		final int[] area = new int[2];
		panel.getLocationInWindow(area);
		final int left = area[0] - origin[0];
		final int top = area[1] - origin[1];
		final int width = panel instanceof ScrollView ? panel.getWidth() : px(b.width);
		final int height = panel instanceof ScrollView ? panel.getHeight() : px(b.height);
		final int border = px(b.tablet ? 8 : 6);
		final TextView tip = text(note, b.small, Color.WHITE);
		tip.setSingleLine(false);
		tip.setEllipsize(null);
		tip.setBackground(panelFrame(b.tablet ? 8 : 6, Color.rgb(0x08, 0x08, 0x10)));
		tip.setPadding(border + px(12), border + px(8), border + px(12), border + px(8));
		final int maxWidth = Math.min(px(b.tablet ? 900 : 700), width);
		tip.setMaxWidth(maxWidth);
		tip.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		params.leftMargin = Math.max(left, Math.min(at[0] - origin[0] - px(8), left + width - tip.getMeasuredWidth()));
		final int below = at[1] - origin[1] + anchor.getHeight() + px(6);
		// Above the choice when there is no room below it, so the tip never covers the choice.
		params.topMargin = below + tip.getMeasuredHeight() <= top + height ? below
				: Math.max(top, at[1] - origin[1] - px(6) - tip.getMeasuredHeight());
		this.activity.root.addView(tip, params);
		this.hint = tip;
		tip.postDelayed(this::hideHint, HINT_MILLIS);
	}

	void hideHint() {
		if (this.hint != null) {
			this.activity.root.removeView(this.hint);
			this.hint = null;
		}
	}
}
