package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_ANIMATED;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MUSIC;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_SKIN;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_UPDATE_CHECK;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_UPDATE_SKIPPED;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.LauncherOptions;
import com.etheller.warsmash.launcher.SkinArt;

/** The launcher's own Options: its tabs of settings, most of them the engine's. */
final class OptionsPage extends LauncherViews {
	static final int TAB_GRAPHICS = 0;
	private static final int TAB_SOUND = 1;
	private static final int TAB_GAMEPLAY = 2;
	private static final int TAB_TOUCH = 3;
	static final int TAB_GAME_DATA = 4;
	private static final int TAB_ABOUT = 5;

	int optionsTab = TAB_GRAPHICS;

	OptionsPage(final LauncherActivity activity) {
		super(activity);
	}

	void showOptions(final int tab) {
		this.optionsTab = tab;
		final LauncherBoard b = this.activity.board;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_OPTIONS);
		final FrameLayout content = content(view, 0);
		final float top = addTabs(content, new int[] { R.string.options_graphics, R.string.options_sound,
			R.string.options_gameplay, R.string.options_touch, R.string.options_game_data, R.string.options_about },
				tab, this::showOptions);
		final FrameLayout panel = framed(content, 0, top, b.contentW, b.contentH - top);
		final ScrollView scroll = new ScrollView(this.activity);
		scroll.setVerticalScrollBarEnabled(true);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		final LinearLayout rows = new LinearLayout(this.activity);
		rows.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(rows, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		place(panel, scroll, b.pageFrame, b.pageFrame, b.contentW - (2 * b.pageFrame),
				b.contentH - top - (2 * b.pageFrame));
		final EngineSettings engine = new EngineSettings(this.activity);
		switch (tab) {
		case TAB_SOUND:
			soundRows(rows, engine);
			break;
		case TAB_GAMEPLAY:
			gameplayRows(rows, engine);
			break;
		case TAB_TOUCH:
			touchRows(rows, engine);
			break;
		case TAB_GAME_DATA:
			gameDataRows(rows);
			break;
		case TAB_ABOUT:
			aboutRows(rows);
			break;
		default:
			graphicsRows(rows, engine);
			break;
		}
		if (b.tablet) {
			// The tablet's panel is as high as its rows, as the design draws it; a longer tab scrolls.
			int rowsH = 0;
			for (int i = 0; i < rows.getChildCount(); i++) {
				final int h = rows.getChildAt(i).getLayoutParams().height;
				rowsH = (h < 0) || (rowsH < 0) ? -1 : rowsH + h;
			}
			if (rowsH >= 0) {
				final int panelH = Math.min(px(b.contentH - top), rowsH + px(2 * b.pageFrame));
				panel.getLayoutParams().height = panelH;
				scroll.getLayoutParams().height = panelH - px(2 * b.pageFrame);
			}
		}
		place(view, button(getString(R.string.launcher_back), b.backH, b.tablet ? 36 : 30,
				v -> this.activity.homePage.showHome()), b.backX,
				b.backY, b.backW, b.backH);
		this.activity.setPage(view, LauncherActivity.PAGE_OPTIONS);
	}

