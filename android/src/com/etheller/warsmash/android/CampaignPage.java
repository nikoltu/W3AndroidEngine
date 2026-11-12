package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.CAMPAIGN_MAPS;
import static com.etheller.warsmash.launcher.LauncherKeys.DIFFICULTIES;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_CAMPAIGN;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_DIFFICULTY;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MISSION;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.CampaignResult;
import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMenuData;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/** The Campaign page with its difficulty box, and the Missions of the campaign chosen. */
final class CampaignPage extends LauncherViews {
	/** A section header of the campaign list is this part of a row high. */
	private static final float SECTION_HEIGHT = 0.62f;

	/** The header and name of each Forsaken Kingdom campaign, listed as still to come. */
	private static final int[][] FORSAKEN_NAMES = {
		{ R.string.campaign_fk_prologue_header, R.string.campaign_fk_prologue },
		{ R.string.campaign_fk_forsaken_header, R.string.campaign_fk_forsaken } };

	CampaignPage(final LauncherActivity activity) {
		super(activity);
	}

	private boolean campaignsReady(final int kind) {
		if (this.activity.campaigns != null) {
			return true;
		}
		final int generation = loadingPage(LauncherActivity.RAIL_CAMPAIGN, kind);
		this.activity.catalog.campaigns(found -> {
			if (generation != this.activity.generation) {
				return;
			}
			if (found == null) {
				unreadable();
				return;
			}
			this.activity.campaigns = found;
			this.activity.showPage(kind);
		});
		return false;
	}

	/** The campaign last chosen, else the one the profile's last mission chose, else the first open one. */
	private CampaignCatalog.Entry chosenCampaign() {
		return CampaignChoice.chosenCampaign(this.activity.campaigns.catalog, this.activity.campaigns.progress,
				this.activity.preferences.contains(KEY_CAMPAIGN) ? this.activity.preferences.getInt(KEY_CAMPAIGN, 0)
				: null);
	}

	/** The campaign's scene, its model at 22 % from the left, darkening to the right where the list is. */
	private void sceneBehind(final FrameLayout view, final CampaignCatalog.Entry entry) {
		final String scene = entry != null ? CampaignChoice.campaignScene(entry.getSection()) : "roc_prologue";
		final float focus = 0.22f;
		final Bitmap still = this.activity.art.poster(scene);
		final Drawable drawable = still != null ? new LauncherKit.CoverDrawable(still, focus, 0)
				: this.activity.homePage.sceneStill();
		final LauncherBoard b = this.activity.board;
		view.addView(this.activity.sceneBackdrop(drawable, scene, focus, LauncherActivity.LAYER_PAGE,
				entry != null ? entry.getData().getBackground() : "TutorialBackdrop", null,
				entry != null ? entry.getData().getBackgroundFogSettings() : null, 0, 0, 0, b.width, b.height, -1), 0,
				new FrameLayout.LayoutParams(MATCH, MATCH));
		final View shade = new View(this.activity);
		shade.setBackground(gradient(false, new int[] { Color.TRANSPARENT, Color.TRANSPARENT,
			Color.argb(0xC7, 0, 0, 0) }, new float[] { 0, 0.4f, 1 }));
		view.addView(shade, 1, new FrameLayout.LayoutParams(MATCH, MATCH));
	}

	private float campaignListX() {
		return this.activity.board.campaignListX();
	}

	private float campaignListW() {
		return this.activity.board.tablet ? 924 : 600;
	}

	/** On the phone the campaign pages reach the bottom of the screen. */
	private float campaignExtra() {
		return this.activity.board.tablet ? 0 : 108;
	}

	private float campaignRowH() {
		return this.activity.board.tablet ? 136 : 96;
	}

