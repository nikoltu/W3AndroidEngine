package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_BANNER;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_EDITION;
import static com.etheller.warsmash.launcher.LauncherKeys.KEY_MUSIC;

import java.io.File;
import java.util.List;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.etheller.warsmash.BuildInfo;
import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * The main menu (Home) with its Continue Campaign banner, and the frame every page shares: the backdrop, the
 * rail, the logo, the profile and on the tablet the footer.
 */
final class HomePage extends LauncherViews {
	/** What the Continue Campaign banner shows: the next mission of the current profile's campaign progress. */
	private static final class BannerMission {
		String scene = "roc_prologue";
		String background = "TutorialBackdrop";
		FogSettings fog;
		String title = "";
		String line = "";
		CampaignCatalog.Entry entry;
		int mission;
	}

	private FrameLayout banner;
	private BackdropView bannerScene;
	private BannerMission bannerMission;
	private boolean continuePending;

	HomePage(final LauncherActivity activity) {
		super(activity);
	}

	/** The main menu scene of the chosen edition, as the engine's menu shows it. */
	private BackdropView editionScene(final boolean roc) {
		final LauncherBoard b = this.activity.board;
		final String scene = roc ? "mainmenu" : "mainmenu_tft";
		final float focus = roc ? 0.05f : 1f;
		return this.activity.sceneBackdrop(homeBackground(scene, focus), scene, focus, LauncherActivity.LAYER_PAGE,
				roc ? "GlueSpriteLayerBackground_V0" : "GlueSpriteLayerBackground_V1", "GlueSpriteLayerBackground",
				null, roc ? 0 : 1, 0, 0, b.width, b.height, -1);
	}

	/**
	 * Everything the launcher pages share: the backdrop, the rail with the shown page's entry pressed, the logo, the
	 * profile and on the tablet Quit and the footer. Home passes no selected entry and gets its scene and banner.
	 */
	FrameLayout shell(final int selected) {
		return shell(selected, true);
	}

	/**
	 * @param editionScene false for a page that puts its own scene behind it (the campaign pages), which the shell
	 *                     must not cover, since a live scene is drawn behind the window; the others show the animated
	 *                     main menu scene of the chosen edition, Home as it is and the rest dimmed
	 */
	FrameLayout shell(final int selected, final boolean editionScene) {
		this.activity.art = new SkinCache.Art(this.activity.cache);
		final LauncherBoard b = this.activity.board;
		final boolean home = selected < 0;
		final boolean roc = "roc".equals(this.activity.preferences.getString(KEY_EDITION, "tft"));
		final FrameLayout view = new FrameLayout(this.activity);
		if (editionScene) {
			view.addView(editionScene(roc), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
			if (!home) {
				final View scrim = new View(this.activity);
				scrim.setBackgroundColor(Color.argb(0x73, 0, 0, 0));
				view.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT));
			}
		}

		final View rail = new View(this.activity);
		final Bitmap stone = this.activity.art.bitmap(SkinArt.RAIL_BACKGROUND);
		if (stone != null) {
			rail.setBackground(new LauncherKit.CoverDrawable(stone, 0.5f, Color.argb(0x33, 0, 0, 0)));
		}
		else {
			rail.setBackground(gradient(false, new int[] { Color.argb(0xDB, 0, 0, 0), Color.argb(0xB3, 0, 0, 0) },
					null));
		}
		place(view, rail, 0, 0, b.railW - 6, b.height);
		final View railEdge = new View(this.activity);
		railEdge.setBackgroundColor(
				this.activity.art.reforged() ? Color.rgb(0x6A, 0x65, 0x5C) : Color.rgb(0x2C, 0x24, 0x16));
		place(view, railEdge, b.railW - 6, 0, 6, b.height);

		if (home) {
			addBanner(view);
		}

