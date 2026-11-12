package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.CustomGame.RACES;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/** Player Profile: the profiles, their progress and statistics, and the dialogs that change them. */
final class ProfilePage extends LauncherViews {
	/** The campaign progress of every profile, by name, while the profile page shows; read when it opens. */
	private java.util.Map<String, CCampaignProgress.Snapshot> profileProgress;
	private String profileShown;

	ProfilePage(final LauncherActivity activity) {
		super(activity);
	}

	/** A profile portrait of the skin; the first one stands in for one the game data lacks. */
	Bitmap portraitBitmap(final int index) {
		final Bitmap chosen = this.activity.art.bitmap(SkinArt.portraitFile(index));
		return chosen != null ? chosen : this.activity.art.bitmap(SkinArt.portraitFile(0));
	}

	/** The portraits this skin has in the game data. */
	private List<Integer> portraits() {
		final List<Integer> found = new ArrayList<>();
		for (int i = 0; i < this.activity.cache.portraitCount(); i++) {
			if (this.activity.art.has(SkinArt.portraitFile(i))) {
				found.add(i);
			}
		}
		return found;
	}

	/** Player Profile: the profiles on the left, the one tapped on the right; Select makes it the current one. */
	void showProfile(final String chosen) {
		final List<String> names = this.activity.profiles.names();
		final String current = this.activity.profiles.current();
		final String selected = chosen != null && names.contains(chosen) ? chosen
				: (this.profileShown != null) && names.contains(this.profileShown)
						&& (this.activity.shown == LauncherActivity.PAGE_PROFILE) ? this.profileShown : current;
		this.profileShown = selected;
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_NONE);
		final FrameLayout content = content(view, 0);
		final float listW = t ? 1060 : 560;
		final float frame = b.pageInset;
		final FrameLayout listPanel = framed(content, 0, 0, listW, b.contentH);
		final float rowW = listW - (2 * frame);
		final float fieldH = t ? 112 : 96;
		final float addW = t ? 260 : 180;
		final float gap = t ? 20 : 10;
		final float listH = b.contentH - (2 * frame) - fieldH - gap;
		final LinearLayout rows = scrollList(listPanel, frame, frame, rowW, listH);
		final float rowH = t ? 150 : 96;
		View selectedRow = null;
		for (final String name : names) {
			final View row = profileRow(name, rowW, rowH);
			onTap(row, false, v -> showProfile(name));
			markRow(row, name.equals(selected));
			if (name.equals(selected)) {
				selectedRow = row;
			}
			rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
		}
		if (selectedRow != null) {
			reveal(selectedRow);
		}
		// The design sets the name field a little closer to the frame than the rows.
		final float fieldInset = t ? 40 : 14;
		final float fieldY = b.contentH - fieldInset - fieldH;
		final float fieldW = listW - (2 * fieldInset) - addW - gap;
		final EditText field = nameField(t ? 34 : 24);
		place(listPanel, field, fieldInset, fieldY, fieldW, fieldH);
		final LauncherKit.GlueButton add = button(glueLabel("KEY_ADD", "Add"), fieldH, t ? 34 : 26, v -> {
			final String typed = field.getText().toString();
			if (this.activity.profiles.check(typed, null) == Profiles.NameProblem.NONE) {
				this.activity.profiles.create(typed, firstPortrait(), false);
				this.activity.dialogs.hideKeyboard(field);
				showProfile(typed.trim());
			}
		});
		add.setEnabled(false);
		place(listPanel, add, fieldInset + fieldW + gap, fieldY, addW, fieldH);
		watchName(field, null, add, null);
		doneTaps(field, add);

