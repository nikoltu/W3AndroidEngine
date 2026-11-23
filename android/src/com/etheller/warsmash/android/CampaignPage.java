package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.CAMPAIGN_MAPS;
import static com.etheller.warsmash.launcher.LauncherKeys.DIFFICULTIES;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_CAMPAIGN;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_DIFFICULTY;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MISSION;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

import android.animation.ValueAnimator;
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
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.CampaignResult;
import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.MapIssue;
import com.etheller.warsmash.launcher.ResultMotion;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMenuData;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/** The Campaign page, and the Missions of the campaign chosen with its version and difficulty menus. */
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
		final LauncherBoard b = this.activity.board;
		// The Reforged pictures are composed for a page like this one; only the classic scenes are placed.
		final float bandMid = (b.railW + b.contentX + b.campaignListX()) / 2 / b.width;
		final float[] frame = this.activity.art.reforged() ? new float[] { 0.22f, 1, 1 }
				: CampaignChoice.bandFrame(scene, b.width / b.height, CampaignChoice.RECORDING_ASPECT, bandMid, 0.40f);
		final float focus = frame[0];
		final Bitmap still = this.activity.art.poster(scene);
		// The poster has its own width, so its crop is not the recording's.
		final float stillFocus = (still == null) || this.activity.art.reforged() ? focus
				: CampaignChoice.bandFocus(scene, b.width / b.height, still.getWidth() / (float) still.getHeight(),
						bandMid, frame[1]);
		final Drawable drawable = still != null ? new LauncherKit.CoverDrawable(still, stillFocus, 0)
				: this.activity.homePage.sceneStill();
		view.addView(this.activity.sceneBackdrop(drawable, scene, focus, LauncherActivity.LAYER_PAGE,
				this.activity.backdropField(entry != null ? entry.getData().getBackground() : "TutorialBackdrop"),
				entry != null ? entry.getData().getBackground() : "TutorialBackdrop",
				entry != null ? entry.getData().getBackgroundFogSettings() : null, 0, 0, 0, b.width, b.height, -1,
				frame[1], frame[2]), 0, new FrameLayout.LayoutParams(MATCH, MATCH));
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
		return campaignRow(header, name, chosen, listener, MapIssue.NONE);
	}

	/** A mission whose map the game cannot play shows the reason in the header's place and is greyed out. */
	private View campaignRow(final String header, final String name, final boolean chosen,
			final View.OnClickListener listener, final MapIssue issue) {
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
		final CharSequence headerText = issue != MapIssue.NONE ? issueText(issue, true)
				: reforged ? upper(glueLabel(header, header)) : glueLabel(header, header);
		final CharSequence nameText = reforged ? upper(glueLabel(name, name)) : glueLabel(name, name);
		place(row, fitWidth(text(headerText, headerSize, chosen ? this.activity.art.text() : this.activity.art.gold()),
				0.7f), textX, (rowH * 0.04f) + (headerSize * 0.15f), w - textX - 8, -1);
		final TextView title = fitWidth(text(nameText, nameSize,
				chosen ? this.activity.art.gold() : this.activity.art.text()), 0.6f);
		if (chosen) {
			title.setShadowLayer(nameSize * this.activity.scale * 0.35f, 0, 0, this.activity.art.gold());
		}
		place(row, title, textX, (rowH * 0.36f) + (nameSize * 0.15f), w - textX - 8, -1);
		if (listener != null) {
			onTap(row, false, listener);
		}
		if ((listener == null) || (issue != MapIssue.NONE)) {
			// A campaign still to come or a mission without a playable map: without colour, darker and half seen.
			mute(row);
		}
		row.setContentDescription(headerText + " " + nameText);
		return row;
	}

	private static void mute(final View row) {
		final ColorMatrix muted = new ColorMatrix();
		muted.setSaturation(0);
		muted.postConcat(new ColorMatrix(new float[] { 0.8f, 0, 0, 0, 0, 0, 0.8f, 0, 0, 0, 0, 0, 0.8f, 0, 0, 0,
			0, 0, 1, 0 }));
		final Paint paint = new Paint();
		paint.setColorFilter(new ColorMatrixColorFilter(muted));
		row.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
		row.setAlpha(0.5f);
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
		this.activity.setPage(view, LauncherActivity.PAGE_CAMPAIGN);
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
		final boolean reforgedMaps = reforgedMaps();
		final List<CampaignMission> missions = entry.getMissions(reforgedMaps);
		// The mission last played, else the first open one not yet won (after a victory the one it opened), else the
		// first of a campaign that is won all through.
		int chosen = this.activity.preferences.getInt(KEY_MISSION, -1);
		if ((chosen < 0) || (chosen >= missions.size()) || !CampaignCatalog.isMissionOpen(entry, chosen, progress)) {
			chosen = 0;
			for (int i = 0; i < missions.size(); i++) {
				if (CampaignCatalog.isMissionOpen(entry, i, progress)
						&& !CampaignCatalog.isMissionWon(entry, i, progress, reforgedMaps)) {
					chosen = i;
					break;
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
		// The campaign's name with the version and difficulty menus stand left of the list in both looks; a short
		// chapter list stands level with the name.
		final float middle = addCampaignBlock(content, entry);
		final float total = shown.size() * rowH;
		listY = Math.max(0, Math.min(listH - total, middle - (total / 2)));
		final LinearLayout rows = scrollList(content, campaignListX(), listY, campaignListW(), listH - listY);
		View chosenRow = null;
		for (int i = 0; i < shown.size(); i++) {
			final CampaignMission mission = shown.get(i);
			final int number = numbers.get(i);
			final MapIssue issue = number < 0 ? MapIssue.NONE : missionIssue(entry, number);
			final View.OnClickListener tap = number < 0 ? v -> playMovies(mission.getMovies())
					: v -> launchMission(entry, number, LauncherActivity.PAGE_MISSIONS);
			final View row;
			if (reforged) {
				final boolean won = (number >= 0)
						&& CampaignCatalog.isMissionWon(entry, number, progress, reforgedMaps);
				row = chapterRow(mission, number == chosen,
						number < 0 ? SkinArt.CHAPTER_CINEMATIC
								: CampaignResult.chapterIcon(won, progress.getMissionWonDifficulty(entry.getNumber(), number)),
						tap, issue);
			}
			else {
				row = campaignRow(mission.getHeader(), mission.getMissionName(), number == chosen, tap, issue);
			}
			rows.addView(row, new LinearLayout.LayoutParams(MATCH, px(rowH)));
			if (number == chosen) {
				chosenRow = row;
			}
		}
		if (chosenRow != null) {
			reveal(chosenRow);
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

	/** A line set in MasterFontBold, as the Reforged result is. */
	private TextView bold(final TextView line) {
		line.setTypeface(this.activity.art.fontBold());
		return line;
	}

	/**
	 * Plays {@link ResultMotion}: the banner and its title fall from above the screen, the other views fade in. The
	 * views start in their first frame; with the system's animations off they are shown as they end.
	 */
	private void animateResult(final View page, final View banner, final View title, final int bannerHeight,
			final List<View> fading) {
		if (!ValueAnimator.areAnimatorsEnabled()) {
			return;
		}
		final ValueAnimator clock = ValueAnimator.ofInt(0, (int) ResultMotion.totalMillis());
		clock.setInterpolator(new LinearInterpolator());
		clock.setDuration(ResultMotion.totalMillis());
		final LongConsumer frame = elapsed -> {
			final float fall = ResultMotion.bannerOffset(elapsed) * bannerHeight;
			banner.setTranslationY(fall);
			title.setTranslationY(fall);
			final float alpha = ResultMotion.contentAlpha(elapsed);
			for (final View view : fading) {
				view.setAlpha(alpha);
			}
		};
		frame.accept(0);
		clock.addUpdateListener(animation -> frame.accept(animation.getCurrentPlayTime()));
		page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			@Override
			public void onViewAttachedToWindow(final View v) {
				clock.start();
			}

			@Override
			public void onViewDetachedFromWindow(final View v) {
				clock.cancel();
			}
		});
		if (page.isAttachedToWindow()) {
			clock.start();
		}
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
		// The classic skin has no pictures of the Reforged interface: without the shield the texts start at the edge.
		final String shieldArt = version == 0 ? SkinArt.VERSION_REFORGED : SkinArt.VERSION_CLASSIC;
		final boolean shielded = this.activity.art.has(shieldArt);
		final String campaignTip = getString(versions ? R.string.launcher_campaign_maps_tip
				: R.string.launcher_campaign_maps_missing_tip);
		if (shielded) {
			final ImageView mark = image(shieldArt, ImageView.ScaleType.FIT_CENTER);
			holdForTip(mark, campaignTip);
			place(content, mark, left, y, shield, shield);
		}
		final float tx = left + (shielded ? shield + (t ? 20 : 10) : (t ? 24 : 8));
		final TextView header = fitted(text(upper(glueLabel(data.getHeader(), data.getHeader())), headerSize,
				this.activity.art.gold()), headerSize, right - tx);
		holdForTip(header, campaignTip);
		place(content, header, tx, y + (shield * 0.1f), right - tx, -1);
		final TextView name = fitted(text(upper(glueLabel(data.getName(), data.getName())), nameSize,
				this.activity.art.text()), nameSize, right - tx);
		holdForTip(name, campaignTip);
		place(content, name, tx, y + (shield * 0.5f), right - tx, -1);
		final float menuY = y + shield + gap;
		final float textSize = t ? 32 : 22;
		// On the phone the menus start under the shield: beside it the list column leaves too little for the labels.
		float mx = t ? tx : left;
		if (result != null) {
			final float continueW = t ? 340 : 190;
			final String label = CampaignResult.buttonLabel(result);
			place(content, button(upper(glueLabel(label)), menuH, textSize,
					v -> continueAfter(entry, result)), mx, menuY, continueW, menuH);
			mx += continueW + gap;
		}
		else if (versions) {
			final float versionW = t ? 400 : 230;
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
			final float difficultyW = t ? 300 : 190;
			place(content, dropdown(glueList(DIFFICULTIES, 1), selected, true, menuH, textSize,
					choice -> this.activity.preferences.edit().putString(KEY_DIFFICULTY, DIFFICULTIES[choice][0])
							.apply()),
					mx, menuY, difficultyW, menuH);
			mx += difficultyW + gap;
		}
		if (this.activity.art.has(SkinArt.ICON_FOLDER_GOLD)) {
			final float folderW = t ? 120 : 84;
			final LauncherKit.GlueButton saves = button("", menuH, textSize,
					v -> this.activity.showPage(LauncherActivity.PAGE_LOAD));
			saves.setContentDescription(glueLabel("KEY_LOAD_SAVED_GAME"));
			place(content, saves, mx, menuY, folderW, menuH);
			final float icon = menuH * 0.5f;
			place(content, image(SkinArt.ICON_FOLDER_GOLD, ImageView.ScaleType.FIT_CENTER),
					mx + ((folderW - icon) / 2), menuY + ((menuH - icon) / 2), icon, icon);
		}
		return y + (shield * 0.5f);
	}

	/** Holding the campaign's shield or name tells how the campaign version comes about. */
	private void holdForTip(final View view, final String tip) {
		view.setOnLongClickListener(v -> {
			showHint(view, tip);
			return true;
		});
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
		final float centre = b.contentX + campaignListX() + (campaignListW() / 2);
		final CampaignResult.Layout first = new CampaignResult.Layout(centre, b.width, b.height);
		// A name wider than the banner goes on in a second line, which moves what follows.
		final String missionName = missionName(entry, result).toString();
		final TextView probe = bold(text("", first.nameSize, Color.WHITE));
		final List<String> nameLines = CampaignResult.nameLines(missionName,
				line -> android.text.Layout.getDesiredWidth(smallCaps(line), probe.getPaint()), px(first.contentW));
		final CampaignResult.Layout l = new CampaignResult.Layout(centre, b.width, b.height, nameLines.size());
		// The banner falls in with its title; the numbers, the mission and the difficulty fade in after it.
		final List<View> fading = new ArrayList<>();
		final ImageView banner = image(CampaignResult.banner(entry.getSection()), ImageView.ScaleType.FIT_XY);
		place(view, banner, l.x, l.bannerY, l.w, l.h);
		final String title = CampaignResult.title(result);
		final TextView titleView = bold(centred(view, smallCaps(glueLabel(title)), l.titleSize, Color.WHITE,
				l.contentX, l.titleY, l.contentW, l.titleH));
		for (final float dividerY : l.dividerY) {
			final ImageView divider = image(SkinArt.RESULT_DIVIDER, ImageView.ScaleType.FIT_XY);
			place(view, divider, l.dividerX, dividerY, l.dividerW, l.dividerH);
			fading.add(divider);
		}
		for (int i = 0; i < CampaignResult.ROWS.length; i++) {
			final String[] row = CampaignResult.ROWS[i];
			final int[] numbers = CampaignResult.numbers(result.score, i);
			final float y = l.rowY[i];
			final ImageView icon = image(row[0], ImageView.ScaleType.FIT_CENTER);
			place(view, icon, l.iconX, y, l.icon, l.icon);
			fading.add(icon);
			fading.add(bold(line(view, upper(glueLabel(row[1])), l.headingSize, CampaignResult.HEADING_GOLD,
					l.detailsX, y + l.headingY, l.columnW * 2, l.headingH)));
			final CharSequence[] labels = { glueLabel("SCORE"), glueLabel(row[2]) };
			for (int c = 0; c < 2; c++) {
				final float x = l.detailsX + (c * l.columnW);
				fading.add(line(view, smallCaps(labels[c]), l.labelSize, CampaignResult.LABEL_GREY, x, y + l.labelY,
						l.columnW, l.labelH));
				fading.add(line(view, Integer.toString(numbers[c]), l.valueSize, Color.WHITE, x, y + l.valueY,
						l.columnW, l.valueH));
			}
		}
		for (int i = 0; i < nameLines.size(); i++) {
			fading.add(fitted(bold(centred(view, smallCaps(nameLines.get(i)), l.nameSize, Color.WHITE, l.contentX,
					l.nameY + (i * l.nameStep), l.contentW, l.nameH)), l.nameSize, l.contentW));
		}
		fading.add(centred(view, result.playTime(), l.durationSize, CampaignResult.HEADING_GOLD, l.contentX,
				l.durationY, l.contentW, l.durationH));
		final String[] difficulty = CampaignResult.difficulty(result.difficulty);
		if (difficulty != null) {
			fading.add(bold(centred(view, smallCaps(glueLabel(difficulty[1])), l.difficultySize,
					CampaignResult.LABEL_GREY, l.contentX, l.difficultyY, l.contentW, l.difficultyH)));
			final ImageView mark = image(difficulty[0], ImageView.ScaleType.FIT_CENTER);
			place(view, mark, l.difficultyIconX, l.difficultyIconY, l.difficultyIcon, l.difficultyIcon);
			fading.add(mark);
		}
		animateResult(view, banner, titleView, px(l.h), fading);
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
		final String title = CampaignResult.title(result);
		centred(content, glueLabel(title), l.titleSize, gold, l.contentX, l.titleY, l.contentW,
				l.titleH);
		for (final float lineY : l.lineY) {
			classicLine(content, l.contentX, lineY, l.contentW, l.lineH);
		}
		for (int i = 0; i < CampaignResult.ROWS.length; i++) {
			final String[] row = CampaignResult.ROWS[i];
			final int[] numbers = CampaignResult.numbers(result.score, i);
			final float y = l.rowY[i];
			fitted(line(content, glueLabel(row[1]), l.headingSize, gold, l.contentX, y, l.headingW, l.rowH),
					l.headingSize, l.headingW);
			final CharSequence[] labels = { glueLabel("SCORE"), glueLabel(row[2]) };
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
			centred(content, glueLabel(difficulty[1]), l.difficultySize, text2, l.contentX,
					l.difficultyY, l.contentW, l.difficultyH);
		}
		final String label = CampaignResult.buttonLabel(result);
		place(content, button(glueLabel(label), l.buttonH, b.buttonText,
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
		final CampaignMission mission = entry.getMissions(true).get(played);
		return glueLabel(mission.getMissionName(), mission.getMissionName());
	}

	/** Continue after a mission: the next mission after a victory, the same one after a loss, else the chapters. */
	private void continueAfter(final CampaignCatalog.Entry entry, final GameResult result) {
		final int played = CampaignResult.playedMission(this.activity.campaigns.catalog, entry, result.map);
		final int next = CampaignResult.continueMission(entry, this.activity.campaigns.progress, played,
				"victory".equals(result.result), reforgedMaps());
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
			final View.OnClickListener listener, final MapIssue issue) {
		final float rowH = chapterRowH();
		final float w = campaignListW();
		final FrameLayout row = new FrameLayout(this.activity);
		final float a = rowH * 0.62f;
		place(row, image(icon, ImageView.ScaleType.FIT_CENTER), rowH * 0.12f, (rowH - a) / 2, a, a);
		final float textX = (rowH * 0.24f) + a;
		final float headerSize = rowH * 0.22f;
		final float nameSize = rowH * 0.32f;
		final CharSequence headerText = issue != MapIssue.NONE ? issueText(issue, true)
				: glueLabel(mission.getHeader(), mission.getHeader());
		final CharSequence nameText = glueLabel(mission.getMissionName(), mission.getMissionName());
		place(row, fitWidth(text(headerText, headerSize, this.activity.art.gold()), 0.6f), textX,
				(rowH * 0.1f) + (headerSize * 0.15f), w - textX - 8, -1);
		final TextView title = fitWidth(text(nameText, nameSize,
				chosen ? this.activity.art.gold() : this.activity.art.text()), 0.6f);
		place(row, title, textX, (rowH * 0.42f) + (nameSize * 0.15f), w - textX - 8, -1);
		onTap(row, false, listener);
		if (issue != MapIssue.NONE) {
			mute(row);
		}
		row.setContentDescription(headerText + " " + nameText);
		return row;
	}

	/** Whether the current profile plays the Reforged campaign maps, whose mission lists the pages show. */
	private boolean reforgedMaps() {
		return this.activity.campaigns
				.reforgedMaps(this.activity.profiles.campaignMaps(this.activity.profiles.current()));
	}

	private MapIssue missionIssue(final CampaignCatalog.Entry entry, final int mission) {
		return this.activity.campaigns.issue(entry, mission,
				this.activity.profiles.campaignMaps(this.activity.profiles.current()));
	}

	void launchMission(final CampaignCatalog.Entry entry, final int mission, final int returnPage) {
		final MapIssue issue = missionIssue(entry, mission);
		if (issue != MapIssue.NONE) {
			this.activity.dialogs.showMessage(getString(R.string.game_not_started), issueText(issue, false));
			return;
		}
		this.activity.preferences.edit().putInt(KEY_CAMPAIGN, entry.getNumber()).putInt(KEY_MISSION, mission).apply();
		this.activity.launchGame(LaunchRequest.mission(this.activity.campaigns.catalog, entry, mission,
				this.activity.profiles.campaignMaps(this.activity.profiles.current()),
				this.activity.preferences.getString(KEY_DIFFICULTY, DIFFICULTIES[0][0])), returnPage);
	}
}
