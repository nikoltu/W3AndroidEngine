package com.etheller.warsmash.android;

import java.util.ArrayDeque;
import java.util.function.IntConsumer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;

/**
 * The launcher's modal boxes over the page (questions, messages and the open drop-down menu), which the
 * on-screen keyboard must not cover.
 */
final class LauncherDialogs extends LauncherViews {
	private static final long DIALOG_MILLIS = 120;
	/** A colour another slot has. */
	private static final float TAKEN_ALPHA = 0.28f;

	View dialog;
	/** The box of the open dialog and its name field, which the on-screen keyboard must not cover. */
	private View openBox;
	private View dialogField;
	/** Where the keyboard leaves no room for the whole dialog, these buttons take the place of the folded view. */
	View dialogFold;
	View[] dialogButtons;
	/** A dialog that only its own buttons close (the first profile). */
	boolean dialogLocked;
	private final ArrayDeque<Runnable> queuedQuestions = new ArrayDeque<>();

	LauncherDialogs(final LauncherActivity activity) {
		super(activity);
	}

	/** The open menu of a drop-down, under it or, near the bottom, over it; a player colour menu is a palette. */
	void showChoices(final View anchor, final String[] items, final int selected, final float textSize,
			final int[] colors, final boolean[] taken, final IntConsumer chosen) {
		if (this.dialog != null) {
			return;
		}
		if (colors != null) {
			showPalette(items, selected, colors, taken, chosen);
			return;
		}
		final LauncherBoard b = this.activity.board;
		final int[] at = new int[2];
		anchor.getLocationInWindow(at);
		final int[] origin = new int[2];
		this.activity.root.getLocationInWindow(origin);
		final float top = (at[1] - origin[1]) / this.activity.scale;
		final float w = anchor.getWidth() / this.activity.scale;
		final float x = Math.max(8, Math.min((at[0] - origin[0]) / this.activity.scale, b.width - 8 - w));
		final float anchorH = anchor.getHeight() / this.activity.scale;
		final float itemH = b.tablet ? Math.max(88, anchorH * 0.8f) : 96;
		final float edge = b.tablet ? 14 : 8;
		// A long menu scrolls within the screen.
		final float h = Math.min((items.length * itemH) + (2 * edge), b.height - 16);
		float y = top + anchorH;
		if ((y + h) > b.height) {
			y = Math.max(8, top - h);
		}
		if ((y + h) > b.height) {
			y = b.height - 8 - h;
		}
		final FrameLayout scrim = new FrameLayout(this.activity);
		scrim.setClickable(true);
		scrim.setOnClickListener(v -> closeDialog());
		final LinearLayout list = new LinearLayout(this.activity);
		list.setOrientation(LinearLayout.VERTICAL);
		list.setBackground(new LauncherKit.FrameDrawable(this.activity.art.bitmap(SkinArt.EDIT_BORDER), px(edge))
				.filled(Color.BLACK).fillInset(px(edge * 0.4f)).fallback(Color.rgb(0x03, 0x04, 0x0A))
				.line(this.activity.art.line()));
		list.setPadding(px(edge), px(edge), px(edge), px(edge));
		list.setClickable(true);
		for (int i = 0; i < items.length; i++) {
			final int choice = i;
			final TextView item = text(LauncherKit.glueText(items[i]), textSize,
					colors != null ? colors[i] : i == selected ? this.activity.art.gold() : this.activity.art.text());
			item.setGravity(Gravity.CENTER_VERTICAL);
			item.setPadding(px(b.tablet ? 16 : 10), 0, px(8), 0);
			markRow(item, i == selected);
			onTap(item, false, v -> {
				closeDialog();
				chosen.accept(choice);
			});
			list.addView(item, new LinearLayout.LayoutParams(MATCH, px(itemH)));
		}
		final ScrollView scroll = new ScrollView(this.activity);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.addView(list, new FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		place(scrim, scroll, x, y, w, h);
		this.activity.root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));
		this.dialog = scrim;
		if (selected > 0) {
			scroll.post(() -> scroll.scrollTo(0, px(selected * itemH) - ((scroll.getHeight() - px(itemH)) / 2)));
		}
	}

	/**
	 * The player colours as a grid of swatches in a box of their own, the chosen one ringed in gold. A colour another
	 * slot has is dimmed and cannot be chosen, but its name still shows on a long press.
	 *
	 * @param taken the colours of the other slots, or null when every colour is free
	 */
	private void showPalette(final String[] names, final int selected, final int[] colors, final boolean[] taken,
			final IntConsumer chosen) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float gap = t ? 20 : 14;
		final float pad = t ? 90 : 60;
		final int columns = Math.min(8, colors.length);
		final int rows = (colors.length + columns - 1) / columns;
		// Swatches stay at least a finger wide; a narrow window shrinks them before the box touches the screen edge.
		final float cell = Math.min(t ? 150 : 104, (b.width - 32 - (2 * pad) - ((columns - 1) * gap)) / columns);
		final float w = (columns * cell) + ((columns - 1) * gap) + (2 * pad);
		final float h = (rows * cell) + ((rows - 1) * gap) + (2 * pad);
		final FrameLayout box = dialogBox(w, Math.min(h, b.height - 32));
		if (box == null) {
			return;
		}
		for (int i = 0; i < colors.length; i++) {
			final int choice = i;
			final GradientDrawable fill = new GradientDrawable();
			fill.setColor(colors[i]);
			fill.setStroke(px(i == selected ? 7 : 2), i == selected ? this.activity.art.gold() : Color.argb(0xB0, 0, 0, 0));
			final View swatch = new View(this.activity);
			swatch.setBackground(fill);
			final CharSequence name = LauncherKit.glueText(names[i]);
			swatch.setContentDescription(name);
			swatch.setTooltipText(name);
			if ((taken != null) && taken[i] && (i != selected)) {
				swatch.setAlpha(TAKEN_ALPHA);
				swatch.setLongClickable(true);
			}
			else {
				onTap(swatch, false, v -> {
					closeDialog();
					chosen.accept(choice);
				});
			}
			place(box, swatch, pad + ((i % columns) * (cell + gap)), pad + ((i / columns) * (cell + gap)), cell, cell);
		}
	}

	void showKeyboard(final EditText field) {
		field.requestFocus();
		field.post(() -> {
			final InputMethodManager input = (InputMethodManager) this.activity
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (input != null) {
				input.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
			}
		});
	}

	void hideKeyboard(final View view) {
		final InputMethodManager input = (InputMethodManager) this.activity
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		if (input != null) {
			input.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}

	/** A modal box in the skin's dialog frame over a dark scrim; null while another dialog is open. */
	FrameLayout dialogBox(final float w, final float h) {
		if (this.dialog != null) {
			return null;
		}
		final LauncherBoard b = this.activity.board;
		final FrameLayout scrim = new FrameLayout(this.activity);
		scrim.setBackgroundColor(Color.argb(0xAB, 0, 0, 0));
		scrim.setClickable(true);
		scrim.setOnClickListener(v -> {
			if (!this.dialogLocked) {
				closeDialog();
			}
		});
		final FrameLayout box = new FrameLayout(this.activity);
		box.setClickable(true);
		final float edge = b.tablet ? 48 : 32;
		box.setBackground(new LauncherKit.FrameDrawable(this.activity.art.bitmap(SkinArt.DIALOG_BORDER), px(edge))
				.tiled(this.activity.art.bitmap(SkinArt.DIALOG_BACKGROUND),
				px(b.tablet ? 256 : 128)).fillInset(px(edge * 0.4f))
				.fallback(Color.argb(0xEB, 0x03, 0x04, 0x0A)).line(this.activity.art.line()));
		place(scrim, box, (b.width - w) / 2, (b.height - h) / 2, w, h);
		this.activity.root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));
		if (ValueAnimator.areAnimatorsEnabled()) {
			scrim.setAlpha(0);
			box.setScaleX(0.94f);
			box.setScaleY(0.94f);
			scrim.animate().alpha(1).setDuration(DIALOG_MILLIS);
			box.animate().scaleX(1).scaleY(1).setDuration(DIALOG_MILLIS);
		}
		this.dialog = scrim;
		this.openBox = box;
		return box;
	}

	void showMessage(final CharSequence title, final String line) {
		showQuestion(title, line, new CharSequence[] { glueLabel("OK", getString(R.string.game_ok)) },
				new Runnable[] { null });
	}

	/** A question or a message with its buttons side by side; every button closes the dialog. */
	void showQuestion(final CharSequence title, final String line, final CharSequence[] labels,
			final Runnable[] actions) {
		showQuestion(title, line, labels, actions, null);
	}

	/** With an icon, the question stands beside it, as the glue dialogs that ask about a profile have it. */
	void showQuestion(final CharSequence title, final String line, final CharSequence[] labels,
			final Runnable[] actions, final Bitmap icon) {
		showQuestion(title, line, labels, actions, icon, 3, false);
	}

	/** A question with a long explanation: the box is taller and the lines of the explanation are not cut at three. */
	void showQuestion(final CharSequence title, final String line, final CharSequence[] labels,
			final Runnable[] actions, final Bitmap icon, final int detailLines, final boolean tall) {
		if (this.dialog != null) {
			// Dropped silently, its buttons would never run; it opens when the open dialog closes.
			this.queuedQuestions.add(() -> showQuestion(title, line, labels, actions, icon, detailLines, tall));
			return;
		}
		final LauncherBoard b = this.activity.board;
		final float w = b.tablet ? 1200 : 900;
		final float h = tall ? (b.tablet ? 860 : 640) : (b.tablet ? 620 : 440);
		final FrameLayout box = dialogBox(w, h);
		if (box == null) {
			return;
		}
		final float inset = b.tablet ? 80 : 56;
		float textX = inset;
		if (icon != null) {
			final float side = b.tablet ? 160 : 112;
			final ImageView mark = new ImageView(this.activity);
			mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
			mark.setImageBitmap(icon);
			place(box, mark, inset, b.tablet ? 90 : 56, side, side);
			textX = inset + side + (b.tablet ? 40 : 24);
		}
		final int gravity = icon != null ? Gravity.START : Gravity.CENTER;
		final LinearLayout texts = new LinearLayout(this.activity);
		texts.setOrientation(LinearLayout.VERTICAL);
		final TextView question = lines(title, icon != null ? b.body * 1.25f : b.title, this.activity.art.text(),
				icon != null ? 3 : 2);
		question.setGravity(gravity);
		texts.addView(question, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		if ((line != null) && !line.isEmpty()) {
			final TextView detail = lines(line, b.body, this.activity.art.text2(), detailLines);
			detail.setGravity(gravity);
			final LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(MATCH,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			detailParams.topMargin = px(b.tablet ? 32 : 20);
			texts.addView(detail, detailParams);
		}
		place(box, texts, textX, b.tablet ? 100 : 64, w - inset - textX, -1);
		final float gap = b.tablet ? 32 : 24;
		final float buttonW = Math.min(b.tablet ? 480 : 360,
				(w - (2 * inset) - ((labels.length - 1) * gap)) / labels.length);
		final float rowW = (labels.length * buttonW) + ((labels.length - 1) * gap);
		final float buttonY = h - inset - b.buttonH;
		for (int i = 0; i < labels.length; i++) {
			final Runnable action = actions[i];
			place(box, button(labels[i], b.buttonH, b.buttonText, v -> {
				closeDialog();
				if (action != null) {
					action.run();
				}
			}), ((w - rowW) / 2) + (i * (buttonW + gap)), buttonY, buttonW, b.buttonH);
		}
	}

	void showExit() {
		showQuestion(getString(R.string.launcher_exit), null,
				new CharSequence[] { glueLabel("NO", getString(R.string.game_no)),
					glueLabel("YES", getString(R.string.game_yes)) },
				new Runnable[] { null, this.activity::finish }, this.activity.art.bitmap(SkinArt.QUESTION));
	}

	void closeDialog() {
		if (this.dialog != null) {
			hideKeyboard(this.dialog);
			this.activity.root.removeView(this.dialog);
			this.dialog = null;
		}
		if (this.dialogField != null) {
			this.activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
		}
		this.openBox = null;
		this.dialogField = null;
		this.dialogFold = null;
		this.dialogButtons = null;
		this.dialogLocked = false;
		final Runnable next = this.queuedQuestions.poll();
		if (next != null) {
			this.activity.root.post(() -> {
				if (!this.activity.isDestroyed()) {
					next.run();
				}
			});
		}
	}

	/**
	 * Panning only keeps the focused field in sight, so the keyboard would cover the dialog's buttons below it. From
	 * Android 11 the dialog moves up by itself as far as the keyboard needs; before that the keyboard's height cannot be
	 * read without panning, so the dialog stands at the top of the screen.
	 */
	void typeInDialog(final View field) {
		this.dialogField = field;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			this.activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
		}
		else if (this.openBox != null) {
			final View box = this.openBox;
			box.post(() -> box.setTranslationY(px(this.activity.board.tablet ? 32 : 12) - box.getTop()));
		}
	}

	void trackOnscreenKeyboard() {
		final View decor = this.activity.getWindow().getDecorView();
		decor.setOnApplyWindowInsetsListener((view, insets) -> {
			liftDialog(insets);
			return view.onApplyWindowInsets(insets);
		});
	}

	private void liftDialog(final WindowInsets insets) {
		if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R) || (this.openBox == null) || (this.dialogField == null)) {
			return;
		}
		final View box = this.openBox;
		final int covered = insets.isVisible(WindowInsets.Type.ime()) ? insets.getInsets(WindowInsets.Type.ime()).bottom
				: 0;
		final View decor = this.activity.getWindow().getDecorView();
		final int[] at = new int[2];
		decor.getLocationOnScreen(at);
		final int keyboardTop = (at[1] + decor.getHeight()) - covered;
		box.getLocationOnScreen(at);
		final int top = at[1] - Math.round(box.getTranslationY());
		final int margin = px(this.activity.board.tablet ? 32 : 12);
		// The field itself stays on the screen even where the buttons cannot fit above the keyboard too.
		final int maxLift = Math.max(0, (top + this.dialogField.getTop()) - margin);
		int bottom = top + box.getHeight();
		final boolean fold = (covered > 0) && (this.dialogFold != null)
				&& (((bottom + margin) - keyboardTop) > maxLift);
		if (this.dialogFold != null) {
			this.dialogFold.setVisibility(fold ? View.INVISIBLE : View.VISIBLE);
			for (final View button : this.dialogButtons) {
				button.setTranslationY(fold ? this.dialogFold.getTop() - button.getTop() : 0);
				if (fold) {
					bottom = top + this.dialogFold.getTop() + button.getHeight();
				}
			}
		}
		final int lift = covered > 0 ? Math.max(0, Math.min((bottom + margin) - keyboardTop, maxLift)) : 0;
		box.animate().translationY(-lift).setDuration(150).start();
	}
}
