package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.launcher.LauncherSlot;
import com.etheller.warsmash.parsers.fdf.GameSkin;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.parsers.w3x.w3i.Force;
import com.etheller.warsmash.parsers.w3x.w3i.Player;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.StandardObjectData;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreference;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

import net.warsmash.uberserver.LobbyPlayerType;

/**
 * The loading screen of a map: the glue one (backdrop, texts, bar, melee panel with minimap and players) or the
 * Reforged one drawn in its place, its preview while a launcher's menu is still being built, and the wait for a tap
 * once the map is loaded.
 */
final class MenuLoadingScreen {
	private static final String TAP_TO_CONTINUE = "TAP TO CONTINUE";
	private static final String PRESS_TO_CONTINUE = "PRESS ANY KEY TO CONTINUE";
	private static final float TAP_PULSE_SECONDS = 1.2f;
	private static final float PREVIEW_BAR_SHARE = 0.25f;
	private final MenuUI menu;
	private UIFrame loadingFrame;
	private UIFrame loadingCustomPanel;
	private UIFrame loadingMeleePanel;
	private TextureFrame loadingMinimapTexture;
	private AbstractRenderableFrame loadingMinimapImage;
	private Texture loadingMinimapUnmanagedTexture;
	/** The map preview the loading screen shows. */
	private Texture loadingPreviewTexture;
	private final List<UIFrame> loadingPlayerSlots = new ArrayList<>();
	private StringFrame loadingTitleText;
	private StringFrame loadingSubtitleText;
	private StringFrame loadingText;
	private StringFrame loadingBarText;
	private Color loadingBarTextColor;
	private SpriteFrame loadingBar;
	private SpriteFrame loadingBackground;
	private boolean waitForTap;
	/** The profile a launcher plays with, named before the menu takes it on. */
	private String startupProfile;
	/** The loading screen of the map a launcher starts, shown while the menu is still being built. */
	private boolean previewingLoading;
	/** The melee backdrop model of the previewed screen, null for a campaign one. */
	private String previewedBackground;
	/** The part of the loading bar the menu build filled before the map itself loads. */
	private float loadingBarStart;
	private boolean loadingMapDone;
	private boolean continueTapped;
	private float tapPulse;
	/** Whether maps load on the Reforged loading screens (the Reforged skin of a launcher). */
	private boolean reforgedLoadingScreens;
	/** The local player's portrait on the Reforged loading screens, from 1; 0 for the default one. */
	private int reforgedPortrait;
	/** The Reforged loading screen shown instead of the glue one; null while the glue one is used. */
	private ReforgedLoadingScreen reforgedLoading;
	private final Matrix4 savedProjection = new Matrix4();

	MenuLoadingScreen(final MenuUI menu) {
		this.menu = menu;
	}

	/** The loading screen frame; built once, early when a launcher previews it. */
	void create() {
		if (this.loadingFrame != null) {
			return;
		}
		final GameUI rootFrame = this.menu.rootFrame;
		this.loadingFrame = rootFrame.createFrame("Loading", rootFrame, 0, 0);
		((AbstractRenderableFrame) this.loadingFrame).clearFramePointAssignments();
		this.menu.anchorGlueLayer(this.loadingFrame, FramePoint.CENTER);
		this.loadingFrame.setVisible(false);
		this.loadingCustomPanel = rootFrame.getFrameByName("LoadingCustomPanel", 0);
		this.loadingCustomPanel.setVisible(false);
		this.loadingTitleText = (StringFrame) rootFrame.getFrameByName("LoadingTitleText", 0);
		this.loadingSubtitleText = (StringFrame) rootFrame.getFrameByName("LoadingSubtitleText", 0);
		this.loadingText = (StringFrame) rootFrame.getFrameByName("LoadingText", 0);
		this.loadingBar = (SpriteFrame) rootFrame.getFrameByName("LoadingBar", 0);
		this.loadingBarText = (StringFrame) rootFrame.getFrameByName("LoadingBarText", 0);
		this.loadingBackground = (SpriteFrame) rootFrame.getFrameByName("LoadingBackground", 0);

		this.loadingMeleePanel = rootFrame.getFrameByName("LoadingMeleePanel", 0);
		this.loadingMeleePanel.setVisible(false);
		// The skirmish map info pane later registers a frame with the same name, so this one is kept here.
		this.loadingMinimapImage = (AbstractRenderableFrame) rootFrame.getFrameByName("MinimapImage", 0);
		this.loadingMinimapTexture = rootFrame.createTextureFrame("LoadingMinimapTexture", this.loadingMinimapImage,
				false, TextureFrame.DEFAULT_TEX_COORDS);
		rootFrame.remove(this.loadingMinimapTexture);
		((SimpleFrame) this.loadingMeleePanel).add(this.loadingMinimapTexture);
		this.loadingMinimapTexture.setSetAllPoints(true);
	}