	/** A row of the campaign screens: the arrow button, the small gold header and the name. */
	private View campaignRow(final String header, final String name, final boolean chosen,
			final View.OnClickListener listener) {
		final float rowH = campaignRowH();
		final float w = campaignListW();
		final FrameLayout row = new FrameLayout(this.activity);
		// The design's row: the button takes 86 % of the row, the header and name follow at fixed fractions of it;
		// 15 % of the text size stands for the space the page's text boxes keep above the letters.
		final float arrow = rowH * 0.86f;
		final boolean reforged = this.activity.art.reforged() && this.activity.art.has(SkinArt.CHEVRON_RIGHT);
		if (reforged) {
			// The Reforged campaign list's arrow: a small blue button with a gold chevron, its text in capitals.
			final float button = arrow * 0.62f;
			final View backdrop = new View(this.activity);
			backdrop.setBackground(LauncherKit.GlueButton.backdrop(this.activity.art, px(button),
					LauncherKit.GlueButton.TONE_PLAIN));
			final float bx = (arrow - button) / 2;
			final float by = (rowH - button) / 2;
			place(row, backdrop, bx, by, button, button);
			place(row, image(SkinArt.CHEVRON_RIGHT, ImageView.ScaleType.FIT_CENTER), bx + (button * 0.22f),
					by + (button * 0.22f), button * 0.56f, button * 0.56f);
		}
		else {
			place(row, image(SkinArt.CAMPAIGN_ARROW, ImageView.ScaleType.FIT_CENTER), 0, (rowH - arrow) / 2, arrow,
					arrow);
		}
		final float textX = arrow + (rowH * 0.14f);
		final float headerSize = rowH * 0.27f;
		final float nameSize = rowH * 0.42f;
		final CharSequence headerText = reforged ? upper(glueLabel(header, header)) : glueLabel(header, header);
		final CharSequence nameText = reforged ? upper(glueLabel(name, name)) : glueLabel(name, name);
		place(row, text(headerText, headerSize, chosen ? this.activity.art.text() : this.activity.art.gold()), textX,
				(rowH * 0.04f) + (headerSize * 0.15f), w - textX - 8, -1);
		final TextView title = text(nameText, nameSize, chosen ? this.activity.art.gold() : this.activity.art.text());
		if (chosen) {
			title.setShadowLayer(nameSize * this.activity.scale * 0.35f, 0, 0, this.activity.art.gold());
		}
		place(row, title, textX, (rowH * 0.36f) + (nameSize * 0.15f), w - textX - 8, -1);
		if (listener != null) {
			onTap(row, false, listener);
		}
		else {
			// A campaign still to come: without colour, darker and half seen, not tappable.
			final ColorMatrix muted = new ColorMatrix();
			muted.setSaturation(0);
			muted.postConcat(new ColorMatrix(new float[] { 0.8f, 0, 0, 0, 0, 0, 0.8f, 0, 0, 0, 0, 0, 0.8f, 0, 0, 0,
				0, 0, 1, 0 }));
			final Paint paint = new Paint();
			paint.setColorFilter(new ColorMatrixColorFilter(muted));
			row.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
			row.setAlpha(0.5f);
		}
		row.setContentDescription(headerText + " " + nameText);
		return row;
	}

	/**
	 * A section header of the campaign list: the section's name in gold capitals, followed by a thin line on the
	 * classic skin, between the Reforged heading ornament and its mirror on the Reforged one.
	 */
	private View sectionHeader(final int section) {
		final float h = campaignRowH() * SECTION_HEIGHT;
		final float size = h * 0.46f;
		final LinearLayout header = new LinearLayout(this.activity);
		header.setOrientation(LinearLayout.HORIZONTAL);
		header.setGravity(Gravity.CENTER_VERTICAL);
		final boolean ornament = this.activity.art.reforged() && this.activity.art.has(SkinArt.HEADER_ORNAMENT);
		final int gap = px(size * 0.45f);
		if (ornament) {
			header.addView(image(SkinArt.HEADER_ORNAMENT, ImageView.ScaleType.FIT_CENTER),
					spaced(px(size * 2.9f), px(size), gap));
		}
		final String name = getString(section == CampaignChoice.SECTION_FROZEN ? R.string.profile_frozen
				: section == CampaignChoice.SECTION_FORSAKEN ? R.string.campaign_forsaken : R.string.profile_reign);
		final TextView title = text(upper(name), size, this.activity.art.gold());
		title.setLetterSpacing(0.08f);
		header.addView(title, spaced(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
				gap));
		String description = name;
		if (section == CampaignChoice.SECTION_FORSAKEN) {
			final String soon = getString(R.string.campaign_coming_soon);
			header.addView(text(soon, size * 0.8f, this.activity.art.text2()), spaced(
					LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, gap));
			description += " " + soon;
		}
		if (ornament) {
			final ImageView mirror = image(SkinArt.HEADER_ORNAMENT, ImageView.ScaleType.FIT_CENTER);
			mirror.setScaleX(-1);
			header.addView(mirror, spaced(px(size * 2.9f), px(size), 0));
		}
		else {
			final View line = new View(this.activity);
			line.setBackground(gradient(false, new int[] { this.activity.art.line(),
				this.activity.art.line() & 0x00FFFFFF }, new float[] { 0, 1 }));
			final LinearLayout.LayoutParams params = spaced(0, Math.max(1, px(2)), 0);
			params.weight = 1;
			header.addView(line, params);
		}
		header.setContentDescription(description);
		return header;
	}

	private static LinearLayout.LayoutParams spaced(final int w, final int h, final int right) {
		final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(w, h);
		params.rightMargin = right;
		return params;
	}

