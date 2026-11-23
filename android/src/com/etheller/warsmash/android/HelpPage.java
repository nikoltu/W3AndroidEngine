package com.etheller.warsmash.android;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.etheller.warsmash.input.touch.TouchStyle;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;

/** The launcher's own Help: the touch gestures, how to get the game data and the usual problems. */
final class HelpPage extends LauncherViews {
	int helpTab;

	HelpPage(final LauncherActivity activity) {
		super(activity);
	}

	void showHelp(final int topic) {
		this.helpTab = topic;
		final LauncherBoard b = this.activity.board;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_HELP);
		// Help has no action bar; on the phone its panel takes that space too.
		final float extra = b.tablet ? 0 : b.height - 16 - b.contentY - b.contentH;
		final FrameLayout content = content(view, extra);
		final float top = addTabs(content, new int[] { R.string.help_touch, R.string.help_game_data,
			R.string.help_problems }, topic, this::showHelp);
		final float bottom = b.contentH + extra;
		final FrameLayout panel = framed(content, 0, top, b.contentW, bottom - top);
		final float frame = b.tablet ? 44 : 18;
		final float x = frame + (b.tablet ? 20 : 6);
		final float w = b.contentW - (2 * x);
		final float h = bottom - top;
		if (topic == 0) {
			gestureCards(panel, x, frame, w, h - (2 * frame));
		}
		else if (topic == 1) {
			numberedRows(panel, x, frame, w, h - (2 * frame), R.string.help_data_title,
					new int[][] { { R.string.help_step_pc, R.string.help_step_pc_line },
						{ R.string.help_step_zip, R.string.help_step_zip_line },
						{ R.string.help_step_folder, R.string.help_step_folder_line } },
					new LauncherKit.Pictogram.Kind[] { LauncherKit.Pictogram.Kind.COMPUTER,
						LauncherKit.Pictogram.Kind.ZIP, LauncherKit.Pictogram.Kind.FOLDER });
		}
		else {
			numberedRows(panel, x, frame, w, h - (2 * frame), R.string.help_problems_title,
					new int[][] { { R.string.help_problem_data, R.string.help_problem_data_line },
						{ R.string.help_problem_zip, R.string.help_problem_zip_line },
						{ R.string.help_problem_save, R.string.help_problem_save_line },
						{ R.string.help_problem_closed, R.string.help_problem_closed_line },
						{ R.string.help_problem_backgrounds, R.string.help_problem_backgrounds_line } },
					null);
		}
		this.activity.setPage(view, LauncherActivity.PAGE_HELP);
	}

	/** One card per gesture in a 4 x 2 grid: the pictogram, the gesture's name and what it does. */
	private void gestureCards(final FrameLayout panel, final float x, final float y, final float w, final float h) {
		final LauncherBoard b = this.activity.board;
		final boolean doubleTap = new EngineSettings(this.activity).touchStyle() == TouchStyle.DOUBLE_TAP;
		final Object[][] gestures = { { LauncherKit.Pictogram.Kind.TAP, R.string.help_tap, R.string.help_tap_line },
			{ LauncherKit.Pictogram.Kind.DRAG, R.string.help_drag, R.string.help_drag_line },
			doubleTap ? new Object[] { LauncherKit.Pictogram.Kind.DOUBLE, R.string.help_double, R.string.help_double_line }
					: new Object[] { LauncherKit.Pictogram.Kind.HOLD, R.string.help_hold, R.string.help_hold_line },
			{ LauncherKit.Pictogram.Kind.SHIFT, R.string.help_shift, R.string.help_shift_line },
			{ LauncherKit.Pictogram.Kind.TWO, R.string.help_two, R.string.help_two_line },
			{ LauncherKit.Pictogram.Kind.PINCH, R.string.help_pinch, R.string.help_pinch_line },
			{ LauncherKit.Pictogram.Kind.MINIMAP, R.string.help_minimap, R.string.help_minimap_line },
			{ LauncherKit.Pictogram.Kind.HOLD, R.string.help_skip, R.string.help_skip_line } };
		float top = y + (b.tablet ? 4 : 4);
		if (b.tablet) {
			place(panel, text(getString(R.string.help_touch_title), b.title, this.activity.art.text()), x, y, w, -1);
			top = y + 96;
		}
		final int columns = 4;
		final float gap = b.tablet ? 24 : 10;
		final float cardW = (w - ((columns - 1) * gap)) / columns;
		final float cardH = ((y + h) - top - gap) / 2;
		final float nameSize = b.tablet ? 36 : 24;
		final float lineSize = b.tablet ? 28 : 20;
		for (int i = 0; i < gestures.length; i++) {
			final float cx = x + ((i % columns) * (cardW + gap));
			final float cy = top + ((i / columns) * (cardH + gap));
			final FrameLayout card = new FrameLayout(this.activity);
			final LauncherKit.FrameDrawable border = new LauncherKit.FrameDrawable(
					this.activity.art.bitmap(SkinArt.EDIT_BORDER), px(b.tablet ? 14 : 8))
							.tiled(this.activity.art.bitmap(SkinArt.EDIT_BACKGROUND), px(b.tablet ? 110 : 60))
							.fallback(Color.argb(0xCC, 0x05, 0x06, 0x0C)).line(this.activity.art.line());
			// The Reforged text-field art is made for a one-line box; stretched over a card its bottom edge is a bare strip.
			card.setBackground(this.activity.art.reforged() ? panelFrame(b.tablet ? 8 : 6, Color.argb(0xDB, 0x0E, 0x0C, 0x0A))
					: border);
			place(panel, card, cx, cy, cardW, cardH);
			final float icon = Math.min(cardH * 0.5f, cardW * 0.6f);
			final View picture = new View(this.activity);
			picture.setBackground(new LauncherKit.Pictogram((LauncherKit.Pictogram.Kind) gestures[i][0],
					this.activity.art.font()));
			place(card, picture, (cardW - icon) / 2, b.tablet ? 24 : 6, icon, icon);
			final TextView name = text(getString((Integer) gestures[i][1]), nameSize, this.activity.art.gold());
			name.setGravity(Gravity.CENTER_HORIZONTAL);
			place(card, name, 8, icon + (b.tablet ? 34 : 10), cardW - 16, -1);
			final TextView line = lines(getString((Integer) gestures[i][2]), lineSize, this.activity.art.text(), 2);
			line.setGravity(Gravity.CENTER_HORIZONTAL);
			final float lineY = icon + (b.tablet ? 86 : 42);
			place(card, line, 12, lineY, cardW - 24, cardH - lineY - 4);
			card.setContentDescription(getString((Integer) gestures[i][1]) + ": "
					+ getString((Integer) gestures[i][2]));
		}
	}

	/** Numbered rows: a number in a disc, an optional pictogram, the gold title and one white line. */
	private void numberedRows(final FrameLayout panel, final float x, final float y, final float w, final float h,
			final int title, final int[][] items, final LauncherKit.Pictogram.Kind[] pictures) {
		final LauncherBoard b = this.activity.board;
		float top = y + (b.tablet ? 4 : 0);
		if (b.tablet) {
			place(panel, text(getString(title), b.title, this.activity.art.text()), x, y, w, -1);
			top = y + 110;
		}
		final float step = ((y + h) - top) / items.length;
		final float number = b.tablet ? 56 : 36;
		final float picture = pictures == null ? 0 : Math.min(step * 0.8f, b.tablet ? 160 : 96);
		final float textX = x + number + (b.tablet ? 28 : 16) + (picture > 0 ? picture + (b.tablet ? 32 : 16) : 0);
		final float titleSize = b.tablet ? 36 : 24;
		final float lineSize = b.tablet ? 30 : 21;
		for (int i = 0; i < items.length; i++) {
			final float rowY = top + (i * step);
			final TextView disc = text(Integer.toString(i + 1), number * 0.55f, this.activity.art.gold());
			disc.setGravity(Gravity.CENTER);
			disc.setBackground(LauncherKit.disc());
			place(panel, disc, x, rowY, number, number);
			if (picture > 0) {
				final View image = new View(this.activity);
				image.setBackground(new LauncherKit.Pictogram(pictures[i], this.activity.art.font()));
				place(panel, image, x + number + (b.tablet ? 28 : 16), rowY, picture, picture);
			}
			place(panel, text(getString(items[i][0]), titleSize, this.activity.art.gold()), textX, rowY,
					w - (textX - x), -1);
			place(panel, lines(getString(items[i][1]), lineSize, this.activity.art.text(), 2), textX,
					rowY + titleSize + (b.tablet ? 14 : 6), w - (textX - x), -1);
		}
	}
}
