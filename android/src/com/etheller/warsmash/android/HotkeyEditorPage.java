package com.etheller.warsmash.android;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CustomKeys;
import com.etheller.warsmash.launcher.GlueText;
import com.etheller.warsmash.launcher.HotkeyCatalog;
import com.etheller.warsmash.launcher.KeyEditor;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.Profiles;
import com.etheller.warsmash.launcher.SkinArt;

/**
 * The hotkey editor below the keyboard and mouse settings: the races, a race's units, a unit's command cards laid out
 * by the places of their buttons, and the chosen command's key. A tap on a command waits for the key pressed on the
 * attached keyboard. The keys are the engine's CustomKeys file, which the game reads, so they work in a game at once.
 */
final class HotkeyEditorPage extends LauncherViews {
	private static final int GOLD_OUTLINE = 0xFFFCD312;
	private static final int CONFLICT = 0xFFFF6A5A;
	private static final String[] WORDS = { "shared_units", "shared_structures", "card_commands", "card_siege",
		"card_construction", "card_build", "card_learn" };

	private final OptionsPage options;
	private KeyEditor editor;
	private HotkeyCatalog hotkeys;
	private GameCatalog source;
	private boolean reading;
	private boolean unreadable;
	private int race;
	private int unit;
	private int card;
	private String command;
	private String capturing;
	private final Map<String, Bitmap> icons = new HashMap<>();
	private final Set<String> asked = new HashSet<>();
	private LinearLayout holder;
	private int generation;
	private final Set<String> prefetched = new HashSet<>();

	HotkeyEditorPage(final LauncherActivity activity, final OptionsPage options) {
		super(activity);
		this.options = options;
	}

	private KeyEditor editor() {
		if (this.editor == null) {
			this.editor = KeyEditor.open(new File(this.activity.getFilesDir(), CustomKeys.FILE),
					new AndroidStore(this.activity.getSharedPreferences(Profiles.ENGINE_PREFERENCES,
							android.content.Context.MODE_PRIVATE)));
		}
		return this.editor;
	}