	/** Whether the loading screen of a map is up, from the preview until the game takes over. */
	boolean isShown() {
		return this.previewingLoading || ((this.loadingFrame != null) && this.loadingFrame.isVisible());
	}

	boolean isPreviewing() {
		return this.previewingLoading;
	}

	void setStartupProfile(final String profile) {
		this.startupProfile = (profile == null) || profile.isEmpty() ? null : profile;
	}

	void setWaitForTap(final boolean waitForTap) {
		this.waitForTap = waitForTap;
	}

	boolean isReforgedLoadingScreens() {
		return this.reforgedLoadingScreens;
	}

	void setReforgedLoadingScreens(final boolean reforgedLoadingScreens) {
		this.reforgedLoadingScreens = reforgedLoadingScreens;
	}

	void setReforgedPortrait(final int portrait) {
		this.reforgedPortrait = portrait;
	}

	boolean preview(final String mapFilename, final String startupSlots) {
		try {
			final War3Map map = War3MapViewer.beginLoadingMap(this.menu.dataSource, mapFilename);
			final War3MapW3i mapInfo = map.readMapInformation();
			final int campaignBackground = mapInfo.getCampaignBackground();
			final WTS wts = Warcraft3MapObjectData.loadWTS(map);
			create();
			final String mapModel = mapLoadingModel(mapInfo);
			if (mapModel != null) {
				this.previewedBackground = mapModel;
				fillLoadingScreen(mapModel, 0, mapInfo, wts, map);
				fillMapLoadingPanels(map, mapInfo, wts);
				fillReforgedModelLoadingScreen(mapInfo, wts, previewRace(mapInfo, startupSlots));
			}
			else if (campaignBackground < 0) {
				this.previewedBackground = meleeLoadingBackground(map, previewSkinKey(mapInfo, startupSlots));
				fillLoadingScreen(this.previewedBackground, 0, mapInfo, wts);
				fillMapLoadingPanels(map, mapInfo, wts);
				fillReforgedMapLoadingScreen(map, mapInfo, wts, -1);
				if (this.reforgedLoading != null) {
					this.reforgedLoading.setTeams(previewLoadingTeams(mapInfo, wts, startupSlots));
				}
			}
			else {
				final DataTable worldEditData = new StandardObjectData(map).getWorldEditData();
				fillCampaignLoadingScreen(worldEditData, campaignBackground, mapInfo, wts);
			}
			this.loadingFrame.setVisible(true);
			this.loadingBar.setVisible(this.reforgedLoading == null);
			this.previewingLoading = true;
			return true;
		}
		catch (final IOException | RuntimeException e) {
			Log.error(e);
			return false;
		}
	}

	void setPreviewProgress(final float ratio) {
		if (this.previewingLoading) {
			this.loadingBarStart = PREVIEW_BAR_SHARE * ratio;
			setLoadingRatio(this.loadingBarStart);
		}
	}

	void showTapToContinue(final float deltaTime) {
		if (this.tapPulse == 0) {
			setLoadingRatio(1.0f);
			this.menu.rootFrame.setText(this.loadingBarText, continueText());
			this.loadingBarText.setColor(new Color(0.99f, 0.827f, 0.0705f, 1.0f));
		}
		this.tapPulse = (this.tapPulse + deltaTime) % TAP_PULSE_SECONDS;
		final float alpha = 0.6f + (0.4f * (float) Math.cos((this.tapPulse / TAP_PULSE_SECONDS) * 2 * Math.PI));
		this.loadingBarText.setAlpha(alpha);
		if (this.reforgedLoading != null) {
			this.reforgedLoading.setBarText(continueText(), alpha);
		}
	}

	/** With a keyboard and mouse the game's own line, Press any key to continue, instead of the touch one. */
	private String continueText() {
		if (Gdx.app.getType() != Application.ApplicationType.Desktop) {
			return TAP_TO_CONTINUE;
		}
		final String glue = this.menu.rootFrame.getTemplates().getDecoratedString("LOADING_PRESS_A_KEY");
		return (glue == null) || glue.isEmpty() || "LOADING_PRESS_A_KEY".equals(glue) ? PRESS_TO_CONTINUE
				: glue.toUpperCase(Locale.ROOT);
	}

	private void setLoadingRatio(final float ratio) {
		this.loadingBar.setFrameByRatio(ratio);
		if (this.reforgedLoading != null) {
			this.reforgedLoading.setRatio(ratio);
		}
	}

	/** The bar while the map loads: the part the preview filled, then the map loader's own progress. */
	void setLoadingProgress(final float completionRatio) {
		setLoadingRatio(this.loadingBarStart + ((1 - this.loadingBarStart) * completionRatio));
	}

	void clearReforgedLoading() {
		if (this.reforgedLoading != null) {
			this.reforgedLoading.dispose();
			this.reforgedLoading = null;
		}
	}