		final ImageView logo = new ImageView(this.activity);
		logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
		logo.setImageBitmap(this.activity.art.bitmap(roc ? SkinArt.LOGO_ROC : SkinArt.LOGO_TFT));
		// On Home the logo switches the edition; elsewhere it leads back to Home.
		logo.setContentDescription(getString(home ? R.string.launcher_logo_edition : R.string.launcher_logo_home));
		onTap(logo, false, v -> {
			if (home) {
				this.activity.preferences.edit().putString(KEY_EDITION, roc ? "tft" : "roc").apply();
			}
			showHome();
		});
		if (b.tablet) {
			// Only Home darkens the top of the rail under the logo; the other pages show the rail's stone.
			final View shade = new View(this.activity);
			shade.setVisibility(home ? View.VISIBLE : View.GONE);
			shade.setBackground(gradient(true, new int[] { Color.BLACK, Color.BLACK, Color.TRANSPARENT },
					new float[] { 0, 0.82f, 1 }));
			place(view, shade, 0, 0, b.railW - 6, b.logoY + b.logoH + 30);
			place(view, logo, b.logoX, b.logoY, b.logoW, b.logoH);
		}

		for (int i = 0; i < LauncherActivity.RAIL.length; i++) {
			final int entry = i;
			// The local network (v0.6) does not exist yet; its entry stays in place, disabled.
			final boolean open = entry != LauncherActivity.RAIL_LAN;
			addHolder(view, glue(LauncherActivity.RAIL[i][0], LauncherActivity.RAIL[i][1]),
					b.holderY + (i * b.holderPitch), i == selected,
					open ? v -> this.activity.openEntry(entry) : null);
		}
		if (b.quitY >= 0) {
			addHolder(view, glue("KEY_QUIT", "Quit"), b.quitY, false, v -> this.activity.dialogs.showExit());
			addFooter(view);
		}
		addProfile(view, home);
		if (!b.tablet) {
			// On the phone the logo sits in the top bar, over everything (on Home also over the banner).
			place(view, logo, b.logoX, b.logoY, b.logoW, b.logoH);
		}
		return view;
	}

	/** The still behind Home: the scene's first frame, or the generic loading screen when the data has no recording. */
	private Drawable homeBackground(final String scene, final float focus) {
		final Bitmap still = this.activity.art.poster(scene);
		if (still != null) {
			return new LauncherKit.CoverDrawable(still, focus, 0);
		}
		return sceneStill();
	}

	/** Without a recorded first frame: dark until the live scene shows, the generic screen when none will. */
	Drawable sceneStill() {
		return this.activity.animated() && (this.activity.renderer != null) ? new ColorDrawable(Color.BLACK)
				: pageBackground();
	}

	/** Behind the other pages: the generic loading screen (Reforged: its home screen) under a scrim. */
	private Drawable pageBackground() {
		final Bitmap generic = this.activity.art.bitmap(SkinArt.BACKGROUND);
		if (generic != null) {
			return new LauncherKit.CoverDrawable(generic, 0.5f, Color.argb(0x73, 0, 0, 0));
		}
		return new ColorDrawable(Color.rgb(0x07, 0x0A, 0x18));
	}

	/**
	 * A menu entry as the original main menu draws it: the holder frame with the blue button in its middle. The
	 * Reforged menu has no holders; its blue button fills most of the place.
	 */
	private void addHolder(final FrameLayout parent, final String label, final float y, final boolean selected,
			final View.OnClickListener listener) {
		final LauncherBoard b = this.activity.board;
		final FrameLayout holder = new FrameLayout(this.activity);
		final Bitmap frame = this.activity.art.bitmap(SkinArt.HOLDER);
		final boolean held = (frame != null) && !this.activity.art.reforged();
		if (held) {
			final ImageView image = new ImageView(this.activity);
			image.setScaleType(ImageView.ScaleType.FIT_XY);
			image.setImageBitmap(frame);
			holder.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT));
		}
		final float buttonH = b.holderH * (held ? 0.594f : 0.72f);
		final LauncherKit.GlueButton button = new LauncherKit.GlueButton(this.activity, this.activity.art, px(buttonH),
				b.holderH * 0.594f * 0.52f * this.activity.scale);
		button.setText(LauncherKit.glueText(label));
		button.setDuplicateParentStateEnabled(true);
		if (held) {
			place(holder, button, b.holderW * 0.0455f, b.holderH * 0.188f, b.holderW * 0.909f, buttonH);
		}
		else {
			place(holder, button, b.holderW * 0.04f, b.holderH * 0.14f, b.holderW * 0.92f, buttonH);
		}
		holder.setSelected(selected);
		if (listener != null) {
			holder.setClickable(true);
			onTap(holder, true, listener);
		}
		else {
			holder.setEnabled(false);
			button.setEnabled(false);
		}
		holder.setContentDescription(LauncherKit.glueText(label).toString());
		place(parent, holder, b.holderX, y, b.holderW, b.holderH);
	}

	void showHome() {
		final FrameLayout view = shell(-1);
		this.activity.setPage(view, LauncherActivity.PAGE_HOME);
		if (!this.activity.profiles.any()) {
			this.activity.profilePage.showFirstProfile();
		}
		if (this.activity.campaigns == null) {
			final int generation = this.activity.generation;
			this.activity.catalog.campaigns(found -> {
				if (found == null) {
					return;
				}
				this.activity.campaigns = found;
				if ((generation == this.activity.generation) && (this.activity.shown == LauncherActivity.PAGE_HOME)) {
					refillBanner();
				}
			});
		}
	}

	/** The campaign the progress last chose (else the one played last) and its furthest open mission. */
	private BannerMission nextMission() {
		final CampaignCatalog catalog = this.activity.campaigns.catalog;
		final CCampaignProgress.Snapshot progress = this.activity.campaigns.progress;
		final CampaignCatalog.Entry entry = CampaignChoice.playedCampaign(catalog, progress);
		if ((entry == null) || entry.getData().getMissions().isEmpty()) {
			return null;
		}
		final List<CampaignMission> missions = entry.getData().getMissions();
		int mission = 0;
		for (int i = 0; i < missions.size(); i++) {
			if (CampaignCatalog.isMissionOpen(entry, i, progress)) {
				mission = i;
			}
		}
		final BannerMission next = new BannerMission();
		next.entry = entry;
		next.mission = mission;
		next.scene = CampaignChoice.campaignScene(entry.getSection());
		next.background = entry.getData().getBackground();
		next.fog = entry.getData().getBackgroundFogSettings();
		next.title = glueLabel(missions.get(mission).getMissionName(), missions.get(mission).getMissionName())
				.toString();
		next.line = glueLabel(entry.getData().getHeader(), entry.getData().getHeader()) + " · "
				+ glueLabel(missions.get(mission).getHeader(), missions.get(mission).getHeader());
		return next;
	}

	/** What the banner showed last, until the progress is read again; without it the Prologue's first chapter. */
	private BannerMission keptMission() {
		final BannerMission kept = new BannerMission();
		final String[] parts = this.activity.preferences.getString(KEY_BANNER, "").split("\t", -1);
		if (parts.length == 4) {
			kept.scene = parts[0];
			kept.background = parts[1];
			kept.title = parts[2];
			kept.line = parts[3];
			return kept;
		}
		kept.title = glue("CAMP_MAP_PROLOGUE01", "");
		final String campaign = glue("CAMP_TUTORIAL_NAME", "");
		final String chapter = glue("CAMP_CHAPTER_1", getString(R.string.launcher_chapter_one));
		kept.line = campaign.isEmpty() ? chapter : campaign + " · " + chapter;
		return kept;
	}

	/** Continue Campaign: the next mission's scene in the skin's frame along the right edge. */
	private void addBanner(final FrameLayout parent) {
		final LauncherBoard b = this.activity.board;
		this.banner = new FrameLayout(this.activity);
		place(parent, this.banner, b.width - b.bannerW, 0, b.bannerW, b.height);
		this.continuePending = false;
		fillBanner(this.activity.campaigns != null ? nextMission() : null);
	}

	/** The banner once the progress is read: the next mission, and its live scene. */
	private void refillBanner() {
		if (this.bannerScene != null) {
			this.bannerScene.stop();
			this.activity.backdrops.remove(this.bannerScene);
		}
		fillBanner(nextMission());
		this.activity.backdrops.addAll(this.activity.building);
		if (this.activity.resumed) {
			for (final BackdropView backdrop : this.activity.building) {
				backdrop.play();
			}
		}
		this.activity.building.clear();
		this.activity.showScene(LauncherActivity.LAYER_BANNER,
				this.activity.buildingScenes[LauncherActivity.LAYER_BANNER],
				this.activity.buildingSceneViews[LauncherActivity.LAYER_BANNER]);
		this.activity.buildingScenes[LauncherActivity.LAYER_BANNER] = null;
		this.activity.buildingSceneViews[LauncherActivity.LAYER_BANNER] = null;
		if (this.continuePending) {
			continueCampaign();
		}
	}

	private void fillBanner(final BannerMission next) {
		final LauncherBoard b = this.activity.board;
		final boolean live = next != null;
		final BannerMission shown = live ? next : keptMission();
		this.bannerMission = next;
		if (live) {
			this.activity.preferences.edit().putString(KEY_BANNER, shown.scene + "\t" + shown.background + "\t"
					+ shown.title.replace('\t', ' ') + "\t" + shown.line.replace('\t', ' ')).apply();
		}
		final FrameLayout box = this.banner;
		box.removeAllViews();
		final float x = b.width - b.bannerW;
		final float innerW = b.bannerW - b.frame;
		final float innerH = b.height - b.frame;
		final Bitmap recorded = this.activity.art.poster(shown.scene);
		final float focus;
		final Drawable still;
		if (recorded != null) {
			// The Reforged scenes have their figures near the middle; the classic ones are framed on the model.
			focus = this.activity.art.reforged() ? 0.3f
					: CampaignChoice.modelFocus(CampaignChoice.modelX(shown.scene), innerW / innerH,
							recorded.getWidth() / (float) recorded.getHeight());
			still = new LauncherKit.CoverDrawable(recorded, focus, 0);
		}
		else {
			focus = 0.5f;
			still = new LauncherKit.CoverDrawable(this.activity.art.bitmap(SkinArt.CAMPAIGN_POSTER), 0.5f, 0);
		}
		if (live) {
			this.bannerScene = this.activity.sceneBackdrop(still, shown.scene, focus, LauncherActivity.LAYER_BANNER,
					shown.background, null,
					shown.fog, 0, x + (b.frame / 2), b.frame / 2, innerW, innerH, CampaignChoice.modelX(shown.scene));
		}
		else {
			this.bannerScene = this.activity.backdrop(still, shown.scene, focus);
		}
		place(box, this.bannerScene, b.frame / 2, b.frame / 2, innerW, innerH);
		final View shade = new View(this.activity);
		shade.setBackground(gradient(true, new int[] { Color.TRANSPARENT, Color.TRANSPARENT,
			Color.argb(0xE6, 0, 0, 0), Color.argb(0xE6, 0, 0, 0) }, new float[] { 0, 0.46f, 0.84f, 1 }));
		place(box, shade, b.frame / 2, b.frame / 2, innerW, innerH);
		final View frame = new View(this.activity);
		frame.setBackground(new LauncherKit.FrameDrawable(this.activity.art.bitmap(SkinArt.PANEL_BORDER), px(b.frame))
				.line(this.activity.art.line()));
		place(box, frame, 0, 0, b.bannerW, b.height);

		final float ix = b.tablet ? 64 : 30;
		final float iw = b.bannerW - (b.tablet ? 128 : 60);
		final float bh = b.tablet ? 112 : 96;
		final float by = b.height - (b.tablet ? 72 : 24) - bh;
		place(box, text(getString(R.string.launcher_continue_campaign), b.tablet ? 32 : 22, this.activity.art.gold()),
				ix,
				by - (b.tablet ? 300 : 178), iw, -1);
		final TextView mission = text(LauncherKit.glueText(shown.title), b.tablet ? 60 : 34, this.activity.art.text());
		mission.setSingleLine(false);
		mission.setMaxLines(2);
		mission.setLineSpacing(0, 1.1f);
		place(box, mission, ix, by - (b.tablet ? 250 : 150), iw, b.tablet ? 140 : 84);
		place(box, text(LauncherKit.glueText(shown.line), b.tablet ? 30 : 20, this.activity.art.text2()), ix,
				by - (b.tablet ? 88 : 50), iw, -1);
		final View hit = new View(this.activity);
		onTap(hit, true, v -> continueCampaign());
		hit.setContentDescription(getString(R.string.launcher_continue_campaign));
		box.addView(hit, frameParams(0, 0, b.bannerW, b.height));
		place(box, button(LauncherKit.glueText(glue("CONTINUE", "Continue")), bh, b.tablet ? 44 : 30,
				v -> continueCampaign()), ix, by, iw, bh);
	}

	/** Straight into the banner's mission with the difficulty last chosen; before the progress is read, once it is. */
	private void continueCampaign() {
		if (this.activity.launching) {
			return;
		}
		if (this.activity.campaigns == null) {
			this.continuePending = true;
			return;
		}
		this.continuePending = false;
		final BannerMission next = this.bannerMission != null ? this.bannerMission : nextMission();
		if (next == null) {
			this.activity.campaignPage.showCampaign();
			return;
		}
		this.activity.campaignPage.launchMission(next.entry, next.mission, LauncherActivity.PAGE_HOME);
	}

	private void addProfile(final FrameLayout parent, final boolean home) {
		final LauncherBoard b = this.activity.board;
		final LinearLayout chip = new LinearLayout(this.activity);
		chip.setOrientation(LinearLayout.HORIZONTAL);
		chip.setGravity(Gravity.CENTER_VERTICAL);
		final ImageView portrait = new ImageView(this.activity);
		portrait.setScaleType(ImageView.ScaleType.FIT_XY);
		final String profile = this.activity.profiles.current();
		portrait.setImageBitmap(this.activity.profilePage.portraitBitmap(this.activity.profiles.portrait(profile)));
		chip.addView(portrait, new LinearLayout.LayoutParams(px(b.chipH), px(b.chipH)));
		final TextView name = text(this.activity.profiles.any() ? profile : glue("PLAYER", "Player"), b.name,
				this.activity.art.text());
		final LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		nameParams.leftMargin = px(b.tablet ? 28 : 18);
		chip.addView(name, nameParams);
		final float right = home ? b.width - b.bannerW - 40 : b.tablet ? b.width - 40 : b.logoX - 20;
		onTap(chip, false, v -> this.activity.profilePage.showProfile(null));
		chip.setContentDescription(profile);
		place(parent, chip, b.chipX, b.chipY, Math.min(b.tablet ? 1400 : 900, right - b.chipX), b.chipH);
	}

	/** The tablet footer: which game art the data holds, the music box and the versions. */
	private void addFooter(final FrameLayout parent) {
		final LinearLayout row = new LinearLayout(this.activity);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setBackgroundColor(Color.argb(0x8C, 0, 0, 0));
		row.setPadding(px(20), 0, px(12), 0);
		row.addView(indicator(getString(R.string.launcher_classic), true));
		final View gap = new View(this.activity);
		row.addView(gap, new LinearLayout.LayoutParams(px(18), 1));
		row.addView(indicator(getString(R.string.launcher_reforged), SkinCache.hasReforged(this.activity)));
		onTap(row, false, v -> this.activity.optionsPage.showOptions(OptionsPage.TAB_GAME_DATA));
		row.addView(new View(this.activity), new LinearLayout.LayoutParams(0, 1, 1));
		final LinearLayout music = new LinearLayout(this.activity);
		music.setGravity(Gravity.CENTER_VERTICAL);
		music.addView(text(getString(R.string.launcher_music), 28, Color.rgb(0xE6, 0xE0, 0xD2)));
		final LauncherKit.CheckDrawable check = new LauncherKit.CheckDrawable(this.activity.art);
		check.setChecked(this.activity.preferences.getBoolean(KEY_MUSIC, true));
		final View box = new View(this.activity);
		box.setBackground(check);
		final LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(px(52), px(52));
		boxParams.leftMargin = px(12);
		music.addView(box, boxParams);
		music.setMinimumHeight(px(88));
		onTap(music, false, v -> {
			final boolean on = !this.activity.preferences.getBoolean(KEY_MUSIC, true);
			this.activity.preferences.edit().putBoolean(KEY_MUSIC, on).apply();
			check.setChecked(on);
			this.activity.updateMusic();
		});
		row.addView(music);
		place(parent, row, 24, 1600, 592, 88);

		final TextView version = text(buildLine() + dataVersion(), 24, LauncherKit.CAPTION);
		version.setPadding(px(20), 0, 0, 0);
		version.setShadowLayer(0, 0, 0, 0);
		place(parent, version, 24, 1730, 592, 48);
	}

	private View indicator(final String label, final boolean present) {
		final LinearLayout item = new LinearLayout(this.activity);
		item.setGravity(Gravity.CENTER_VERTICAL);
		final GradientDrawable dot = new GradientDrawable();
		dot.setShape(GradientDrawable.OVAL);
		dot.setColor(present ? LauncherKit.OK : LauncherKit.OFF);
		final View mark = new View(this.activity);
		mark.setBackground(dot);
		final LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(px(20), px(20));
		markParams.rightMargin = px(10);
		item.addView(mark, markParams);
		item.addView(text(label, 28, Color.rgb(0xE6, 0xE0, 0xD2)));
		// The dot's colour alone says nothing to a screen reader; the word does.
		item.setContentDescription(
				label + ": " + getString(present ? R.string.options_found : R.string.options_missing));
		return item;
	}

	/** Such as "v0.3.12 (abc1234)", with a marker when the build is not an official one. */
	String buildLine() {
		return BuildInfo.current().displayVersion()
				+ (BuildInfo.current().isOfficial() ? "" : " - " + getString(R.string.build_unofficial));
	}

	String versionName() {
		try {
			return this.activity.getPackageManager().getPackageInfo(this.activity.getPackageName(), 0).versionName;
		}
		catch (final PackageManager.NameNotFoundException e) {
			return "";
		}
	}

	boolean installedGame() {
		return "CASC".equalsIgnoreCase(this.activity.ini.get("DataSources").getField("Type00"));
	}

	String dataFolder() {
		return this.activity.ini.get("DataSources").getField("Path00").replace("\"", "");
	}

	/**
	 * The version and build of the game the data comes from: an installed game's build info; for a folder, the build
	 * info or the data packer's note it was made with, else that the data does not say.
	 */
	String dataVersion() {
		final File folder = new File(dataFolder());
		String version = "";
		if (installedGame()) {
			version = DataFolderCheck.describe(DataFolderCheck.Kind.CASC, folder).replace("version ", "");
		}
		else {
			version = DataFolderCheck.folderGameVersion(folder);
		}
		return " (" + (version.isEmpty() ? getString(R.string.launcher_data_version_unknown) : version) + ")";
	}
}