	private void graphicsRows(final LinearLayout rows, final EngineSettings engine) {
		final boolean reforgedHere = SkinCache.hasReforged(this.activity);
		radioRow(rows, R.string.options_interface,
				new String[] { getString(R.string.launcher_classic),
					getString(reforgedHere ? R.string.launcher_reforged : R.string.options_reforged_locked) },
				new boolean[] { true, reforgedHere }, this.activity.cache.reforged() ? 1 : 0,
				choice -> chooseSkin(choice == 1));
		final GraphicsSet[] sets = GraphicsSet.values();
		final String[] setNames = new String[sets.length];
		final boolean[] setsHere = new boolean[sets.length];
		for (int i = 0; i < sets.length; i++) {
			setsHere[i] = SkinCache.hasGraphics(this.activity, sets[i]);
			setNames[i] = getString(stringId(LauncherOptions.GRAPHICS_SETS[i][setsHere[i] ? 0 : 1]));
		}
		// The game falls back to Classic when the data lacks the chosen package, and so does the row.
		final int setChoice = GraphicsSet.fromIniName(engine.graphics(GraphicsSettings.KEY_SET, "")).ordinal();
		radioRow(rows, R.string.options_ingame_graphics, setNames, setsHere, setsHere[setChoice] ? setChoice : 0,
				choice -> engine.setGraphics(GraphicsSettings.KEY_SET, sets[choice].getIniName()));
		final String[] sizeNames = new String[LauncherOptions.TEXTURE_SIZES.length];
		for (int i = 0; i < sizeNames.length; i++) {
			sizeNames[i] = getString(stringId(LauncherOptions.TEXTURE_SIZES[i]));
		}
		int textureSize;
		try {
			textureSize = Integer.parseInt(engine.graphics(GraphicsSettings.KEY_TEXTURE_SIZE, ""));
		}
		catch (final NumberFormatException e) {
			textureSize = AndroidLauncher.defaultTextureSize(this.activity);
		}
		radioRow(rows, R.string.options_texture_size, sizeNames, null, LauncherOptions.textureSizeChoice(textureSize),
				choice -> engine.setGraphics(GraphicsSettings.KEY_TEXTURE_SIZE,
						Integer.toString(GraphicsSettings.TEXTURE_SIZES[choice])));
		checkRow(rows, R.string.options_animated, this.activity.preferences.getBoolean(KEY_ANIMATED, true),
				on -> this.activity.preferences.edit().putBoolean(KEY_ANIMATED, on).apply());
		engineRows(rows, engine, LauncherOptions.GRAPHICS);
	}

	/** Rows of engine settings as the shared table lists them. */
	private void engineRows(final LinearLayout rows, final EngineSettings engine,
			final List<LauncherOptions.Row> table) {
		for (final LauncherOptions.Row row : table) {
			final int label = stringId(row.label);
			switch (row.kind) {
			case CHECK:
				checkRow(rows, label, row.value(engine) != 0, on -> row.set(engine, on ? 1 : 0));
				break;
			case RADIO: {
				final String[] choices = new String[row.choices.size()];
				for (int i = 0; i < choices.length; i++) {
					choices[i] = getString(stringId(row.choices.get(i)));
				}
				radioRow(rows, label, choices, null, row.value(engine), choice -> row.set(engine, choice));
				break;
			}
			default:
				sliderRow(rows, label, row.value(engine), getString(stringId(row.choices.get(0))),
						getString(stringId(row.choices.get(1))), (value, done) -> {
							if (done) {
								row.set(engine, value);
							}
						});
				break;
			}
		}
	}

	private int stringId(final String name) {
		return this.activity.getResources().getIdentifier(name, "string", this.activity.getPackageName());
	}

	/** The engine's sound settings; their defaults are the engine's (music at 40, as Warcraft III has it). */
	private void soundRows(final LinearLayout rows, final EngineSettings engine) {
		engineRows(rows, engine, LauncherOptions.SOUND);
		checkRow(rows, R.string.options_menu_music, this.activity.preferences.getBoolean(KEY_MUSIC, true), on -> {
			this.activity.preferences.edit().putBoolean(KEY_MUSIC, on).apply();
			this.activity.updateMusic();
		});
	}

	private void gameplayRows(final LinearLayout rows, final EngineSettings engine) {
		engineRows(rows, engine, LauncherOptions.GAMEPLAY);
		infoRow(rows, R.string.options_profile, this.activity.profiles.current(), false);
		final View profileRow = rows.getChildAt(rows.getChildCount() - 1);
		onTap(profileRow, false, v -> this.activity.profilePage.showProfile(null));
		profileRow.setContentDescription(getString(R.string.options_profile));
	}

	/** The [Touch] keys of warsmash.ini with the engine's defaults and a sensible range around them. */
	private void touchRows(final LinearLayout rows, final EngineSettings engine) {
		touchRow(rows, R.string.options_tap_slop, engine, "TapSlopDp", 10, 4, 24, R.string.options_dp);
		touchRow(rows, R.string.options_long_press, engine, "LongPressMs", 300, 200, 800, R.string.options_ms);
		checkRow(rows, R.string.options_palm_on, !"Off".equalsIgnoreCase(engine.touch("PalmRejection", "")),
				on -> engine.setTouch("PalmRejection", on ? "Auto" : "Off"));
		touchRow(rows, R.string.options_palm, engine, "PalmTouchMajorDp", 96, 20, 400, R.string.options_dp);
		touchRow(rows, R.string.options_tap_reach, engine, "GhostTapRadiusDp", 48, 16, 96, R.string.options_dp);
		checkRow(rows, R.string.options_pen_fingers, "Allow".equalsIgnoreCase(engine.touch("PenFingerPolicy", "")),
				on -> engine.setTouch("PenFingerPolicy", on ? "Allow" : "Ignore"));
	}