	/** Adds the editor to the end of the rows of the settings. */
	void addTo(final LinearLayout rows) {
		final LauncherBoard b = this.activity.board;
		this.generation++;
		this.capturing = null;
		if (this.source != this.activity.catalog) {
			// New game data: the cards and their pictures are read again.
			this.source = this.activity.catalog;
			this.hotkeys = null;
			this.reading = false;
			this.unreadable = false;
			this.icons.clear();
			this.asked.clear();
			this.prefetched.clear();
		}
		this.editor = null;
		final FrameLayout title = this.options.row(rows);
		final TextView name = text(getString(R.string.controls_hotkeys), b.body, this.activity.art.gold());
		name.setGravity(Gravity.CENTER_VERTICAL);
		place(title, name, b.labelX, 0, b.contentW - (2 * b.pageFrame) - b.labelX, b.rowH);
		this.holder = new LinearLayout(this.activity);
		this.holder.setOrientation(LinearLayout.VERTICAL);
		rows.addView(this.holder, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		build();
	}

	/** Whether the editor is on the page shown now; the page is counted after the editor is added, so not by its number. */
	private boolean current() {
		return (this.holder != null) && (this.activity.shown == LauncherActivity.PAGE_OPTIONS)
				&& this.holder.isAttachedToWindow();
	}

	private void build() {
		final ScrollView scroll = this.holder.getParent() != null && this.holder.getParent().getParent() instanceof ScrollView
				? (ScrollView) this.holder.getParent().getParent()
				: null;
		final int scrolled = scroll != null ? scroll.getScrollY() : 0;
		this.holder.removeAllViews();
		fill();
		if (scroll != null) {
			scroll.post(() -> scroll.scrollTo(0, scrolled));
		}
	}

	private void fill() {
		final LauncherBoard b = this.activity.board;
		if (this.hotkeys == null) {
			final FrameLayout row = this.options.row(this.holder);
			final TextView note = text(getString(this.unreadable ? R.string.game_unreadable : R.string.game_loading_list),
					b.body, this.unreadable ? ERROR : this.activity.art.text2());
			note.setGravity(Gravity.CENTER_VERTICAL);
			place(row, note, b.labelX, 0, b.contentW - (2 * b.pageFrame) - b.labelX, b.rowH);
			load();
			return;
		}
		final KeyEditor keys = editor();
		this.options.radioRow(this.holder, R.string.controls_layout,
				new String[] { getString(R.string.controls_classic), getString(R.string.controls_grid) }, null,
				keys.keys().grid() ? 1 : 0, choice -> {
					keys.setGrid(choice == 1, engineStore());
					this.capturing = null;
					build();
				}, null, getString(R.string.controls_layout_tip));
		resetRow(keys);

		final List<HotkeyCatalog.Race> races = this.hotkeys.races;
		this.race = Math.max(0, Math.min(races.size() - 1, this.race));
		raceRow(races);
		final HotkeyCatalog.Race race = races.get(this.race);
		if (race.units.isEmpty()) {
			return;
		}
		this.unit = Math.max(0, Math.min(race.units.size() - 1, this.unit));
		unitGrid(race);
		final HotkeyCatalog.Unit unit = race.units.get(this.unit);
		this.card = Math.max(0, Math.min(unit.cards.size() - 1, this.card));
		if (unit.cards.size() > 1) {
			cardTabs(unit);
		}
		final HotkeyCatalog.Card card = unit.cards.get(this.card);
		commandCard(keys, card);
		requestIcons(race);
	}

	private com.etheller.warsmash.launcher.LauncherStore engineStore() {
		return new AndroidStore(
				this.activity.getSharedPreferences(Profiles.ENGINE_PREFERENCES, android.content.Context.MODE_PRIVATE));
	}

	private void load() {
		if (this.activity.catalog == null) {
			this.unreadable = true;
		}
		if (this.reading || this.unreadable) {
			return;
		}
		this.reading = true;
		final Map<String, String> words = new HashMap<>();
		for (final String word : WORDS) {
			final int id = this.options.stringId("controls_" + word);
			words.put(word, id != 0 ? getString(id) : word);
		}
		final int asking = this.generation;
		this.activity.catalog.hotkeys(words, found -> {
			this.reading = false;
			if (asking != this.generation) {
				return;
			}
			this.hotkeys = found;
			this.unreadable = found == null;
			if (current()) {
				build();
			}
		});
	}

	/** The pictures of a race's units and commands, read from the game data once and kept. */
	private void requestIcons(final HotkeyCatalog.Race race) {
		final Set<String> missing = new HashSet<>();
		for (final HotkeyCatalog.Unit unit : race.units) {
			want(missing, unit.icon);
			for (final HotkeyCatalog.Card card : unit.cards) {
				for (final HotkeyCatalog.Command command : card.commands) {
					want(missing, command.icon);
				}
			}
		}
		if (this.activity.catalog == null) {
			return;
		}
		if (missing.isEmpty()) {
			prefetchOthers(race);
			return;
		}
		this.asked.addAll(missing);
		final int asking = this.generation;
		this.activity.catalog.icons(missing, found -> {
			if ((found == null) || (asking != this.generation)) {
				return;
			}
			for (final Map.Entry<String, com.etheller.warsmash.util.ArgbImage> entry : found.entrySet()) {
				this.icons.put(entry.getKey(), GameCatalog.bitmap(entry.getValue()));
			}
			if (current()) {
				build();
			}
		});
	}

	/** Once the chosen race's pictures show, the next race's are read quietly so a change of race has them at once. */
	private void prefetchOthers(final HotkeyCatalog.Race shownRace) {
		for (final HotkeyCatalog.Race other : this.hotkeys.races) {
			if ((other == shownRace) || !this.prefetched.add(other.name == null ? "" : other.name)) {
				continue;
			}
			final Set<String> missing = new HashSet<>();
			for (final HotkeyCatalog.Unit unit : other.units) {
				want(missing, unit.icon);
				for (final HotkeyCatalog.Card card : unit.cards) {
					for (final HotkeyCatalog.Command command : card.commands) {
						want(missing, command.icon);
					}
				}
			}
			if (missing.isEmpty()) {
				continue;
			}
			this.asked.addAll(missing);
			final int asking = this.generation;
			this.activity.catalog.icons(missing, found -> {
				if ((found == null) || (asking != this.generation)) {
					return;
				}
				for (final Map.Entry<String, com.etheller.warsmash.util.ArgbImage> entry : found.entrySet()) {
					this.icons.put(entry.getKey(), GameCatalog.bitmap(entry.getValue()));
				}
				// The race may have been chosen while its pictures were coming; the build also asks for the next race's.
				if (current()) {
					build();
				}
			});
			// One race at a time: the build after its arrival asks for the next.
			return;
		}
	}

	private void want(final Set<String> missing, final String icon) {
		if ((icon != null) && !this.asked.contains(icon)) {
			missing.add(icon);
		}
	}

	private void resetRow(final KeyEditor keys) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout row = this.options.row(this.holder);
		final float buttonW = b.tablet ? 640 : 400;
		final float buttonH = b.tablet ? 112 : 80;
		final LauncherKit.GlueButton reset = button(getString(R.string.controls_reset), buttonH, b.tablet ? 36 : 26,
				v -> this.activity.dialogs.showQuestion(getString(R.string.controls_reset_ask),
						getString(R.string.controls_reset_line),
						new CharSequence[] { glueLabel("NO", getString(R.string.game_no)),
							glueLabel("YES", getString(R.string.game_yes)) },
						new Runnable[] { null, () -> {
							this.capturing = null;
							if (!keys.resetAll()) {
								this.activity.dialogs.showMessage(getString(R.string.controls_hotkeys),
										getString(R.string.controls_unsaved));
							}
							if (current()) {
								build();
							}
						} }, this.activity.art.bitmap(SkinArt.QUESTION)));
		reset.setEnabled(keys.anyChanged());
		place(row, reset, b.labelX, (b.rowH - buttonH) / 2, buttonW, buttonH);
	}