	boolean isMapDone() {
		return this.loadingMapDone;
	}

	void markMapDone() {
		this.loadingMapDone = true;
	}

	/** Whether a loaded map still waits for the tap that starts it. */
	boolean holdsForTap() {
		return this.waitForTap && !this.continueTapped;
	}

	/** The loaded map has started; the next one waits for its own tap again. */
	void markMapStarted() {
		this.loadingMapDone = false;
		this.continueTapped = false;
	}

	boolean isWaitingForTap() {
		return this.loadingMapDone && this.waitForTap && !this.continueTapped;
	}

	/** @return whether the input was the tap a loaded map waited for */
	boolean continueAfterLoading() {
		if (!isWaitingForTap()) {
			return false;
		}
		this.continueTapped = true;
		this.tapPulse = 0;
		this.loadingBarText.setAlpha(1.0f);
		if (this.loadingBarTextColor != null) {
			this.loadingBarText.setColor(this.loadingBarTextColor);
		}
		this.menu.rootFrame.setDecoratedText(this.loadingBarText, "LOADING_LOADING");
		if (this.reforgedLoading != null) {
			this.reforgedLoading.setBarText(reforgedLoadingString(), 1);
		}
		return true;
	}

	/** The screen comes up for a map whose loading starts. */
	void beginLoading() {
		this.loadingMapDone = false;
		this.continueTapped = false;
		this.tapPulse = 0;
		if (this.loadingBarTextColor == null) {
			this.loadingBarTextColor = new Color(this.loadingBarText.getColor());
		}
		this.loadingFrame.setVisible(true);
		this.loadingBar.setVisible(this.reforgedLoading == null);
	}

	/** Fills the screen for the map being loaded; a previewed screen goes on with its bar. */
	void showMap(final War3Map map, final War3MapW3i mapInfo, final DataTable worldEditData, final WTS wts,
			final int localPlayerIndex) {
		final int campaignBackground = mapInfo.getCampaignBackground();
		final String mapModel = mapLoadingModel(mapInfo);
		if (mapModel != null) {
			if (!this.previewingLoading) {
				fillLoadingScreen(mapModel, 0, mapInfo, wts, map);
				this.loadingBarStart = 0;
			}
			setLoadingRatio(this.loadingBarStart);
		}
		else if (campaignBackground == -1) {
			final String background = meleeLoadingBackground(map,
					loadingSkinKey(this.menu.currentMapConfig.getPlayer(localPlayerIndex)));
			// A previewed screen goes on with its bar; its backdrop only changes for a race the preview could not
			// know, which a Reforged screen does not show.
			final boolean previewedReforged = this.previewingLoading && (this.reforgedLoading != null)
					&& this.reforgedLoading.isMapScreen();
			if (!previewedReforged && (!this.previewingLoading || !background.equals(this.previewedBackground))) {
				fillLoadingScreen(background, 0, mapInfo, wts);
			}
			if (!this.previewingLoading) {
				this.loadingBarStart = 0;
			}
			setLoadingRatio(this.loadingBarStart);
		}
		else if (!this.previewingLoading) {
			fillCampaignLoadingScreen(worldEditData, campaignBackground, mapInfo, wts);
			this.loadingBarStart = 0;
		}
		// A previewed screen has been shown since the menu started building; only its bar goes on.
		this.previewingLoading = false;
		this.previewedBackground = null;
		if (fillMapLoadingPanels(map, mapInfo, wts)) {
			setLoadingPlayers();
		}
		if (mapModel != null) {
			if (this.reforgedLoading == null) {
				fillReforgedModelLoadingScreen(mapInfo, wts,
						raceId(this.menu.currentMapConfig.getPlayer(localPlayerIndex), localRace(mapInfo)));
			}
		}
		else if (campaignBackground == -1) {
			fillReforgedMapLoadingScreen(map, mapInfo, wts, localPlayerIndex);
		}
	}

	/** The map could not be loaded: the screen goes. */
	void abandon() {
		this.loadingMapDone = false;
		this.continueTapped = false;
		this.previewingLoading = false;
		this.previewedBackground = null;
		if (this.loadingFrame != null) {
			this.loadingFrame.setVisible(false);
			this.loadingBar.setVisible(false);
			this.loadingBackground.setVisible(false);
		}
		clearReforgedLoading();
	}

	/** Back in the menu after a game. */
	void hide() {
		this.loadingBar.setVisible(false);
		this.loadingFrame.setVisible(false);
		this.loadingBackground.setVisible(false);
		clearReforgedLoading();
	}