	private void touchRow(final LinearLayout rows, final int label, final EngineSettings engine, final String key,
			final int fallback, final int min, final int max, final int unit) {
		int current;
		try {
			current = Math.round(Float.parseFloat(engine.touch(key, Integer.toString(fallback))));
		}
		catch (final NumberFormatException e) {
			current = fallback;
		}
		current = Math.max(min, Math.min(max, current));
		final TextView shown = text(getString(unit, current), this.activity.board.small, this.activity.art.text());
		sliderRow(rows, label, Math.round(((current - min) * 100f) / (max - min)), null, null, (value, done) -> {
			final int next = min + Math.round(((max - min) * value) / 100f);
			shown.setText(getString(unit, next));
			if (done) {
				engine.setTouch(key, Integer.toString(next));
			}
		}, shown);
	}

	private void gameDataRows(final LinearLayout rows) {
		infoRow(rows, R.string.options_source,
				getString(this.activity.homePage.installedGame() ? R.string.options_source_casc
						: R.string.options_source_folder),
				false);
		infoRow(rows, R.string.options_folder, this.activity.homePage.dataFolder(), true);
		final String version = this.activity.homePage.dataVersion();
		infoRow(rows, R.string.options_version, version.isEmpty() ? getString(R.string.options_version_unknown)
				: version.trim().replace("(", "").replace(")", ""), false);
		final DataFolderCheck.Kind kind = this.activity.homePage.installedGame() ? DataFolderCheck.Kind.CASC
				: DataFolderCheck.Kind.FOLDER;
		final File folder = new File(this.activity.homePage.dataFolder());
		final String locale = DataFolderCheck.locale(kind, folder);
		infoRow(rows, R.string.options_locale, locale.isEmpty() ? getString(R.string.options_not_stated) : locale,
				false);
		if (!this.activity.homePage.installedGame()) {
			final String files = DataFolderCheck.describe(kind, folder);
			final TextView shown = infoRow(rows, R.string.options_files, files, false);
			final DataFolderCheck.Readability known = DataFolderCheck.lastReadability(folder);
			if (known != null) {
				shown.setText(filesText(files, known));
			}
			else {
				new Thread(() -> {
					final DataFolderCheck.Readability found = DataFolderCheck.readability(folder, Integer.MAX_VALUE,
							0);
					this.activity.runOnUiThread(() -> shown.setText(filesText(files, found)));
				}, "DataFolderReadability").start();
			}
			final String packer = DataFolderCheck.packerTool(folder);
			infoRow(rows, R.string.options_packer, packer.isEmpty() ? getString(R.string.options_not_stated) : packer,
					false);
		}
		infoRow(rows, R.string.options_reforged_ui,
				getString(SkinCache.hasReforged(this.activity) ? R.string.options_found : R.string.options_missing),
				false);
		final int recordings = this.activity.art.recordings();
		infoRow(rows, R.string.options_backgrounds, recordings == 0 ? getString(R.string.options_backgrounds_missing)
				: getString(R.string.options_backgrounds_count, recordings, SkinArt.SCENES.length), false);
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = row(rows);
		final float buttonW = b.tablet ? 480 : 300;
		final float buttonH = b.tablet ? 112 : 80;
		place(row, button(getString(R.string.options_change), buttonH, b.tablet ? 36 : 26,
				v -> this.activity.openGameData()),
				b.labelX, (b.rowH - buttonH) / 2, buttonW, buttonH);
		place(row, button(getString(R.string.options_recheck), buttonH, b.tablet ? 36 : 26, v -> recheck()),
				b.labelX + buttonW + (b.tablet ? 32 : 24), (b.rowH - buttonH) / 2, buttonW, buttonH);
	}