	void showCampaign() {
		if (!campaignsReady(LauncherActivity.PAGE_CAMPAIGN)) {
			return;
		}
		final LauncherBoard b = this.activity.board;
		final CampaignCatalog catalog = this.activity.campaigns.catalog;
		final CCampaignProgress.Snapshot progress = this.activity.campaigns.progress;
		final CampaignCatalog.Entry chosen = chosenCampaign();
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_CAMPAIGN, false);
		sceneBehind(view, chosen);
		final FrameLayout content = content(view, campaignExtra());
		final LinearLayout rows = scrollList(content, campaignListX(), 0, campaignListW(), b.contentH + campaignExtra());
		View chosenRow = null;
		final float rowH = campaignRowH();
		for (final CampaignChoice.ListLine line : CampaignChoice.campaignList(catalog, progress)) {
			switch (line.kind) {
			case CampaignChoice.ListLine.GAP:
				rows.addView(new View(this.activity), new LinearLayout.LayoutParams(1, px(rowH / 2)));
				break;
			case CampaignChoice.ListLine.SECTION:
				rows.addView(sectionHeader(line.index), new LinearLayout.LayoutParams(MATCH,
						px(rowH * SECTION_HEIGHT)));
				break;
			case CampaignChoice.ListLine.COMING: {
				final int[] names = FORSAKEN_NAMES[line.index];
				final View row = campaignRow(getString(names[0]), getString(names[1]), false, null);
				rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
				break;
			}
			default: {
				final CampaignCatalog.Entry entry = line.entry;
				final View row = campaignRow(entry.getData().getHeader(), entry.getData().getName(), entry == chosen,
						v -> {
							if (this.activity.preferences.getInt(KEY_CAMPAIGN, -1) != entry.getNumber()) {
								this.activity.preferences.edit().putInt(KEY_CAMPAIGN, entry.getNumber())
										.remove(KEY_MISSION).apply();
							}
							showMissions();
						});
				rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
				if (entry == chosen) {
					chosenRow = row;
				}
				break;
			}
			}
		}
		if (chosenRow == null) {
			place(content, text(getString(R.string.game_no_campaigns), b.body, this.activity.art.text2()),
					campaignListX(), 0,
					campaignListW(), -1);
		}
		else {
			reveal(chosenRow);
		}
		// The Reforged campaign screen asks for the version and difficulty on the chosen campaign's page.
		if (!(this.activity.art.reforged() && this.activity.art.has(SkinArt.CHAPTER_OPEN))) {
			addDifficulty(content);
		}
		this.activity.setPage(view, LauncherActivity.PAGE_CAMPAIGN);
	}

	/**
	 * The difficulty box at the bottom left: the label and its menu in one line, the description under them; with
	 * the Reforged maps in the data, the campaign version's menu in a box of its own above it.
	 */
	private void addDifficulty(final FrameLayout content) {
		final boolean t = this.activity.board.tablet;
		final boolean versions = (this.activity.campaigns != null) && this.activity.campaigns.catalog.hasReforgedMaps();
		final float edge = t ? 38 : 14;
		final float pad = t ? 24 : 12;
		final float inner = edge + pad;
		final float menuH = t ? 98 : 96;
		final float menuW = t ? 400 : 230;
		final float labelSize = t ? 36 : 24;
		final float w = t ? 820 : 508;
		final float h = t ? 340 : 233;
		final float y = t ? 1092 : 349;
		final FrameLayout box = new FrameLayout(this.activity);
		box.setBackground(editFrame(edge));
		place(content, box, 0, y, w, h);
		final TextView label = text(glueLabel("SELECT_DIFFICULTY_LEVEL", "Difficulty Level:"), labelSize,
				this.activity.art.gold());
		label.setGravity(Gravity.CENTER_VERTICAL);
		place(box, label, inner, inner, w - (2 * inner) - menuW - 12, menuH);
		final String current = this.activity.preferences.getString(KEY_DIFFICULTY, DIFFICULTIES[0][0]);
		int selected = 0;
		for (int i = 0; i < DIFFICULTIES.length; i++) {
			if (DIFFICULTIES[i][0].equals(current)) {
				selected = i;
			}
		}
		place(box, dropdown(glueList(DIFFICULTIES, 1), selected, true, menuH, t ? 34 : 26,
				choice -> this.activity.preferences.edit().putString(KEY_DIFFICULTY, DIFFICULTIES[choice][0]).apply()),
				w - inner - menuW, inner, menuW, menuH);
		place(box, lines(glueLabel("DIFFICULTY_TEXT", ""), t ? 26 : 19, this.activity.art.text(), 3), inner,
				inner + menuH + (t ? 16 : 10), w - (2 * inner), -1);
		if (!versions) {
			return;
		}
		// The version box stands above the difficulty box, which keeps its place in the design.
		final float versionH = (2 * inner) + menuH;
		final FrameLayout versionBox = new FrameLayout(this.activity);
		versionBox.setBackground(editFrame(edge));
		place(content, versionBox, 0, y - versionH - (t ? 24 : 8), w, versionH);
		final TextView versionLabel = text(glueLabel("CAMPAIGN_VERSION", getString(R.string.launcher_campaign_maps)),
				labelSize, this.activity.art.gold());
		versionLabel.setGravity(Gravity.CENTER_VERTICAL);
		// "Campaign Version:" is longer than the difficulty's label; its menu is narrower.
		final float versionMenuW = t ? 320 : menuW;
		place(versionBox, versionLabel, inner, inner, w - (2 * inner) - versionMenuW - 12, menuH);
		final String profile = this.activity.profiles.current();
		final int version = LaunchContract.CAMPAIGN_MAPS_CLASSIC.equals(this.activity.profiles.campaignMaps(profile))
				? 1 : 0;
		place(versionBox, dropdown(glueList(CAMPAIGN_MAPS, 1), version, true, menuH, t ? 34 : 26,
				choice -> this.activity.profiles.setCampaignMaps(profile, CAMPAIGN_MAPS[choice][0])),
				w - inner - versionMenuW,
				inner, versionMenuW, menuH);
	}

	void showMissions() {
		if (!campaignsReady(LauncherActivity.PAGE_MISSIONS)) {
			return;
		}
		final CampaignCatalog.Entry entry = chosenCampaign();
		if (entry == null) {
			showCampaign();
			return;
		}
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final boolean reforged = this.activity.art.reforged() && this.activity.art.has(SkinArt.CHAPTER_OPEN);
		if (this.activity.campaignResult != null) {
			if (reforged) {
				showResult(entry, this.activity.campaignResult);
			}
			else {
				showClassicResult(entry, this.activity.campaignResult);
			}
			return;
		}
		this.activity.campaignResult = null;
		final CCampaignProgress.Snapshot progress = this.activity.campaigns.progress;
		final List<CampaignMission> missions = entry.getData().getMissions();
		// The mission last played, else the furthest one open: after a victory the one it opened.
		int chosen = this.activity.preferences.getInt(KEY_MISSION, -1);
		if ((chosen < 0) || (chosen >= missions.size()) || !CampaignCatalog.isMissionOpen(entry, chosen, progress)) {
			chosen = 0;
			for (int i = 0; i < missions.size(); i++) {
				if (CampaignCatalog.isMissionOpen(entry, i, progress)) {
					chosen = i;
				}
			}
		}
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_CAMPAIGN, false);
		sceneBehind(view, entry);
		final FrameLayout content = content(view, campaignExtra());
		// The cinematics the campaign has, as the original list shows them: the introduction and the opening once
		// open, before the missions, the ending after them once won. A cinematic has no mission number.
		final CampaignMenuData data = entry.getData();
		final List<CampaignMission> shown = new ArrayList<>();
		final List<Integer> numbers = new ArrayList<>();
		addCinematic(shown, numbers, data.getIntroCinematic());
		if (progress.isOpeningCinematicAvailable(entry.getNumber())) {
			addCinematic(shown, numbers, data.getOpenCinematic());
		}
		for (int i = 0; i < missions.size(); i++) {
			if (CampaignCatalog.isMissionOpen(entry, i, progress)) {
				shown.add(missions.get(i));
				numbers.add(i);
			}
		}
		if (progress.isEndingCinematicAvailable(entry.getNumber())) {
			addCinematic(shown, numbers, data.getEndCinematic());
		}
		final float rowH = reforged ? chapterRowH() : campaignRowH();
		final float listH = b.contentH + campaignExtra();
		float listY = 0;
		if (reforged) {
			// As on the Reforged campaign screen, a short chapter list stands level with the campaign's name.
			final float middle = addCampaignBlock(content, entry);
			final float total = shown.size() * rowH;
			listY = Math.max(0, Math.min(listH - total, middle - (total / 2)));
		}
		final LinearLayout rows = scrollList(content, campaignListX(), listY, campaignListW(), listH - listY);
		View chosenRow = null;
		for (int i = 0; i < shown.size(); i++) {
			final CampaignMission mission = shown.get(i);
			final int number = numbers.get(i);
			final View.OnClickListener tap = number < 0 ? v -> playMovies(mission.getMovies())
					: v -> launchMission(entry, number, LauncherActivity.PAGE_MISSIONS);
			final View row;
			if (reforged) {
				final boolean won = (number >= 0) && CampaignCatalog.isMissionWon(entry, number, progress);
				row = chapterRow(mission, number == chosen,
						number < 0 ? SkinArt.CHAPTER_CINEMATIC : won ? SkinArt.CHAPTER_WON : SkinArt.CHAPTER_OPEN, tap);
			}
			else {
				row = campaignRow(mission.getHeader(), mission.getMissionName(), number == chosen, tap);
			}
			rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
			if (number == chosen) {
				chosenRow = row;
			}
		}
		if (chosenRow != null) {
			reveal(chosenRow);
		}
		if (!reforged) {
			addClassicName(content, data);
		}
		action(view, 0, glueLabel("BACK", getString(R.string.launcher_back)), v -> showCampaign());
		this.activity.setPage(view, LauncherActivity.PAGE_MISSIONS);
	}

	/** The classic campaign's header and name, centred under the scene left of the list. */
	private void addClassicName(final FrameLayout content, final CampaignMenuData data) {
		final LauncherBoard b = this.activity.board;
		final boolean t = b.tablet;
		final float nameSize = t ? 72 : 40;
		final float headerSize = t ? 40 : 24;
		final float nameY = t ? b.contentH - 20 - (nameSize * 1.2f) : 380;
		final float nameW = campaignListX() - (t ? 60 : 24);
		final TextView header = text(glueLabel(data.getHeader(), ""), headerSize, this.activity.art.gold());
		header.setGravity(Gravity.CENTER_HORIZONTAL);
		place(content, header, 0, t ? b.contentH - 110 - (headerSize * 1.2f) : nameY - headerSize - 8, nameW, -1);
		final TextView name = text(glueLabel(data.getName(), ""), nameSize, this.activity.art.text());
		name.setGravity(Gravity.CENTER_HORIZONTAL);
		place(content, name, 0, nameY, nameW, -1);
	}

	/** A cinematic the list shows when the game data has its movies. */
	private void addCinematic(final List<CampaignMission> shown, final List<Integer> numbers,
			final CampaignMission cinematic) {
		if ((cinematic != null) && this.activity.campaigns.hasMovie(cinematic)) {
			shown.add(cinematic);
			numbers.add(-1);
		}
	}

	private void playMovies(final List<String> names) {
		final int width = this.activity.getResources().getDisplayMetrics().widthPixels;
		this.activity.catalog.movies(names, width, movies -> {
			if ((movies == null) || movies.isEmpty() || this.activity.isFinishing()) {
				Log.w(LauncherActivity.TAG, "No movie to play for " + names);
				return;
			}
			this.activity.startActivity(MovieActivity.intent(this.activity, movies));
			this.activity.overridePendingTransition(0, 0);
		});
	}

	private static String upper(final CharSequence text) {
		return text.toString().toUpperCase(Locale.ROOT);
	}

	/**
	 * The Reforged campaign screen's left side: the shield of the campaign version, the campaign's header and name,
	 * and under the name one bar with the version and difficulty menus and the button to the saved games. Gives the
	 * height of the name's middle.
	 */
	private float addCampaignBlock(final FrameLayout content, final CampaignCatalog.Entry entry) {
		return addCampaignBlock(content, entry, null);
	}

	/** The campaign block; after a mission Continue stands in place of the version and difficulty menus. */
	private float addCampaignBlock(final FrameLayout content, final CampaignCatalog.Entry entry,
			final GameResult result) {
		final boolean t = this.activity.board.tablet;
		final CampaignMenuData data = entry.getData();
		final boolean versions = this.activity.campaigns.catalog.hasReforgedMaps();
		final String profile = this.activity.profiles.current();
		final int version = !versions
				|| LaunchContract.CAMPAIGN_MAPS_CLASSIC.equals(this.activity.profiles.campaignMaps(profile)) ? 1 : 0;
		final float shield = t ? 176 : 84;
		final float headerSize = t ? 46 : 22;
		final float nameSize = t ? 60 : 30;
		final float menuH = t ? 98 : 96;
		final float gap = t ? 12 : 6;
		final float left = t ? 24 : 0;
		final float right = campaignListX() - (t ? 48 : 16);
		final float y = ((this.activity.board.contentH + campaignExtra()) - shield - gap - menuH) * 0.5f;
		place(content, image(version == 0 ? SkinArt.VERSION_REFORGED : SkinArt.VERSION_CLASSIC,
				ImageView.ScaleType.FIT_CENTER), left, y, shield, shield);
		final float tx = left + shield + (t ? 20 : 10);
		final TextView header = fitted(text(upper(glueLabel(data.getHeader(), data.getHeader())), headerSize,
				this.activity.art.gold()), headerSize, right - tx);
		place(content, header, tx, y + (shield * 0.1f), right - tx, -1);
		final TextView name = fitted(text(upper(glueLabel(data.getName(), data.getName())), nameSize,
				this.activity.art.text()), nameSize, right - tx);
		place(content, name, tx, y + (shield * 0.5f), right - tx, -1);
		final float menuY = y + shield + gap;
		final float textSize = t ? 32 : 22;
		float mx = tx;
		if (result != null) {
			final float continueW = t ? 340 : 190;
			place(content, button(upper(glueLabel("CONTINUE", "Continue")), menuH, textSize,
					v -> continueAfter(entry, result)), mx, menuY, continueW, menuH);
			mx += continueW + gap;
		}
		else if (versions) {
			final float versionW = t ? 400 : 170;
			place(content, dropdown(glueList(CAMPAIGN_MAPS, 1), version, true, menuH, textSize,
					choice -> {
						this.activity.profiles.setCampaignMaps(profile, CAMPAIGN_MAPS[choice][0]);
						showMissions();
					}), mx, menuY, versionW, menuH);
			mx += versionW + gap;
		}
		if (result == null) {
			final String current = this.activity.preferences.getString(KEY_DIFFICULTY, DIFFICULTIES[0][0]);
			int selected = 0;
			for (int i = 0; i < DIFFICULTIES.length; i++) {
				if (DIFFICULTIES[i][0].equals(current)) {
					selected = i;
				}
			}
			final float difficultyW = t ? 300 : 150;
			place(content, dropdown(glueList(DIFFICULTIES, 1), selected, true, menuH, textSize,
					choice -> this.activity.preferences.edit().putString(KEY_DIFFICULTY, DIFFICULTIES[choice][0])
							.apply()),
					mx, menuY, difficultyW, menuH);
			mx += difficultyW + gap;
		}
		final float folderW = t ? 120 : 96;
		final LauncherKit.GlueButton saves = button("", menuH, textSize,
				v -> this.activity.showPage(LauncherActivity.PAGE_LOAD));
		saves.setContentDescription(glueLabel("KEY_LOAD_SAVED_GAME", "Load Saved Game"));
		place(content, saves, mx, menuY, folderW, menuH);
		final float icon = menuH * 0.5f;
		place(content, image(SkinArt.ICON_FOLDER_GOLD, ImageView.ScaleType.FIT_CENTER), mx + ((folderW - icon) / 2),
				menuY + ((menuH - icon) / 2), icon, icon);
		return y + (shield * 0.5f);
	}

	/**
	 * After a campaign mission, as on the Reforged campaign screen: the campaign with Continue on the left, the
	 * campaign's banner with the mission's result on the right.
	 */
	private void showResult(final CampaignCatalog.Entry entry, final GameResult result) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_CAMPAIGN, false);
		sceneBehind(view, entry);
		final FrameLayout content = content(view, campaignExtra());
		addCampaignBlock(content, entry, result);
		final CampaignResult.Layout l = new CampaignResult.Layout(
				b.contentX + campaignListX() + (campaignListW() / 2), b.width, b.height);
		place(view, image(CampaignResult.banner(entry.getSection()), ImageView.ScaleType.FIT_XY), l.x, 0, l.w, l.h);
		final String[] title = CampaignResult.title(result);
		centred(view, glueLabel(title[0], title[1]), l.titleSize, Color.WHITE, l.contentX, l.titleY, l.contentW, l.titleH);
		for (final float dividerY : l.dividerY) {
			place(view, image(SkinArt.RESULT_DIVIDER, ImageView.ScaleType.FIT_XY), l.dividerX, dividerY, l.dividerW,
					l.dividerH);
		}
		for (int i = 0; i < CampaignResult.ROWS.length; i++) {
			final String[] row = CampaignResult.ROWS[i];
			final int[] numbers = CampaignResult.numbers(result.score, i);
			final float y = l.rowY[i];
			place(view, image(row[0], ImageView.ScaleType.FIT_CENTER), l.iconX, y, l.icon, l.icon);
			line(view, upper(glueLabel(row[1], row[2])), l.headingSize, CampaignResult.HEADING_GOLD, l.detailsX,
					y + l.headingY, l.columnW * 2, l.headingH);
			final CharSequence[] labels = { glueLabel("SCORE", "Score"), glueLabel(row[3], row[4]) };
			for (int c = 0; c < 2; c++) {
				final float x = l.detailsX + (c * l.columnW);
				line(view, labels[c], l.labelSize, CampaignResult.LABEL_GREY, x, y + l.labelY, l.columnW, l.labelH);
				line(view, Integer.toString(numbers[c]), l.valueSize, Color.WHITE, x, y + l.valueY, l.columnW,
						l.valueH);
			}
		}
		fitted(centred(view, missionName(entry, result), l.nameSize, Color.WHITE, l.contentX, l.nameY, l.contentW, l.nameH), l.nameSize,
				l.contentW);
		centred(view, result.playTime(), l.durationSize, CampaignResult.HEADING_GOLD, l.contentX, l.durationY,
				l.contentW, l.durationH);
		final String[] difficulty = CampaignResult.difficulty(result.difficulty);
		if (difficulty != null) {
			centred(view, glueLabel(difficulty[1], difficulty[2]), l.difficultySize, CampaignResult.LABEL_GREY,
					l.contentX, l.difficultyY, l.contentW, l.difficultyH);
			place(view, image(difficulty[0], ImageView.ScaleType.FIT_CENTER), l.difficultyIconX, l.difficultyIconY,
					l.difficultyIcon, l.difficultyIcon);
		}
		action(view, 0, glueLabel("BACK", getString(R.string.launcher_back)), v -> {
			this.activity.campaignResult = null;
			showMissions();
		});
		this.activity.setPage(view, LauncherActivity.PAGE_MISSIONS);
	}

	/**
	 * After a campaign mission on the classic skin: the same result and Continue as the Reforged one, in a panel of the
	 * classic frame where the mission list stands, the campaign's name under the scene.
	 */
	private void showClassicResult(final CampaignCatalog.Entry entry, final GameResult result) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout view = this.activity.homePage.shell(LauncherActivity.RAIL_CAMPAIGN, false);
		sceneBehind(view, entry);
		final FrameLayout content = content(view, campaignExtra());
		addClassicName(content, entry.getData());
		final CampaignResult.ClassicLayout l = new CampaignResult.ClassicLayout(campaignListX(), 0, campaignListW(),
				b.contentH + campaignExtra(), b.pageFrame, b.buttonH);
		framed(content, l.x, l.y, l.w, l.h);
		final int gold = this.activity.art.gold();
		final int text = this.activity.art.text();
		final int text2 = this.activity.art.text2();
		final String[] title = CampaignResult.title(result);
		centred(content, glueLabel(title[0], title[1]), l.titleSize, gold, l.contentX, l.titleY, l.contentW,
				l.titleH);
		for (final float lineY : l.lineY) {
			classicLine(content, l.contentX, lineY, l.contentW, l.lineH);
		}
		for (int i = 0; i < CampaignResult.ROWS.length; i++) {
			final String[] row = CampaignResult.ROWS[i];
			final int[] numbers = CampaignResult.numbers(result.score, i);
			final float y = l.rowY[i];
			fitted(line(content, glueLabel(row[1], row[2]), l.headingSize, gold, l.contentX, y, l.headingW, l.rowH),
					l.headingSize, l.headingW);
			final CharSequence[] labels = { glueLabel("SCORE", "Score"), glueLabel(row[3], row[4]) };
			for (int c = 0; c < 2; c++) {
				line(content, labels[c], l.labelSize, text2, l.columnX[c], y + l.labelY, l.columnW, l.labelH);
				line(content, Integer.toString(numbers[c]), l.valueSize, text, l.columnX[c], y + l.valueY, l.columnW,
						l.valueH);
			}
		}
		fitted(centred(content, missionName(entry, result), l.nameSize, text, l.contentX, l.nameY, l.contentW,
				l.nameH), l.nameSize, l.contentW);
		centred(content, result.playTime(), l.durationSize, gold, l.contentX, l.durationY, l.contentW, l.durationH);
		final String[] difficulty = CampaignResult.difficulty(result.difficulty);
		if (difficulty != null) {
			centred(content, glueLabel(difficulty[1], difficulty[2]), l.difficultySize, text2, l.contentX,
					l.difficultyY, l.contentW, l.difficultyH);
		}
		place(content, button(glueLabel("CONTINUE", "Continue"), l.buttonH, b.buttonText,
				v -> continueAfter(entry, result)), l.buttonX, l.buttonY, l.buttonW, l.buttonH);
		action(view, 0, glueLabel("BACK", getString(R.string.launcher_back)), v -> {
			this.activity.campaignResult = null;
			showMissions();
		});
		this.activity.setPage(view, LauncherActivity.PAGE_MISSIONS);
	}

	/** A thin line in the skin's line colour that fades out at both ends, as the classic section headers end. */
	private void classicLine(final FrameLayout parent, final float x, final float y, final float w, final float h) {
		final View line = new View(this.activity);
		final int colour = this.activity.art.line();
		line.setBackground(gradient(false, new int[] { colour & 0x00FFFFFF, colour, colour, colour & 0x00FFFFFF },
				new float[] { 0, 0.2f, 0.8f, 1 }));
		place(parent, line, x, y, w, Math.max(h, 1 / this.activity.scale));
	}

	/** The played mission's name as the list has it, else the map's own name. */
	private CharSequence missionName(final CampaignCatalog.Entry entry, final GameResult result) {
		final CharSequence listed = playedName(entry, result);
		return listed.length() == 0 ? LauncherKit.glueText(result.mapName) : listed;
	}

	/** A line of text in its box, vertically centred. */
	private TextView line(final FrameLayout parent, final CharSequence value, final float size, final int color,
			final float x, final float y, final float w, final float h) {
		final TextView line = text(value, size, color);
		line.setGravity(Gravity.CENTER_VERTICAL);
		place(parent, line, x, y, w, h);
		return line;
	}

	private TextView centred(final FrameLayout parent, final CharSequence value, final float size, final int color,
			final float x, final float y, final float w, final float h) {
		final TextView line = line(parent, value, size, color, x, y, w, h);
		line.setGravity(Gravity.CENTER);
		return line;
	}

	/** The mission's name as the campaign list has it (a map names itself by its file), else empty. */
	private CharSequence playedName(final CampaignCatalog.Entry entry, final GameResult result) {
		final int played = CampaignResult.playedMission(this.activity.campaigns.catalog, entry, result.map);
		if (played < 0) {
			return "";
		}
		final CampaignMission mission = entry.getData().getMissions().get(played);
		return glueLabel(mission.getMissionName(), mission.getMissionName());
	}

	/** Continue after a mission: the next mission after a victory, the same one after a loss, else the chapters. */
	private void continueAfter(final CampaignCatalog.Entry entry, final GameResult result) {
		final int played = CampaignResult.playedMission(this.activity.campaigns.catalog, entry, result.map);
		final int next = CampaignResult.continueMission(entry, this.activity.campaigns.progress, played,
				"victory".equals(result.result));
		this.activity.campaignResult = null;
		if (next >= 0) {
			launchMission(entry, next, LauncherActivity.PAGE_MISSIONS);
		}
		else {
			showMissions();
		}
	}

	/** A line made smaller, down to 60 %, rather than cut when it is wider than the place. */
	private TextView fitted(final TextView line, final float size, final float width) {
		final float natural = line.getPaint().measureText(line.getText().toString());
		final float place = px(width);
		if (natural > place) {
			line.setTextSize(TypedValue.COMPLEX_UNIT_PX,
					size * this.activity.scale * Math.max(0.6f, place / natural));
		}
		return line;
	}

	private float chapterRowH() {
		return this.activity.board.tablet ? 124 : 96;
	}

	/** A Reforged chapter row: its icon (cinematic, won or not yet), the chapter's kind in gold and its name. */
	private View chapterRow(final CampaignMission mission, final boolean chosen, final String icon,
			final View.OnClickListener listener) {
		final float rowH = chapterRowH();
		final float w = campaignListW();
		final FrameLayout row = new FrameLayout(this.activity);
		final float a = rowH * 0.62f;
		place(row, image(icon, ImageView.ScaleType.FIT_CENTER), rowH * 0.12f, (rowH - a) / 2, a, a);
		final float textX = (rowH * 0.24f) + a;
		final float headerSize = rowH * 0.22f;
		final float nameSize = rowH * 0.32f;
		final CharSequence headerText = glueLabel(mission.getHeader(), mission.getHeader());
		final CharSequence nameText = glueLabel(mission.getMissionName(), mission.getMissionName());
		place(row, text(headerText, headerSize, this.activity.art.gold()), textX,
				(rowH * 0.1f) + (headerSize * 0.15f), w - textX - 8, -1);
		final TextView title = text(nameText, nameSize, chosen ? this.activity.art.gold() : this.activity.art.text());
		place(row, title, textX, (rowH * 0.42f) + (nameSize * 0.15f), w - textX - 8, -1);
		onTap(row, false, listener);
		row.setContentDescription(headerText + " " + nameText);
		return row;
	}

	void launchMission(final CampaignCatalog.Entry entry, final int mission, final int returnPage) {
		this.activity.preferences.edit().putInt(KEY_CAMPAIGN, entry.getNumber()).putInt(KEY_MISSION, mission).apply();
		this.activity.launchGame(LaunchRequest.mission(this.activity.campaigns.catalog, entry, mission,
				this.activity.profiles.campaignMaps(this.activity.profiles.current()),
				this.activity.preferences.getString(KEY_DIFFICULTY, DIFFICULTIES[0][0])), returnPage);
	}
}