		final float rightX = t ? 1096 : 570;
		final float rightW = b.contentW - rightX;
		final FrameLayout panel = framed(content, rightX, 0, rightW, b.contentH);
		final ScrollView scroll = new ScrollView(this.activity);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		final FrameLayout details = new FrameLayout(this.activity);
		scroll.addView(details, new FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT));
		place(panel, scroll, frame, frame, rightW - (2 * frame), b.contentH - (2 * frame));
		fillProfileDetails(details, selected, rightW - (2 * frame));

		action(view, 0, glueLabel("BACK", getString(R.string.launcher_back)), v -> this.activity.homePage.showHome());
		final LauncherKit.GlueButton delete = action(view, 1, glueLabel("KEY_DELETE", "Delete"),
				v -> askDeleteProfile(selected));
		delete.setTone(LauncherKit.GlueButton.TONE_DANGER);
		delete.setEnabled(names.size() > 1);
		final LauncherKit.GlueButton select = action(view, 2, glueLabel("KEY_SELECT", "Select"), v -> {
			this.activity.profiles.select(selected);
			profileChanged();
			this.activity.homePage.showHome();
		});
		this.activity.setPage(view, LauncherActivity.PAGE_PROFILE);
		if (this.profileProgress == null) {
			readProfileProgress(names);
		}
	}

	/** A profile of the list: portrait, name and how far its campaigns got. */
	private View profileRow(final String name, final float w, final float rowH) {
		final boolean t = this.activity.board.tablet;
		final LinearLayout row = new LinearLayout(this.activity);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(px(t ? 14 : 8), 0, px(8), 0);
		final float icon = rowH - (t ? 28 : 16);
		final ImageView portrait = new ImageView(this.activity);
		portrait.setScaleType(ImageView.ScaleType.FIT_XY);
		portrait.setImageBitmap(portraitBitmap(this.activity.profiles.portrait(name)));
		row.addView(portrait, new LinearLayout.LayoutParams(px(icon), px(icon)));
		final LinearLayout texts = new LinearLayout(this.activity);
		texts.setOrientation(LinearLayout.VERTICAL);
		texts.addView(text(name, t ? 40 : 26, this.activity.art.text()));
		final String progress = campaignLine(name);
		if (!progress.isEmpty()) {
			final TextView line = text(progress, t ? 28 : 19, this.activity.art.text2());
			final LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lineParams.topMargin = px(t ? 10 : 4);
			texts.addView(line, lineParams);
		}
		final LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
				ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		textParams.leftMargin = px(t ? 22 : 12);
		row.addView(texts, textParams);
		row.setContentDescription(name);
		return row;
	}

	/** "Undead Campaign · Chapter Four": the campaign the profile plays and its furthest mission. */
	private String campaignLine(final String name) {
		final CCampaignProgress.Snapshot progress = this.profileProgress == null ? null : this.profileProgress.get(name);
		if ((progress == null) || (this.activity.campaigns == null)) {
			return "";
		}
		final CampaignCatalog catalog = this.activity.campaigns.catalog;
		final CampaignCatalog.Entry entry = CampaignChoice.playedCampaign(catalog, progress);
		if (entry == null) {
			return "";
		}
		final String chapter = chapterOf(entry, progress);
		return glueLabel(entry.getData().getHeader(), entry.getData().getHeader())
				+ (chapter.isEmpty() ? "" : " · " + chapter);
	}

	/** The header of the furthest open mission of a campaign. */
	private String chapterOf(final CampaignCatalog.Entry entry, final CCampaignProgress.Snapshot progress) {
		final List<CampaignMission> missions = entry.getData().getMissions();
		int furthest = -1;
		for (int i = 0; i < missions.size(); i++) {
			if (CampaignCatalog.isMissionOpen(entry, i, progress)) {
				furthest = i;
			}
		}
		return furthest < 0 ? ""
				: glueLabel(missions.get(furthest).getHeader(), missions.get(furthest).getHeader()).toString();
	}

	private void readProfileProgress(final List<String> names) {
		final int generation = this.activity.generation;
		final Runnable read = () -> {
			final List<File> folders = new ArrayList<>();
			for (final String name : names) {
				folders.add(this.activity.profiles.folder(name));
			}
			this.activity.catalog.progress(folders, snapshots -> {
				if ((snapshots == null) || (generation != this.activity.generation)
						|| (this.activity.shown != LauncherActivity.PAGE_PROFILE)) {
					return;
				}
				final java.util.Map<String, CCampaignProgress.Snapshot> found = new java.util.HashMap<>();
				for (int i = 0; i < names.size(); i++) {
					found.put(names.get(i), snapshots.get(i));
				}
				this.profileProgress = found;
				showProfile(this.profileShown);
			});
		};
		if (this.activity.campaigns != null) {
			read.run();
			return;
		}
		this.activity.catalog.campaigns(found -> {
			if (found != null) {
				this.activity.campaigns = found;
				read.run();
			}
		});
	}

	/** The right side: portrait, name, the two buttons, then the campaigns and the statistics side by side. */
	private void fillProfileDetails(final FrameLayout details, final String name, final float w) {
		final boolean t = this.activity.board.tablet;
		final float picture = t ? 240 : 120;
		// The design sets the portrait a little further in than the panel's contents.
		final float left = t ? 22 : 0;
		final float top0 = t ? 16 : 0;
		final ImageView portrait = new ImageView(this.activity);
		portrait.setScaleType(ImageView.ScaleType.FIT_XY);
		portrait.setImageBitmap(portraitBitmap(this.activity.profiles.portrait(name)));
		portrait.setContentDescription(name);
		place(details, portrait, left, top0, picture, picture);
		final float textX = left + picture + (t ? 44 : 16);
		place(details, text(name, t ? 60 : 32, this.activity.art.text()), textX, top0, w - textX, -1);
		final long created = this.activity.profiles.created(name);
		final int saved = this.activity.profiles.savedGames(name);
		if (t) {
			final String facts = created > 0
					? getString(R.string.profile_created,
							new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(created)), saved)
					: getString(R.string.profile_saved_games, saved);
			place(details, text(facts, 28, this.activity.art.text2()), textX, top0 + 84, w - textX, -1);
		}
		final float buttonH = t ? 112 : 96;
		final float buttonsX = t ? textX : 0;
		final float buttonsY = t ? top0 + 128 : picture + 12;
		final float changeW = t ? 340 : ((w - buttonsX) - 12) / 2;
		final float renameW = t ? 260 : changeW;
		place(details, button(LauncherKit.glueText(glue("CHANGE_PORTRAIT", "Change Portrait") + "…"), buttonH,
				t ? 32 : 24, v -> showChoosePortrait(name)), buttonsX, buttonsY, changeW, buttonH);
		place(details, button(getString(R.string.profile_rename), buttonH, t ? 32 : 24, v -> showRename(name)),
				buttonsX + changeW + (t ? 20 : 12), buttonsY, renameW, buttonH);
		final float top = t ? top0 + picture + 50 : buttonsY + buttonH + 20;
		// Campaigns take a little more than half of the width, as the design's columns do.
		final float inner = w - (2 * left) - (t ? 16 : 0);
		final float campaignsW = t ? inner * 0.52f : (w - 16) / 2;
		final float statisticsX = left + campaignsW + (t ? 40 : 16);
		final float campaignsEnd = campaignColumn(details, name, left, top, campaignsW);
		final float statisticsEnd = statisticsColumn(details, name, statisticsX, top, w - statisticsX - left);
		final View end = new View(this.activity);
		place(details, end, 0, Math.max(campaignsEnd, statisticsEnd), 1, 1);
	}

	/** The campaigns under the game they belong to; each with "Completed", its chapter or a dash. */
	private float campaignColumn(final FrameLayout parent, final String name, final float x, final float top,
			final float w) {
		final boolean t = this.activity.board.tablet;
		final float step = t ? 48 : 36;
		final float size = t ? 28 : 18;
		float y = top;
		if ((this.activity.campaigns == null) || (this.profileProgress == null)) {
			place(parent, text(getString(R.string.game_loading_list), size, this.activity.art.text2()), x, y, w, -1);
			return y + step;
		}
		final CampaignCatalog catalog = this.activity.campaigns.catalog;
		final CCampaignProgress.Snapshot progress = this.profileProgress.get(name);
		// The combined list runs on from Reign of Chaos into The Frozen Throne at its first expansion campaign.
		Boolean frozenShown = null;
		boolean frozen = !catalog.isUnified() && !catalog.isClassicList();
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			frozen |= catalog.isUnified() && entry.getSection().toLowerCase(Locale.ROOT).endsWith("ex");
			if ((frozenShown == null) || (frozenShown != frozen)) {
				if (y > top) {
					y += t ? 18 : 4;
				}
				y = sectionHead(parent, getString(frozen ? R.string.profile_frozen : R.string.profile_reign), x, y, w);
				frozenShown = frozen;
			}
			final String status;
			final int color;
			if (progress == null) {
				status = getString(R.string.profile_not_started);
				color = this.activity.art.text2();
			}
			else if (CampaignCatalog.isCompleted(entry, progress)) {
				status = getString(R.string.profile_completed);
				color = LauncherKit.OK;
			}
			else if (CampaignChoice.started(entry, progress)
					|| (CampaignChoice.playedCampaign(catalog, progress) == entry)) {
				status = chapterOf(entry, progress);
				color = this.activity.art.text();
			}
			else {
				status = getString(R.string.profile_not_started);
				color = this.activity.art.text2();
			}
			final float labelW = w * (t ? 0.62f : 0.55f);
			final float indent = t ? 16 : 0;
			place(parent, text(CampaignChoice.shortCampaign(glueLabel(entry.getData().getHeader(), entry.getData().getHeader())),
					size, this.activity.art.text()), x + indent, y, labelW - indent - 8, -1);
			place(parent, text(status, size, color), x + labelW, y, w - labelW, -1);
			y += step;
		}
		return y;
	}

	/** Games played, wins, the favourite race, the race with most wins and a row per race. */
	private float statisticsColumn(final FrameLayout parent, final String name, final float x, final float top,
			final float w) {
		final boolean t = this.activity.board.tablet;
		final float step = t ? 48 : 36;
		final float size = t ? 28 : 18;
		final float indent = t ? 16 : 0;
		float y = sectionHead(parent, getString(R.string.profile_statistics), x, top, w);
		final int[][] counts = this.activity.profiles.statistics(name, RACES.length);
		int games = 0;
		int wins = 0;
		int favourite = -1;
		int mostWins = -1;
		for (int i = 0; i < counts.length; i++) {
			games += counts[i][0];
			wins += counts[i][1];
			if ((counts[i][0] > 0) && ((favourite < 0) || (counts[i][0] > counts[favourite][0]))) {
				favourite = i;
			}
			if ((counts[i][1] > 0) && ((mostWins < 0) || (counts[i][1] > counts[mostWins][1]))) {
				mostWins = i;
			}
		}
		final String[] races = glueList(RACES, 0);
		final String none = getString(R.string.profile_not_started);
		final String[][] facts = { { getString(R.string.profile_games_played), Integer.toString(games) },
			{ getString(R.string.profile_wins),
				wins + (games > 0 ? String.format(Locale.ROOT, " · %d %%", Math.round((wins * 100f) / games)) : "") },
			{ getString(R.string.profile_favourite), favourite < 0 ? none : LauncherKit.glueText(races[favourite]).toString() },
			{ getString(R.string.profile_most_wins), mostWins < 0 ? none : LauncherKit.glueText(races[mostWins]).toString() } };
		final float labelW = w * 0.55f;
		for (final String[] fact : facts) {
			place(parent, text(fact[0], size, this.activity.art.text2()), x + indent, y, labelW - indent - 8, -1);
			place(parent, text(fact[1], size, this.activity.art.text()), x + labelW, y, w - labelW, -1);
			y += step;
		}
		y += t ? 24 : step * 0.3f;
		final float symbol = t ? 40 : 28;
		final float gamesX = x + (w * 0.55f);
		final float winsX = x + (w * 0.8f);
		place(parent, text(getString(R.string.profile_games), t ? 24 : size, this.activity.art.text2()), gamesX, y,
				w * 0.25f,
				-1);
		place(parent, text(getString(R.string.profile_wins), t ? 24 : size, this.activity.art.text2()), winsX, y,
				w * 0.2f, -1);
		y += t ? 40 : step;
		// Random first in the lobby; the table lists it last, as the design's race order has it.
		final int[] order = { 1, 2, 3, 4, 0 };
		for (final int race : order) {
			final View mark;
			if (race > 0) {
				mark = image(SkinArt.RACE_SYMBOLS[race - 1], ImageView.ScaleType.FIT_CENTER);
			}
			else {
				final TextView question = text("?", symbol * 0.8f, this.activity.art.gold());
				question.setGravity(Gravity.CENTER);
				mark = question;
			}
			place(parent, mark, x + indent, y - ((symbol - size) / 2), symbol, symbol);
			place(parent, text(LauncherKit.glueText(races[race]), size, this.activity.art.text()),
					x + indent + symbol + 14, y,
					gamesX - x - indent - symbol - 20, -1);
			place(parent, text(Integer.toString(counts[race][0]), size, this.activity.art.text()), gamesX, y, w * 0.25f,
					-1);
			place(parent, text(Integer.toString(counts[race][1]), size, this.activity.art.gold()), winsX, y, w * 0.2f,
					-1);
			y += step;
		}
		return y;
	}

	/** A heading of the profile's tables: gold, with a thin line under it on the tablet; returns the next line's y. */
	private float sectionHead(final FrameLayout parent, final String title, final float x, final float y,
			final float w) {
		final boolean t = this.activity.board.tablet;
		place(parent, text(title, t ? 30 : 22, this.activity.art.gold()), x, y, w, -1);
		if (!t) {
			return y + 38;
		}
		final View line = new View(this.activity);
		line.setBackgroundColor(Color.argb(0x1F, 0xFF, 0xFF, 0xFF));
		place(parent, line, x, y + 42, w, 2);
		return y + 60;
	}

	/** The current profile changed: its progress and saves are read again. */
	private void profileChanged() {
		this.activity.campaigns = null;
		this.profileProgress = null;
		this.activity.loadPage.saves = null;
	}

	private int firstPortrait() {
		final List<Integer> found = portraits();
		return found.isEmpty() ? 0 : found.get(0);
	}

	/** Keeps the confirming button enabled only for a usable name, and says why another one is not. */
	private void watchName(final EditText field, final String renaming, final View confirm, final TextView problem) {
		final Runnable check = () -> {
			final String typed = field.getText().toString();
			final Profiles.NameProblem found = this.activity.profiles.check(typed, renaming);
			confirm.setEnabled(found == Profiles.NameProblem.NONE);
			if (problem != null) {
				problem.setText(found == Profiles.NameProblem.EMPTY
						? glue("PROFILE_NEEDS_A_NAME", getString(R.string.profile_needs_name))
						: found == Profiles.NameProblem.TAKEN ? getString(R.string.profile_name_taken)
								: found == Profiles.NameProblem.CHARACTERS
										? getString(R.string.profile_name_characters)
										: "");
			}
		};
		afterTextChanged(field, check);
		check.run();
	}

	/**
	 * The portraits to pick from, a race under its name (one scrolling row on the phone's first profile); the chosen
	 * one glows and is scrolled into sight.
	 */
	private View portraitGrid(final int[] chosen, final int columns, final float cell, final float gap,
			final boolean oneRow) {
		final List<ImageView> glows = new ArrayList<>();
		final LinearLayout lines = new LinearLayout(this.activity);
		lines.setOrientation(oneRow ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		final View[] chosenItem = new View[1];
		int number = 0;
		for (final SkinArt.PortraitGroup group : this.activity.cache.portraitGroups()) {
			final List<Integer> found = new ArrayList<>();
			for (final int portrait : group.portraits) {
				if ((portrait >= 0) && this.activity.art.has(SkinArt.portraitFile(portrait))) {
					found.add(portrait);
				}
			}
			if (found.isEmpty()) {
				continue;
			}
			if (oneRow && (number > 0)) {
				lines.addView(new View(this.activity), new LinearLayout.LayoutParams(px(gap * 2), 1));
			}
			if (!oneRow && (group.key != null)) {
				final TextView header = text(glue(group.key, group.fallback), Math.max(24, cell * 0.16f),
						this.activity.art.gold());
				final LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(MATCH,
						ViewGroup.LayoutParams.WRAP_CONTENT);
				headerParams.topMargin = number > 0 ? px(gap) : 0;
				headerParams.bottomMargin = px(gap / 2);
				lines.addView(header, headerParams);
			}
			LinearLayout line = null;
			for (int i = 0; i < found.size(); i++) {
				final int portrait = found.get(i);
				if (oneRow) {
					line = lines;
				}
				else if ((i % columns) == 0) {
					line = new LinearLayout(this.activity);
					final LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(MATCH, px(cell));
					lineParams.bottomMargin = px(gap);
					lines.addView(line, lineParams);
				}
				final FrameLayout item = new FrameLayout(this.activity);
				final ImageView picture = new ImageView(this.activity);
				picture.setScaleType(ImageView.ScaleType.FIT_XY);
				picture.setImageBitmap(portraitBitmap(portrait));
				item.addView(picture, new FrameLayout.LayoutParams(MATCH, MATCH));
				final ImageView glow = new ImageView(this.activity);
				glow.setScaleType(ImageView.ScaleType.FIT_XY);
				glow.setImageBitmap(this.activity.art.bitmap(SkinArt.PORTRAIT_ACTIVE));
				if (!this.activity.art.reforged()) {
					// The command card draws its active glow additively.
					final android.graphics.Paint add = new android.graphics.Paint();
					add.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD));
					glow.setLayerType(View.LAYER_TYPE_HARDWARE, add);
				}
				glow.setVisibility(portrait == chosen[0] ? View.VISIBLE : View.INVISIBLE);
				if (portrait == chosen[0]) {
					chosenItem[0] = oneRow ? item : line;
				}
				item.addView(glow, new FrameLayout.LayoutParams(MATCH, MATCH));
				glows.add(glow);
				number++;
				item.setContentDescription(getString(R.string.profile_portrait) + " " + number);
				onTap(item, false, v -> {
					chosen[0] = portrait;
					for (final ImageView other : glows) {
						other.setVisibility(other == glow ? View.VISIBLE : View.INVISIBLE);
					}
				});
				final LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(px(cell), px(cell));
				itemParams.rightMargin = px(gap);
				line.addView(item, itemParams);
			}
		}
		if (oneRow) {
			final HorizontalScrollView scroll = new HorizontalScrollView(this.activity);
			scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
			scroll.addView(lines);
			if (chosenItem[0] != null) {
				scroll.post(() -> scroll.scrollTo(Math.max(0, chosenItem[0].getLeft() - px(cell)), 0));
			}
			return scroll;
		}
		final ScrollView scroll = new ScrollView(this.activity);
		scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
		scroll.addView(lines);
		if (chosenItem[0] != null) {
			scroll.post(() -> scroll.scrollTo(0, Math.max(0, chosenItem[0].getTop() - px(cell / 2))));
		}
		return scroll;
	}

	/** No profile yet: New Profile over Home, which only Create or Skip close. */
	void showFirstProfile() {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float w = t ? 2200 : 1568;
		final float h = t ? 1600 : 688;
		final FrameLayout box = this.activity.dialogs.dialogBox(w, h);
		if (box == null) {
			return;
		}
		this.activity.dialogs.dialogLocked = true;
		final float inset = t ? 80 : 40;
		final float inner = w - (2 * inset);
		final EditText field = nameField(t ? 36 : 26);
		final TextView problem = text("", t ? 26 : 18, ERROR);
		final int[] portrait = { firstPortrait() };
		final View grid;
		final float buttonY = h - (t ? 72 : 32) - b.buttonH;
		if (t) {
			place(box, text(glue("NEW_PROFILE", "New Profile"), b.title, this.activity.art.gold()), inset, 64, inner,
					-1);
			place(box, lines(getString(R.string.profile_message), b.body, this.activity.art.text(), 2), inset, 140,
					inner, -1);
			place(box, field, inset, 210, inner, 120);
			place(box, problem, inset, 340, inner, -1);
			place(box, text(getString(R.string.profile_portrait), b.body, this.activity.art.gold()), inset, 392, inner,
					-1);
			final int columns = 8;
			final float cell = 220;
			final float gridW = (columns * (cell + 20)) - 20;
			grid = portraitGrid(portrait, columns, cell, 20, false);
			place(box, grid, inset + ((inner - gridW) / 2), 446, gridW, buttonY - 446 - 32);
		}
		else {
			// On the phone the field is at the top, where the keyboard leaves it in sight.
			place(box, field, inset, 36, inner, 96);
			place(box, problem, inset, 140, inner * 0.5f, -1);
			place(box, text(getString(R.string.profile_message), 20, this.activity.art.text2()), inset + (inner * 0.5f),
					140,
					inner * 0.5f, -1);
			grid = portraitGrid(portrait, 0, 176, 12, true);
			place(box, grid, inset, 190, inner, 176 + 12);
		}
		final float buttonW = t ? 440 : 360;
		final float buttonGap = t ? 32 : 24;
		final float rowX = (w - ((2 * buttonW) + buttonGap)) / 2;
		final LauncherKit.GlueButton skip = button(getString(R.string.profile_skip), b.buttonH, b.buttonText, v -> {
			final String fallback = glue("PLAYER", "Player");
			if (this.activity.profiles.check(fallback, null) == Profiles.NameProblem.NONE) {
				this.activity.profiles.create(fallback, firstPortrait(), true);
			}
			else {
				this.activity.profiles.select(fallback);
			}
			this.activity.dialogs.closeDialog();
			profileChanged();
			this.activity.homePage.showHome();
		});
		place(box, skip, rowX, buttonY, buttonW, b.buttonH);
		final LauncherKit.GlueButton create = button(getString(R.string.profile_create), b.buttonH, b.buttonText,
				v -> {
					this.activity.profiles.create(field.getText().toString(), portrait[0], true);
					this.activity.dialogs.closeDialog();
					profileChanged();
					this.activity.homePage.showHome();
				});
		create.setTone(LauncherKit.GlueButton.TONE_CONFIRM);
		place(box, create, rowX + buttonW + buttonGap, buttonY, buttonW, b.buttonH);
		watchName(field, null, create, problem);
		this.activity.dialogs.typeInDialog(field);
		this.activity.dialogs.dialogFold = grid;
		this.activity.dialogs.dialogButtons = new View[] { skip, create };
		problem.setText("");
		doneTaps(field, create);
	}

	/** Choose Portrait: every portrait of the skin, the profile's own marked; OK keeps the one tapped. */
	private void showChoosePortrait(final String name) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final int columns = 8;
		final float cell = t ? 240 : 168;
		final float gap = t ? 24 : 12;
		final float w = t ? 2400 : 1568;
		final float gridY = t ? 140 : 84;
		final float h = t ? 1760 : 704;
		final FrameLayout box = this.activity.dialogs.dialogBox(w, h);
		if (box == null) {
			return;
		}
		final float inset = t ? 72 : 36;
		place(box, text(getString(R.string.profile_choose_portrait), b.title, this.activity.art.gold()), inset,
				t ? 56 : 28,
				w - (2 * inset), -1);
		final int[] portrait = { this.activity.profiles.portrait(name) };
		final float gridW = (columns * (cell + gap)) - gap;
		final float buttonY = h - (t ? 64 : 28) - b.buttonH;
		place(box, portraitGrid(portrait, columns, cell, gap, false), (w - gridW) / 2, gridY, gridW,
				buttonY - gridY - (t ? 24 : 12));
		final float buttonW = t ? 400 : 320;
		place(box, button(glueLabel("CANCEL", "Cancel"), b.buttonH, b.buttonText,
				v -> this.activity.dialogs.closeDialog()),
				w - inset - (2 * buttonW) - 24, buttonY, buttonW, b.buttonH);
		place(box, button(glueLabel("OK", getString(R.string.game_ok)), b.buttonH, b.buttonText, v -> {
			this.activity.profiles.setPortrait(name, portrait[0]);
			this.activity.dialogs.closeDialog();
			showProfile(name);
		}), w - inset - buttonW, buttonY, buttonW, b.buttonH);
	}

	/** Rename: the same name rules as a new profile; the folder with the saves moves along. */
	private void showRename(final String name) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float w = t ? 1400 : 1080;
		final float h = t ? 780 : 404;
		final FrameLayout box = this.activity.dialogs.dialogBox(w, h);
		if (box == null) {
			return;
		}
		final float inset = t ? 80 : 40;
		final float inner = w - (2 * inset);
		place(box, text(getString(R.string.profile_rename_title), b.title, this.activity.art.gold()), inset,
				t ? 70 : 30, inner,
				-1);
		final EditText field = nameField(t ? 36 : 26);
		field.setText(name);
		field.setSelection(name.length());
		place(box, field, inset, t ? 180 : 90, inner, t ? 120 : 96);
		final TextView problem = text("", t ? 26 : 18, ERROR);
		place(box, problem, inset, t ? 316 : 194, inner, -1);
		final float buttonY = h - (t ? 72 : 28) - b.buttonH;
		final float buttonW = t ? 400 : 320;
		place(box, button(glueLabel("CANCEL", "Cancel"), b.buttonH, b.buttonText,
				v -> this.activity.dialogs.closeDialog()),
				w - inset - (2 * buttonW) - 24, buttonY, buttonW, b.buttonH);
		final LauncherKit.GlueButton ok = button(glueLabel("OK", getString(R.string.game_ok)), b.buttonH,
				b.buttonText, v -> {
					final String typed = field.getText().toString().trim();
					this.activity.dialogs.closeDialog();
					if (!typed.equals(name) && this.activity.profiles.rename(name, typed)) {
						this.profileProgress = null;
						showProfile(typed);
					}
				});
		place(box, ok, w - inset - buttonW, buttonY, buttonW, b.buttonH);
		watchName(field, name, ok, problem);
		doneTaps(field, ok);
		this.activity.dialogs.typeInDialog(field);
		this.activity.dialogs.showKeyboard(field);
	}

	/** The original question, No first: the profile goes with its saves and campaign progress. */
	private void askDeleteProfile(final String name) {
		if (this.activity.profiles.names().size() <= 1) {
			return;
		}
		final String format = glue("DELETE_PROFILE_MESSAGE", getString(R.string.profile_delete_fallback));
		String question;
		try {
			question = String.format(Locale.ROOT, format, name);
		}
		catch (final RuntimeException e) {
			question = format;
		}
		this.activity.dialogs.showQuestion(question, getString(R.string.profile_delete_line),
				new CharSequence[] { glueLabel("NO", getString(R.string.game_no)),
					glueLabel("KEY_DELETE", "Delete") },
				new Runnable[] { null, () -> {
					final boolean current = name.equals(this.activity.profiles.current());
					this.activity.profiles.delete(name);
					if (current) {
						profileChanged();
					}
					this.profileProgress = null;
					this.profileShown = null;
					showProfile(this.activity.profiles.current());
				} }, this.activity.art.bitmap(SkinArt.QUESTION));
	}
}