	private FrameLayout frame(final float height) {
		final FrameLayout frame = new FrameLayout(this.activity);
		this.holder.addView(frame, new LinearLayout.LayoutParams(MATCH, px(height)));
		return frame;
	}

	private void raceRow(final List<HotkeyCatalog.Race> races) {
		final LauncherBoard b = this.activity.board;
		final float height = b.tablet ? 96 : 68;
		final float gap = b.tablet ? 20 : 10;
		final FrameLayout row = frame(height + (b.tablet ? 40 : 20));
		final float width = (b.contentW - (2 * b.pageFrame) - (2 * b.labelX) - ((races.size() - 1) * gap)) / races.size();
		for (int i = 0; i < races.size(); i++) {
			final int index = i;
			final String name = races.get(i).name;
			final LauncherKit.GlueButton button = button(
					name == null ? getString(R.string.controls_common) : glue(name), height,
					b.tablet ? 32 : 22, v -> {
						if (index != this.race) {
							this.race = index;
							this.unit = 0;
							this.card = 0;
							this.command = null;
							this.capturing = null;
							build();
						}
					});
			button.setSelected(i == this.race);
			place(row, button, b.labelX + (i * (width + gap)), 0, width, height);
		}
	}

	private void unitGrid(final HotkeyCatalog.Race race) {
		final LauncherBoard b = this.activity.board;
		final float tileW = b.tablet ? 260 : 150;
		final float tileH = b.tablet ? 250 : 128;
		final float icon = b.tablet ? 150 : 72;
		final float usable = b.contentW - (2 * b.pageFrame) - (2 * b.labelX);
		final int columns = Math.max(1, (int) (usable / tileW));
		final float pitch = usable / columns;
		FrameLayout row = null;
		for (int i = 0; i < race.units.size(); i++) {
			if ((i % columns) == 0) {
				row = frame(tileH);
			}
			final HotkeyCatalog.Unit unit = race.units.get(i);
			final int index = i;
			final FrameLayout tile = new FrameLayout(this.activity);
			markRow(tile, i == this.unit);
			place(tile, picture(unit.icon), (pitch - icon) / 2, tileH * 0.06f, icon, icon);
			final TextView name = text(unit.name, b.small, i == this.unit ? this.activity.art.gold() : this.activity.art.text());
			name.setGravity(Gravity.CENTER);
			place(tile, name, 4, (tileH * 0.06f) + icon + (tileH * 0.04f), pitch - 8, b.small * 1.6f);
			onTap(tile, false, v -> {
				if (index != this.unit) {
					this.unit = index;
					this.card = 0;
					this.command = null;
					this.capturing = null;
					build();
				}
			});
			tile.setContentDescription(unit.name);
			place(row, tile, b.labelX + ((i % columns) * pitch), 0, pitch, tileH);
		}
	}