	/** @return false when the menu itself is to be drawn */
	boolean render(final SpriteBatch batch, final BitmapFont font20, final GlyphLayout glyphLayout) {
		if ((this.reforgedLoading != null) && this.loadingFrame.isVisible()) {
			final int width = Gdx.graphics.getWidth();
			final int height = Gdx.graphics.getHeight();
			this.savedProjection.set(batch.getProjectionMatrix());
			batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
			this.reforgedLoading.render(batch, width, height);
			batch.setProjectionMatrix(this.savedProjection);
			return true;
		}
		else if (this.previewingLoading) {
			// The rest of the menu is still being built and shows nothing but the loading screen yet.
			this.loadingFrame.render(batch, font20, glyphLayout);
			return true;
		}
		return false;
	}

	/**
	 * Shows the melee panel (map name, game type, minimap) or the custom panel of a loading screen without a campaign
	 * background.
	 *
	 * @return whether it is the melee panel
	 */
	private boolean fillMapLoadingPanels(final War3Map map, final War3MapW3i mapInfo, final WTS wts) {
		final boolean meleeLoading = (mapInfo.getCampaignBackground() == -1)
				&& mapInfo.hasFlag(War3MapW3iFlags.MELEE_MAP);
		this.loadingCustomPanel.setVisible(!meleeLoading);
		this.loadingMeleePanel.setVisible(meleeLoading);
		if (meleeLoading) {
			final GameUI rootFrame = this.menu.rootFrame;
			rootFrame.setText((StringFrame) rootFrame.getFrameByName("LoadingMeleeMapName", 0),
					getStringWithWTS(wts, mapInfo.getName()));
			rootFrame.setDecoratedText((StringFrame) rootFrame.getFrameByName("LoadingMeleeGameTypeValue", 0),
					"MELEE");
			setLoadingMinimap(map, mapInfo);
			alignMinimapWithBackdrop();
		}
		return meleeLoading;
	}

	/**
	 * NOTE: this is a heavy reload to get the user skin, because MeleeUI loads it again so much later in the map load
	 * pipeline. It's probably possible to optimize that, so that the stuff we load here is passed downstream to
	 * MeleeUI and only loaded once.
	 */
	private static String meleeLoadingBackground(final War3Map map, final String skinKey) {
		final GameSkin userSkin = GameUI.loadSkin(map, skinKey);
		return userSkin.getSkin().getField("LoadingMeleeBackground");
	}

	/** The skin whose melee loading backdrop a player sees: that of the chosen race, Default for random. */
	private static String loadingSkinKey(final CBasePlayer player) {
		for (int j = 0; j < WarsmashConstants.RACE_MANAGER.getEntryCount(); j++) {
			final CRaceManagerEntry entry = WarsmashConstants.RACE_MANAGER.get(j);
			final CRacePreference racePreference = WarsmashConstants.RACE_MANAGER
					.getRacePreferenceById(entry.getRacePrefId());
			if (player.isRacePrefSet(racePreference)) {
				return entry.getKey();
			}
		}
		return "Default";
	}

	/**
	 * The skin of the local player's race before the map's script has set the players up: the race of the first user
	 * slot of a launcher's slots ("slot:USER:race..." as {@link MenuUI#startSkirmish}), else of the map's first human
	 * player.
	 */
	private static String previewSkinKey(final War3MapW3i mapInfo, final String startupSlots) {
		final int raceId = previewRace(mapInfo, startupSlots);
		final CRace race = raceId > 0 ? WarsmashConstants.RACE_MANAGER.getRace(raceId) : null;
		final CRaceManagerEntry entry = race == null ? null : WarsmashConstants.RACE_MANAGER.get(race);
		return entry == null ? "Default" : entry.getKey();
	}

	private void fillCampaignLoadingScreen(final DataTable worldEditData, final int campaignBackground,
			final War3MapW3i mapInfo, final WTS wts) {
		final Element loadingScreens = worldEditData.get("LoadingScreens");
		final String key = String.format("%2s", Integer.toString(campaignBackground)).replace(' ', '0');
		fillLoadingScreen(loadingScreens.getField(key, 3), loadingScreens.getFieldValue(key, 2), mapInfo, wts);
		if (this.reforgedLoadingScreens) {
			this.reforgedLoading = ReforgedLoadingScreen.create(this.menu.dataSource,
					this.menu.rootFrame.getFontGenerator(), campaignBackground, localRace(mapInfo),
					getStringWithWTS(wts, mapInfo.getLoadingScreenTitle()),
					getStringWithWTS(wts, mapInfo.getLoadingScreenSubtitle()),
					getStringWithWTS(wts, mapInfo.getLoadingScreenText()));
			if (this.reforgedLoading != null) {
				this.reforgedLoading.setBarText(reforgedLoadingString(), 1);
				this.loadingBackground.setVisible(false);
				this.loadingBar.setVisible(false);
			}
		}
	}

	/** The race of the map's first human player, as the map information numbers races; 0 if none says. */
	private static int localRace(final War3MapW3i mapInfo) {
		for (final Player player : mapInfo.getPlayers()) {
			if (player.getType() == 1) {
				return player.getRace();
			}
		}
		return 0;
	}