	private void aboutRows(final LinearLayout rows) {
		infoRow(rows, R.string.options_launcher, this.activity.homePage.buildLine(), false);
		infoRow(rows, R.string.options_engine, getString(R.string.options_engine_value), false);
		infoRow(rows, R.string.options_license, getString(R.string.options_license_value), false);
		infoRow(rows, R.string.options_art, getString(R.string.options_art_value), false);
		checkRow(rows, R.string.options_check_updates, this.activity.preferences.getBoolean(KEY_UPDATE_CHECK, true),
				on -> this.activity.preferences.edit().putBoolean(KEY_UPDATE_CHECK, on).apply());
		final String skipped = this.activity.preferences.getString(KEY_UPDATE_SKIPPED, "");
		if (!skipped.isEmpty()) {
			infoRow(rows, R.string.options_skipped_update, getString(R.string.options_skipped_update_value, skipped),
					false);
			final View skippedRow = rows.getChildAt(rows.getChildCount() - 1);
			onTap(skippedRow, false, v -> {
				this.activity.preferences.edit().remove(KEY_UPDATE_SKIPPED).apply();
				showOptions(TAB_ABOUT);
			});
			skippedRow.setContentDescription(getString(R.string.options_skipped_update));
		}
	}

	/** An option row: the gold label on the left, a line above every row but the first. */
	private FrameLayout row(final LinearLayout rows) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = new FrameLayout(this.activity);
		if (rows.getChildCount() > 0) {
			final View line = new View(this.activity);
			line.setBackgroundColor(Color.argb(0x14, 0xFF, 0xFF, 0xFF));
			row.addView(line, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(2))));
		}
		rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(b.rowH)));
		return row;
	}

	private FrameLayout labelledRow(final LinearLayout rows, final int label) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = row(rows);
		final TextView name = text(getString(label), b.body, this.activity.art.gold());
		name.setGravity(Gravity.CENTER_VERTICAL);
		place(row, name, b.labelX, 0, b.controlX - b.pageFrame - b.labelX - 16, b.rowH);
		return row;
	}

	/** Control positions are given in content coordinates; the rows start inside the panel frame. */
	private float controlX(final float offset) {
		return (this.activity.board.controlX - this.activity.board.pageFrame) + offset;
	}

	private void checkRow(final LinearLayout rows, final int label, final boolean value,
			final Consumer<Boolean> changed) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = labelledRow(rows, label);
		final LauncherKit.CheckDrawable check = new LauncherKit.CheckDrawable(this.activity.art);
		check.setChecked(value);
		final View box = new View(this.activity);
		box.setBackground(check);
		place(row, box, controlX(0), (b.rowH - b.control) / 2, b.control, b.control);
		final boolean[] on = { value };
		// The whole row is the touch target.
		onTap(row, false, v -> {
			on[0] = !on[0];
			check.setChecked(on[0]);
			changed.accept(on[0]);
		});
		row.setContentDescription(getString(label));
	}

	private void radioRow(final LinearLayout rows, final int label, final String[] choices, final boolean[] enabled,
			final int selected, final IntConsumer changed) {
		final LauncherBoard b = this.activity.board;
		final TextView[] names = new TextView[choices.length];
		final float[] nameWidths = new float[choices.length];
		for (int i = 0; i < choices.length; i++) {
			final boolean active = (enabled == null) || enabled[i];
			names[i] = text(choices[i], b.body, active ? this.activity.art.text() : LauncherKit.DISABLED);
			nameWidths[i] = names[i].getPaint().measureText(choices[i]) / this.activity.scale;
		}
		final LauncherBoard.RadioPlaces places = b.radioPlaces(nameWidths, b.contentW - (2 * b.pageFrame));
		final FrameLayout row = row(rows);
		row.getLayoutParams().height = px(places.height);
		final TextView rowName = text(getString(label), b.body, this.activity.art.gold());
		rowName.setGravity(Gravity.CENTER_VERTICAL);
		place(row, rowName, b.labelX, 0,
				(places.ownLine ? b.contentW - (2 * b.pageFrame) : b.controlX - b.pageFrame) - b.labelX - 16,
				places.labelH);
		final LauncherKit.RadioDrawable[] marks = new LauncherKit.RadioDrawable[choices.length];
		final int[] current = { selected };
		for (int i = 0; i < choices.length; i++) {
			final int choice = i;
			final boolean active = (enabled == null) || enabled[i];
			final LinearLayout item = new LinearLayout(this.activity);
			item.setGravity(Gravity.CENTER_VERTICAL);
			marks[i] = new LauncherKit.RadioDrawable(this.activity.art);
			marks[i].set(i == selected, active);
			final View mark = new View(this.activity);
			mark.setBackground(marks[i]);
			item.addView(mark, new LinearLayout.LayoutParams(px(b.control), px(b.control)));
			final TextView name = names[i];
			// A name cut to share a narrow line ends in an ellipsis instead of running into the next choice.
			final LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
					ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			nameParams.leftMargin = px(LauncherBoard.RADIO_NAME_GAP);
			item.addView(name, nameParams);
			item.setEnabled(active);
			if (active) {
				onTap(item, false, v -> {
					if (current[0] == choice) {
						return;
					}
					marks[current[0]].set(false, true);
					current[0] = choice;
					marks[choice].set(true, true);
					changed.accept(choice);
				});
			}
			place(row, item, places.x[i], places.choicesY, places.w[i], b.rowH);
		}
	}

	private void sliderRow(final LinearLayout rows, final int label, final int value, final String low,
			final String high, final LauncherKit.Slider.Listener changed) {
		sliderRow(rows, label, value, low, high, changed, null);
	}

	/** Low and High on either side of the slider, or the slider and its value shown after it. */
	private void sliderRow(final LinearLayout rows, final int label, final int value, final String low,
			final String high, final LauncherKit.Slider.Listener changed, final TextView shown) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = labelledRow(rows, label);
		final float lowW = low != null ? (b.tablet ? 90 : 72) : 0;
		final float sliderW = b.tablet ? 720 : 380;
		if (low != null) {
			final TextView lowText = text(low, b.small, this.activity.art.text());
			lowText.setGravity(Gravity.CENTER_VERTICAL);
			place(row, lowText, controlX(0), 0, lowW, b.rowH);
		}
		final LauncherKit.Slider slider = new LauncherKit.Slider(this.activity, this.activity.art, px(b.control * 0.5f),
				px(b.control));
		slider.setValue(value);
		slider.setListener(changed);
		slider.setContentDescription(getString(label));
		place(row, slider, controlX(lowW), 0, sliderW, b.rowH);
		final TextView after = shown != null ? shown : text(high, b.small, this.activity.art.text());
		after.setGravity(Gravity.CENTER_VERTICAL);
		place(row, after, controlX(lowW + sliderW + (b.tablet ? 20 : 12)), 0, b.tablet ? 240 : 140, b.rowH);
	}

	private String filesText(final String files, final DataFolderCheck.Readability readability) {
		return readability.unreadable == 0 ? files
				: getString(R.string.options_files_unreadable, files, readability.unreadable);
	}

	private TextView infoRow(final LinearLayout rows, final int label, final String value, final boolean path) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = labelledRow(rows, label);
		final TextView shown = text(value, b.body, this.activity.art.text());
		shown.setGravity(Gravity.CENTER_VERTICAL);
		// A long folder keeps its last parts visible.
		shown.setEllipsize(path ? TextUtils.TruncateAt.START : TextUtils.TruncateAt.END);
		place(row, shown, controlX(0), 0, b.contentW - b.controlX - b.pageFrame - 24, b.rowH);
		return shown;
	}

	private void chooseSkin(final boolean reforged) {
		final String skin = reforged ? SkinArt.REFORGED_SKIN : SkinArt.CLASSIC_SKIN;
		this.activity.preferences.edit().putString(KEY_SKIN, skin).apply();
		this.activity.cache = SkinCache.forConfiguration(this.activity, this.activity.ini, skin);
		if (this.activity.cache.isCurrent()) {
			showOptions(TAB_GRAPHICS);
		}
		else {
			this.activity.afterPrep = LauncherActivity.PAGE_OPTIONS;
			this.activity.prepPage.showPrep();
		}
	}

	/** Re-check: the art is prepared again from the game data, as after a change of data. */
	private void recheck() {
		this.activity.cache.invalidate();
		this.activity.afterPrep = LauncherActivity.PAGE_OPTIONS;
		this.optionsTab = TAB_GAME_DATA;
		this.activity.prepPage.showPrep();
	}
}