	private View picture(final String path) {
		final ImageView view = new ImageView(this.activity);
		view.setScaleType(ImageView.ScaleType.FIT_XY);
		final Bitmap bitmap = path == null ? null : this.icons.get(path);
		if (bitmap != null) {
			view.setImageBitmap(bitmap);
		}
		else {
			view.setBackground(new ColorDrawable(0xFF0C0C0C));
		}
		return view;
	}

	private void cardTabs(final HotkeyCatalog.Unit unit) {
		final LauncherBoard b = this.activity.board;
		final float height = b.tablet ? 88 : 60;
		final float gap = b.tablet ? 20 : 10;
		final FrameLayout row = frame(height + (b.tablet ? 30 : 14));
		final float width = Math.min(b.tablet ? 480 : 260, (b.contentW - (2 * b.pageFrame) - (2 * b.labelX)
				- ((unit.cards.size() - 1) * gap)) / unit.cards.size());
		for (int i = 0; i < unit.cards.size(); i++) {
			final int index = i;
			final LauncherKit.GlueButton button = button(unit.cards.get(i).name, height, b.tablet ? 30 : 20, v -> {
				if (index != this.card) {
					this.card = index;
					this.command = null;
					this.capturing = null;
					build();
				}
			});
			button.setSelected(i == this.card);
			place(row, button, b.labelX + (i * (width + gap)), 0, width, height);
		}
	}