	/** The text of the Reforged bar while loading: the game's own string when the data has one. */
	private String reforgedLoadingString() {
		return decoratedString("LOADING_STRING_0", "Loading...");
	}

	/** A string of the game's string tables, or the given text when the data does not have it. */
	private String decoratedString(final String key, final String missing) {
		final String text = this.menu.rootFrame.getTemplates().getDecoratedString(key);
		return key.equals(text) ? missing : text;
	}

	/**
	 * The Reforged screen of a map without a campaign loading screen: a previewed one goes on with its bar and gets
	 * its players once the game is set up.
	 *
	 * @param localPlayerIndex the local player, or -1 before the game is set up
	 */
	private void fillReforgedMapLoadingScreen(final War3Map map, final War3MapW3i mapInfo, final WTS wts,
			final int localPlayerIndex) {
		if (!this.reforgedLoadingScreens) {
			return;
		}
		if (!mapInfo.hasFlag(War3MapW3iFlags.MELEE_MAP)) {
			// The glue screen shows the preview of a melee map only.
			setLoadingMinimap(map, mapInfo);
		}
		if ((this.reforgedLoading == null) || !this.reforgedLoading.isMapScreen()) {
			clearReforgedLoading();
			this.reforgedLoading = ReforgedLoadingScreen.createForMap(this.menu.dataSource,
					this.menu.rootFrame.getFontGenerator(), getStringWithWTS(wts, mapInfo.getName()),
					getStringWithWTS(wts, mapInfo.getAuthor()),
					decoratedString("LOADING_MAP_CREATED_BY", "Map Created By:"), this.loadingPreviewTexture,
					this.reforgedPortrait);
			if (this.reforgedLoading == null) {
				return;
			}
			this.reforgedLoading.setLoadingTips(loadingTips());
		}
		else {
			this.reforgedLoading.setPreview(this.loadingPreviewTexture);
		}
		this.loadingBackground.setVisible(false);
		this.loadingBar.setVisible(false);
		if (localPlayerIndex >= 0) {
			this.reforgedLoading.setTeams(reforgedLoadingTeams(localPlayerIndex));
		}
	}

	/** The game's loading tips, which the Reforged bar shows by turns for maps outside a campaign. */
	private List<String> loadingTips() {
		final List<String> tips = new ArrayList<>();
		for (int i = 0; i < 18; i++) {
			final String key = "LOADING_STRING_" + i;
			final String tip = this.menu.rootFrame.getTemplates().getDecoratedString(key);
			if (!key.equals(tip)) {
				tips.add(tip);
			}
		}
		if (tips.isEmpty()) {
			tips.add(reforgedLoadingString());
		}
		return tips;
	}