	private void commandCard(final KeyEditor keys, final HotkeyCatalog.Card card) {
		final LauncherBoard b = this.activity.board;
		final float cell = b.tablet ? 220 : 96;
		final float gap = b.tablet ? 14 : 8;
		final float gridW = (4 * cell) + (3 * gap);
		final float gridH = (3 * cell) + (2 * gap);
		final Set<String> conflicts = keys.conflicts(card);
		HotkeyCatalog.Command selected = null;
		for (final HotkeyCatalog.Command candidate : card.commands) {
			if (candidate.id().equals(this.command)) {
				selected = candidate;
			}
		}
		if ((selected == null) && !card.commands.isEmpty()) {
			selected = card.commands.get(0);
			this.command = selected.id();
		}
		final float extraRow = hasOff(card) ? cell * 0.6f + gap * 2 : 0;
		final FrameLayout area = frame(gridH + (b.tablet ? 40 : 20) + extraRow);
		final float top = b.tablet ? 10 : 6;
		final FrameLayout slots = new FrameLayout(this.activity);
		slots.setBackground(new ColorDrawable(0xFF000000));
		place(area, slots, b.labelX - 8, top - 8, gridW + 16, gridH + 16);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 4; column++) {
				final View empty = new View(this.activity);
				empty.setBackground(outline(0xFF0C0C0C, 0xFF222222, 2));
				place(area, empty, b.labelX + (column * (cell + gap)), top + (row * (cell + gap)), cell, cell);
			}
		}
		float offX = b.labelX;
		final float offSize = cell * 0.6f;
		for (final HotkeyCatalog.Command item : card.commands) {
			final boolean off = item.x < 0;
			final float size = off ? offSize : cell;
			final float x = off ? offX : b.labelX + (item.x * (cell + gap));
			final float y = off ? top + gridH + (gap * 2) : top + (item.y * (cell + gap));
			if (off) {
				offX += offSize + gap;
			}
			place(area, commandCell(keys, item, size, item == selected, conflicts.contains(item.id())), x, y, size, size);
		}

		if (selected == null) {
			return;
		}
		final float detailX = b.labelX + gridW + (b.tablet ? 60 : 30);
		final float detailW = b.contentW - (2 * b.pageFrame) - detailX - b.labelX;
		final boolean waiting = selected.id().equals(this.capturing);
		final boolean bad = conflicts.contains(selected.id());
		final TextView name = text(commandName(selected), b.body * 1.15f, this.activity.art.gold());
		name.setGravity(Gravity.CENTER_VERTICAL);
		final float nameH = b.body * 1.9f;
		place(area, name, detailX, top, detailW, nameH);
		final float keyH = b.tablet ? 130 : 76;
		final float keyW = b.tablet ? 340 : 190;
		final float keyY = top + nameH + (b.tablet ? 16 : 8);
		final TextView key = text(waiting ? getString(R.string.controls_press) : CustomKeys.shown(keys.keyOf(selected)),
				waiting ? b.body : b.title, bad ? CONFLICT : waiting ? Color.WHITE : this.activity.art.gold());
		key.setGravity(Gravity.CENTER);
		key.setBackground(waiting ? editFrameWaiting() : editFrame(b.tablet ? 14 : 8));
		final HotkeyCatalog.Command target = selected;
		onTap(key, false, v -> {
			this.capturing = target.id().equals(this.capturing) ? null : target.id();
			build();
		});
		key.setContentDescription(getString(R.string.controls_key));
		key.setOnLongClickListener(v -> {
			showHint(key, getString(R.string.controls_tap_hint));
			return true;
		});
		place(area, key, detailX, keyY, keyW, keyH);
		final float defaultW = b.tablet ? 480 : 270;
		final boolean changed = keys.changed(selected);
		final LauncherKit.GlueButton restore = button(
				getString(R.string.controls_default_key, CustomKeys.shown(keys.defaultKeyOf(selected))), keyH * 0.8f,
				b.tablet ? 34 : 22, v -> {
					this.capturing = null;
					if (!keys.restore(target)) {
						this.activity.dialogs.showMessage(getString(R.string.controls_hotkeys),
								getString(R.string.controls_unsaved));
					}
					build();
				});
		restore.setEnabled(changed);
		place(area, restore, detailX + keyW + (b.tablet ? 30 : 16), keyY + (keyH * 0.1f), defaultW, keyH * 0.8f);

		// The chosen command's tooltip as the game shows it, with its key as the player has it; a conflict under it.
		final LinearLayout tip = new LinearLayout(this.activity);
		tip.setOrientation(LinearLayout.VERTICAL);
		tip.setPadding(px(b.labelX), px(b.tablet ? 20 : 8), px(b.labelX), px(b.tablet ? 24 : 12));
		final String title = GlueText.plain(CustomKeys.titled(selected.tip, selected.dataKey, keys.keyOf(selected)))
				.replace("%d", "1");
		tip.addView(lines(title, b.body, Color.WHITE, 2), new LinearLayout.LayoutParams(MATCH,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		if (!selected.text.isEmpty()) {
			final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(MATCH,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			textParams.topMargin = px(b.tablet ? 12 : 6);
			tip.addView(lines(GlueText.plain(selected.text), b.small, 0xFFE8E0C8, b.tablet ? 8 : 5), textParams);
		}
		if (!conflicts.isEmpty()) {
			final Map<String, List<String>> byKey = new LinkedHashMap<>();
			for (final HotkeyCatalog.Command item : card.commands) {
				if (conflicts.contains(item.id())) {
					byKey.computeIfAbsent(CustomKeys.shown(keys.keyOf(item)), k -> new ArrayList<>())
							.add(commandName(item));
				}
			}
			final StringBuilder list = new StringBuilder();
			for (final Map.Entry<String, List<String>> entry : byKey.entrySet()) {
				if (list.length() > 0) {
					list.append("; ");
				}
				list.append(entry.getKey()).append(" – ").append(String.join(" / ", entry.getValue()));
			}
			final LinearLayout.LayoutParams conflictParams = new LinearLayout.LayoutParams(MATCH,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			conflictParams.topMargin = px(b.tablet ? 16 : 8);
			final TextView conflict = lines(LauncherKit.glueText(getString(R.string.controls_conflict, list)), b.small,
					Color.WHITE, 3);
			conflict.setGravity(Gravity.CENTER_HORIZONTAL);
			tip.addView(conflict, conflictParams);
		}
		this.holder.addView(tip, new LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private static boolean hasOff(final HotkeyCatalog.Card card) {
		for (final HotkeyCatalog.Command command : card.commands) {
			if (command.x < 0) {
				return true;
			}
		}
		return false;
	}

	private String commandName(final HotkeyCatalog.Command command) {
		return CustomKeys.OFF.equals(command.field) ? getString(R.string.controls_off, command.name) : command.name;
	}

	/** A button of the command card: its picture, its key in the corner, and a gold frame when chosen. */
	private View commandCell(final KeyEditor keys, final HotkeyCatalog.Command item, final float size,
			final boolean chosen, final boolean bad) {
		final FrameLayout cell = new FrameLayout(this.activity);
		cell.addView(picture(item.icon), new FrameLayout.LayoutParams(MATCH, MATCH));
		final String shown = CustomKeys.shown(keys.keyOf(item));
		final float badge = size * 0.36f;
		final float badgeW = shown.length() > 1 ? size * 0.7f : badge;
		final TextView mark = text(shown, badge * 0.75f, bad ? CONFLICT : Color.WHITE);
		mark.setGravity(Gravity.CENTER);
		mark.setBackgroundColor(0xC0000000);
		final FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(px(badgeW), px(badge),
				Gravity.BOTTOM | Gravity.END);
		cell.addView(mark, markParams);
		if (chosen) {
			final View frame = new View(this.activity);
			frame.setBackground(outline(Color.TRANSPARENT, GOLD_OUTLINE, this.activity.board.tablet ? 6 : 4));
			cell.addView(frame, new FrameLayout.LayoutParams(MATCH, MATCH));
		}
		final boolean waiting = item.id().equals(this.capturing);
		onTap(cell, false, v -> {
			// A tap chooses the command and waits for its key; a second tap on the waiting one gives up.
			this.command = item.id();
			this.capturing = waiting ? null : item.id();
			build();
		});
		cell.setContentDescription(commandName(item));
		return cell;
	}

	private GradientDrawable outline(final int fill, final int line, final int width) {
		final GradientDrawable drawable = new GradientDrawable();
		drawable.setColor(fill);
		drawable.setStroke(px(width), line);
		return drawable;
	}

	private android.graphics.drawable.Drawable editFrameWaiting() {
		final GradientDrawable drawable = outline(0xE63C2D05, GOLD_OUTLINE, this.activity.board.tablet ? 4 : 3);
		return drawable;
	}

	/**
	 * A key pressed on the keyboard while a command is chosen: a letter, digit or punctuation mark binds, Esc gives up a
	 * waiting one.
	 */
	boolean handleKey(final KeyEvent event) {
		// A key pressed while a command is chosen but not yet waiting binds at once, so the key typed right after the
		// tap on a command or before "Press a key…" shows is not lost.
		final boolean armed = this.capturing != null;
		if (!current() || (this.hotkeys == null) || (!armed && (this.command == null))) {
			return false;
		}
		final int code = event.getKeyCode();
		String key = KeyEditor.keyOfAndroidCode(code);
		if (key == null) {
			// Punctuation binds by the character the key types without a modifier, so Shift does not change the key.
			final int typed = event.getUnicodeChar(0) & KeyCharacterMap.COMBINING_ACCENT_MASK;
			key = (typed > 0) && (typed < 0x80) ? KeyEditor.keyOfChar((char) typed) : null;
		}
		if ((key == null) && (!armed || (code != KeyEvent.KEYCODE_ESCAPE))) {
			return false;
		}
		if (!armed && ((event.getModifiers() & (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_META_ON)) != 0)) {
			return false;
		}
		if ((event.getAction() != KeyEvent.ACTION_DOWN) || (event.getRepeatCount() > 0)) {
			return true;
		}
		final String waiting = armed ? this.capturing : this.command;
		this.capturing = null;
		if (key != null) {
			final HotkeyCatalog.Command found = find(waiting);
			if ((found != null) && !editor().bind(found, key)) {
				this.activity.dialogs.showMessage(getString(R.string.controls_hotkeys),
						getString(R.string.controls_unsaved));
			}
		}
		build();
		return true;
	}

	private HotkeyCatalog.Command find(final String id) {
		for (final HotkeyCatalog.Race race : this.hotkeys.races) {
			for (final HotkeyCatalog.Unit unit : race.units) {
				for (final HotkeyCatalog.Card card : unit.cards) {
					for (final HotkeyCatalog.Command found : card.commands) {
						if (found.id().equals(id)) {
							return found;
						}
					}
				}
			}
		}
		return null;
	}
}