	/** The players of the game by team, as the Reforged loading screen lists them. */
	private List<ReforgedLoadingScreen.Team> reforgedLoadingTeams(final int localPlayerIndex) {
		final GameUI rootFrame = this.menu.rootFrame;
		final Map<Integer, List<ReforgedLoadingScreen.Player>> players = new TreeMap<>();
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CBasePlayer player = this.menu.currentMapConfig.getPlayer(i);
			if ((player.getSlotState() != CPlayerSlotState.PLAYING) || ((player.getController() != CMapControl.USER)
					&& (player.getController() != CMapControl.COMPUTER))) {
				continue;
			}
			final String raceKey = getRaceStringKey(player);
			final String name = player.getController() == CMapControl.COMPUTER
					? rootFrame.getTemplates().getDecoratedString(getComputerSlotKey(player.getAIDifficulty()))
					: player.getName();
			players.computeIfAbsent(player.getTeam(), team -> new ArrayList<>())
					.add(new ReforgedLoadingScreen.Player(name, player.getColor(),
							rootFrame.getTemplates().getDecoratedString(raceKey), reforgedRaceIcon(raceKey),
							i == localPlayerIndex));
		}
		return reforgedTeams(players);
	}

	/**
	 * The players of a map before the game is set up, for a previewed loading screen: Reforged lists them from its
	 * first frame. They come from a launcher's slots, else from the map information as -loadfile plays it.
	 *
	 * @param startupSlots the launcher's slots as {@link MenuUI#startSkirmish} takes them, or null
	 */
	private List<ReforgedLoadingScreen.Team> previewLoadingTeams(final War3MapW3i mapInfo, final WTS wts,
			final String startupSlots) {
		final GameUI rootFrame = this.menu.rootFrame;
		final List<Player> mapPlayers = mapInfo.getPlayers();
		final boolean ownTeams = mapInfo.hasFlag(War3MapW3iFlags.MELEE_MAP)
				&& !mapInfo.hasFlag(War3MapW3iFlags.FIXED_PLAYER_SETTINGS_FOR_CUSTOM_FORCES);
		final Map<Integer, List<ReforgedLoadingScreen.Player>> players = new TreeMap<>();
		boolean localListed = false;
		final List<LauncherSlot> launcherSlots = LauncherSlot.parse(startupSlots);
		final int count = Math.min(WarsmashConstants.MAX_PLAYERS, mapPlayers.size());
		for (int i = 0; i < count; i++) {
			final Player mapPlayer = mapPlayers.get(i);
			LobbyPlayerType type = mapPlayer.getType() == 1 ? LobbyPlayerType.USER
					: mapPlayer.getType() == 2 ? LobbyPlayerType.COMPUTER_NORMAL : null;
			int race = mapPlayer.getRace();
			int team = ownTeams ? i : forceOf(mapInfo, mapPlayer.getId());
			int color = mapPlayer.getId();
			if (startupSlots != null) {
				type = null;
				final LauncherSlot slot = LauncherSlot.of(launcherSlots, i);
				if (slot != null) {
					try {
						type = LobbyPlayerType.valueOf(slot.type);
						race = LauncherSlot.orMap(slot.race, race);
						team = LauncherSlot.orMap(slot.team, team);
						color = LauncherSlot.orMap(slot.color, color);
					}
					catch (final IllegalArgumentException e) {
						type = null;
					}
				}
			}
			final boolean local = (type == LobbyPlayerType.USER) && !localListed;
			final String name;
			if (local) {
				name = this.startupProfile != null ? this.startupProfile
						: this.menu.profileManager.getCurrentProfile();
			}
			else if (type == LobbyPlayerType.USER) {
				name = getStringWithWTS(wts, mapPlayer.getName());
			}
			else if (type == LobbyPlayerType.COMPUTER_NEWBIE) {
				name = rootFrame.getTemplates().getDecoratedString("COMPUTER_NEWBIE");
			}
			else if (type == LobbyPlayerType.COMPUTER_NORMAL) {
				name = rootFrame.getTemplates().getDecoratedString("COMPUTER_NORMAL");
			}
			else if (type == LobbyPlayerType.COMPUTER_INSANE) {
				name = rootFrame.getTemplates().getDecoratedString("COMPUTER_INSANE");
			}
			else {
				continue;
			}
			final String raceKey = raceStringKey(race);
			localListed |= local;
			players.computeIfAbsent(team, key -> new ArrayList<>())
					.add(new ReforgedLoadingScreen.Player(name, color,
							rootFrame.getTemplates().getDecoratedString(raceKey), reforgedRaceIcon(raceKey), local));
		}
		return reforgedTeams(players);
	}

	/** The force of the map information a player starts in; 0 when none names it. */
	private static int forceOf(final War3MapW3i mapInfo, final int playerId) {
		final List<Force> forces = mapInfo.getForces();
		for (int i = 0; i < forces.size(); i++) {
			if ((forces.get(i).getPlayerMasks() & (1L << playerId)) != 0) {
				return i;
			}
		}
		return 0;
	}

	/** The race string of a race as the map information and the launcher number them; random otherwise. */
	private static String raceStringKey(final int race) {
		switch (race) {
		case 1:
			return "HUMAN";
		case 2:
			return "ORC";
		case 3:
			return "UNDEAD";
		case 4:
			return "NIGHT_ELF";
		default:
			return "RANDOM";
		}
	}

	private List<ReforgedLoadingScreen.Team> reforgedTeams(
			final Map<Integer, List<ReforgedLoadingScreen.Player>> players) {
		final String teamName = decoratedString("LOADING_TEAM_NAME", "Team %d");
		final List<ReforgedLoadingScreen.Team> teams = new ArrayList<>();
		for (final Map.Entry<Integer, List<ReforgedLoadingScreen.Player>> team : players.entrySet()) {
			teams.add(new ReforgedLoadingScreen.Team(teamName.replace("%d", Integer.toString(team.getKey() + 1)),
					team.getValue()));
		}
		return teams;
	}

	private static int reforgedRaceIcon(final String raceKey) {
		switch (raceKey) {
		case "HUMAN":
			return 0;
		case "ORC":
			return 1;
		case "UNDEAD":
			return 2;
		case "NIGHT_ELF":
			return 3;
		default:
			return ReforgedLoadingScreen.RACE_RANDOM;
		}
	}

	/**
	 * The map's own loading screen model (its imported file, or a game model it names), which the original shows in
	 * place of a campaign or race screen; null when the map has none.
	 */
	private static String mapLoadingModel(final War3MapW3i mapInfo) {
		final String model = mapInfo.getLoadingScreenModel();
		return (model == null) || model.trim().isEmpty() ? null : model.trim();
	}

	/**
	 * Loads a model with its textures from the given data source (a map over the game's data). The menu keeps its
	 * models by file name, so the map's files are not kept: another map may have other files under the same names.
	 */
	private void setSpriteFrameModelFrom(final DataSource modelSource, final SpriteFrame frame, final String model) {
		final DataSource gameData = this.menu.viewer.dataSource;
		final Set<String> kept = new HashSet<>(this.menu.viewer.fetchCache.keySet());
		this.menu.viewer.setDataSource(modelSource);
		try {
			this.menu.rootFrame.setSpriteFrameModel(frame, model);
		}
		catch (final RuntimeException e) {
			Log.error("Unable to load the loading screen model " + model + ": " + e);
			frame.setModel(null);
		}
		finally {
			this.menu.viewer.setDataSource(gameData);
			this.menu.viewer.fetchCache.keySet().retainAll(kept);
		}
	}

	/**
	 * The Reforged screen of a map with its own loading screen model: the model fills the screen as in the classic
	 * screen, with the crest of the player's race and the map's loading texts at the right, as on a campaign screen.
	 */
	private void fillReforgedModelLoadingScreen(final War3MapW3i mapInfo, final WTS wts, final int race) {
		if (!this.reforgedLoadingScreens) {
			return;
		}
		clearReforgedLoading();
		this.reforgedLoading = ReforgedLoadingScreen.createOverModel(this.menu.dataSource,
				this.menu.rootFrame.getFontGenerator(), race, getStringWithWTS(wts, mapInfo.getLoadingScreenTitle()),
				getStringWithWTS(wts, mapInfo.getLoadingScreenSubtitle()),
				getStringWithWTS(wts, mapInfo.getLoadingScreenText()));
		if (this.reforgedLoading != null) {
			this.reforgedLoading.setBarText(reforgedLoadingString(), 1);
			this.loadingBar.setVisible(false);
		}
	}

	/** The race of the local player of a launcher's slots, else of the map's first human player; 0 for random. */
	private static int previewRace(final War3MapW3i mapInfo, final String startupSlots) {
		final LauncherSlot user = LauncherSlot.firstUser(startupSlots);
		return LauncherSlot.orMap(user == null ? LauncherSlot.MAP_VALUE : user.race, localRace(mapInfo));
	}

	/** A player's chosen race as the map information numbers races; the given one for a random race. */
	private static int raceId(final CBasePlayer player, final int random) {
		switch (getRaceStringKey(player)) {
		case "HUMAN":
			return 1;
		case "ORC":
			return 2;
		case "UNDEAD":
			return 3;
		case "NIGHT_ELF":
			return 4;
		default:
			return random;
		}
	}

	private void fillLoadingScreen(final String screenModel, final int sequence, final War3MapW3i mapInfo,
			final WTS wts) {
		fillLoadingScreen(screenModel, sequence, mapInfo, wts, null);
	}

	/** @param modelSource the map, when the model is one of its own files; null for the game's data */
	private void fillLoadingScreen(final String screenModel, final int sequence, final War3MapW3i mapInfo,
			final WTS wts, final DataSource modelSource) {
		final GameUI rootFrame = this.menu.rootFrame;
		clearReforgedLoading();
		this.menu.menuScreen.setModel(null, null);
		if (modelSource == null) {
			rootFrame.setSpriteFrameModel(this.loadingBackground, screenModel);
		}
		else {
			setSpriteFrameModelFrom(modelSource, this.loadingBackground, screenModel);
		}
		this.loadingBackground.setSequence(sequence);
		this.loadingBackground.setVisible(true);
		// A loading screen picture is shown whole at its 4:3 shape, with black sides on a wider screen, as the
		// original shows it; the texts and the bar of the loading frame stay where its layout puts them over it.
		this.menu.placeBackdrop(this.loadingBackground, 1);
		rootFrame.setSpriteFrameModel(this.loadingBar, rootFrame.getSkinField("LoadingProgressBar"));
		this.loadingBar.setSequence(0);
		this.loadingBar.setFrameByRatio(0.0f);
		this.loadingBar.setZDepth(0.25f);
		rootFrame.setText(this.loadingTitleText, getStringWithWTS(wts, mapInfo.getLoadingScreenTitle()));
		rootFrame.setText(this.loadingSubtitleText, getStringWithWTS(wts, mapInfo.getLoadingScreenSubtitle()));
		this.loadingText.setJustifyV(TextJustify.TOP);
		rootFrame.setText(this.loadingText, getStringWithWTS(wts, mapInfo.getLoadingScreenText()));
		this.loadingCustomPanel.setVisible(true);
		this.loadingMeleePanel.setVisible(false);
	}

	private static String getStringWithWTS(final WTS wts, final String string) {
		return WTS.resolve(wts, string);
	}

	/** The minimap border is painted into the melee loading backdrop; the image goes where its 4:3 frame has it. */
	private void alignMinimapWithBackdrop() {
		final AbstractRenderableFrame frame = this.loadingMinimapImage;
		frame.clearFramePointAssignments();
		frame.setWidth(GameUI.convertX(this.menu.uiViewport, 0.16f));
		frame.setHeight(GameUI.convertY(this.menu.uiViewport, 0.16f));
		// LoadingMeleePanel puts the image's left edge 0.056875 right of the 4:3 left edge, 0.0425 above center.
		frame.addSetPoint(new SetPoint(FramePoint.LEFT, this.menu.rootFrame, FramePoint.CENTER,
				GameUI.convertX(this.menu.uiViewport, 0.056875f - 0.4f),
				GameUI.convertY(this.menu.uiViewport, 0.0425f)));
		this.loadingMeleePanel.positionBounds(this.menu.rootFrame, this.menu.uiViewport);
	}

	private void setLoadingPlayers() {
		final GameUI rootFrame = this.menu.rootFrame;
		for (final UIFrame slot : this.loadingPlayerSlots) {
			slot.setVisible(false);
		}
		this.loadingPlayerSlots.clear();
		final UIFrame container = rootFrame.getFrameByName("LoadingMeleePlayerContainer", 0);
		UIFrame previous = null;
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CBasePlayer player = this.menu.currentMapConfig.getPlayer(i);
			if ((player.getSlotState() != CPlayerSlotState.PLAYING) || ((player.getController() != CMapControl.USER)
					&& (player.getController() != CMapControl.COMPUTER))) {
				continue;
			}
			final UIFrame slot = rootFrame.createFrame("LoadingPlayerSlot", container, 0, i);
			((AbstractUIFrame) container).add(slot);
			if (previous == null) {
				slot.addSetPoint(new SetPoint(FramePoint.TOPLEFT, container, FramePoint.TOPLEFT, 0, 0));
			}
			else {
				slot.addSetPoint(new SetPoint(FramePoint.TOPLEFT, previous, FramePoint.BOTTOMLEFT, 0, 0));
			}
			// Frames created from a template register their children by name, so the latest slot's are found here.
			final StringFrame name = (StringFrame) rootFrame.getFrameByName("LoadingPlayerSlotName", i);
			final StringFrame race = (StringFrame) rootFrame.getFrameByName("LoadingPlayerSlotRace", i);
			rootFrame.setText((StringFrame) rootFrame.getFrameByName("LoadingPlayerSlotLevel", i), "");
			if (player.getController() == CMapControl.COMPUTER) {
				rootFrame.setDecoratedText(name, getComputerSlotKey(player.getAIDifficulty()));
			}
			else {
				rootFrame.setText(name, player.getName());
			}
			rootFrame.setDecoratedText(race, getRaceStringKey(player));
			this.loadingPlayerSlots.add(slot);
			previous = slot;
		}
		container.setHeight(GameUI.convertY(this.menu.uiViewport, 0.0217f) * this.loadingPlayerSlots.size());
		this.loadingMeleePanel.positionBounds(rootFrame, this.menu.uiViewport);
	}

	/** The string of a computer slot's label, e.g. "Computer (Easy)". */
	static String getComputerSlotKey(final AIDifficulty difficulty) {
		if (difficulty == AIDifficulty.NEWBIE) {
			return "COMPUTER_NEWBIE";
		}
		if (difficulty == AIDifficulty.INSANE) {
			return "COMPUTER_INSANE";
		}
		return "COMPUTER_NORMAL";
	}

	private static String getRaceStringKey(final CBasePlayer player) {
		for (int i = 0; i < WarsmashConstants.RACE_MANAGER.getEntryCount(); i++) {
			final CRaceManagerEntry entry = WarsmashConstants.RACE_MANAGER.get(i);
			if (player.isRacePrefSet(WarsmashConstants.RACE_MANAGER.getRacePreferenceById(entry.getRacePrefId()))) {
				switch (entry.getKey()) {
				case "Human":
					return "HUMAN";
				case "Orc":
					return "ORC";
				case "Undead":
					return "UNDEAD";
				case "NightElf":
					return "NIGHT_ELF";
				default:
					return entry.getKey();
				}
			}
		}
		return "RANDOM";
	}

	private void setLoadingMinimap(final War3Map map, final War3MapW3i mapInfo) {
		if (this.loadingMinimapUnmanagedTexture != null) {
			this.loadingMinimapUnmanagedTexture.dispose();
			this.loadingMinimapUnmanagedTexture = null;
		}
		Texture minimapTexture;
		if (mapInfo.hasFlag(War3MapW3iFlags.HIDE_MINIMAP_IN_PREVIEW_SCREENS)) {
			minimapTexture = this.menu.rootFrame.loadTexture("ui\\widgets\\glues\\minimap-unknown.blp");
		}
		else {
			try {
				minimapTexture = ImageUtils.getAnyExtensionTexture(map, "war3mapPreview.blp");
			}
			catch (final Exception exc) {
				minimapTexture = ImageUtils.getAnyExtensionTexture(map, "war3mapMap.blp");
			}
			this.loadingMinimapUnmanagedTexture = minimapTexture;
		}
		this.loadingMinimapTexture.setTexture(minimapTexture);
		this.loadingPreviewTexture = minimapTexture;
	}
}
