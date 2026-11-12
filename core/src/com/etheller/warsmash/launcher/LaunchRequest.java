package com.etheller.warsmash.launcher;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MenuUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/**
 * A game the launcher starts in the engine without its menu: a campaign mission, a custom game, a saved game or a
 * replay, with the profile and the look of the loading screens. Android carries it in the extras of the engine's
 * activity, the desktop hands it to the engine in the same window.
 */
public final class LaunchRequest {
	public static final String CAMPAIGN_MAPS_REFORGED = "reforged";
	public static final String CAMPAIGN_MAPS_CLASSIC = "classic";
	public static final String SKIN_REFORGED = "reforged";

	public static final String SOURCE_CAMPAIGN = "campaign";
	public static final String SOURCE_CUSTOM = "custom";
	public static final String SOURCE_LOAD = "load";
	public static final String SOURCE_REPLAY = "replay";

	/** Campaign number as scripts count campaigns (the named entries of the campaign list); -1 for none. */
	public int campaign = -1;
	public int mission = -1;
	/** easy, normal or hard. */
	public String difficulty;
	/**
	 * The campaign version played, its maps and its graphics: {@link #CAMPAIGN_MAPS_REFORGED} or classic (the
	 * default).
	 */
	public String campaignMaps;
	/** A map of the custom game list, played with the slots and options. */
	public String customMap;
	public String slots;
	public String options;
	/** A saved game of the current profile, by its name without the extension. */
	public String saveFile;
	/** A replay of the Replay folder, by its full path. */
	public String replayFile;
	/** The replay is played although this engine would refuse it. */
	public boolean replayForced;
	/** The map the game starts on, whose loading screen the engine shows from its first frame on. */
	public String mapFile;
	/** The launcher's skin: {@link #SKIN_REFORGED} shows the Reforged loading screens of maps. */
	public String skin;
	/** The profile's Reforged portrait, from 1, which the Reforged loading screens show for the local player. */
	public int portrait;
	/** The player profile the game is played with, by its name. */
	public String profile;

	/** A campaign mission, on the profile's version of the campaign maps. */
	public static LaunchRequest mission(final CampaignCatalog catalog, final CampaignCatalog.Entry entry,
			final int mission, final String campaignMaps, final String difficulty) {
		final LaunchRequest request = new LaunchRequest();
		request.campaign = entry.getNumber();
		request.mission = mission;
		request.campaignMaps = campaignMaps;
		request.mapFile = catalog.missionMapFilename(entry, mission, CAMPAIGN_MAPS_REFORGED.equals(campaignMaps));
		request.difficulty = difficulty;
		return request;
	}

	/**
	 * A saved game of the current profile. A campaign mission's save goes on as that campaign's mission, ending on the
	 * campaign's result as a mission started from its list does; a save that did not keep its campaign is known by its
	 * map when only one campaign lists it.
	 *
	 * @param catalog the campaigns, or null when not known yet (the save is then loaded as any other)
	 */
	public static LaunchRequest load(final GameCatalog.SaveEntry entry, final CampaignCatalog catalog) {
		final LaunchRequest request = new LaunchRequest();
		request.saveFile = entry.name;
		request.mapFile = entry.mapFile;
		if (catalog == null) {
			return request;
		}
		CampaignCatalog.Entry campaign = catalog.getEntry(entry.campaign);
		if ((campaign == null) && !entry.campaignKept) {
			campaign = CampaignResult.campaignOfMap(catalog, entry.mapFile);
		}
		if (campaign != null) {
			request.campaign = campaign.getNumber();
			request.mission = CampaignResult.playedMission(catalog, campaign, entry.mapFile);
			request.campaignMaps = entry.reforgedCampaignMaps ? CAMPAIGN_MAPS_REFORGED : CAMPAIGN_MAPS_CLASSIC;
			request.difficulty = entry.difficulty;
		}
		return request;
	}

	/** A replay this engine plays. */
	public static LaunchRequest replay(final GameCatalog.ReplayEntry entry) {
		return replay(entry, false);
	}

	/** @param forced play it although this engine would refuse it (the player chose to try anyway) */
	public static LaunchRequest replay(final GameCatalog.ReplayEntry entry, final boolean forced) {
		final LaunchRequest request = new LaunchRequest();
		request.replayFile = entry.file.getAbsolutePath();
		request.replayForced = forced;
		request.mapFile = entry.mapFile;
		return request;
	}

	/** Where the game was started from, as the result tells the launcher. */
	public String source() {
		return this.campaign >= 0 ? SOURCE_CAMPAIGN
				: this.customMap != null ? SOURCE_CUSTOM : this.replayFile != null ? SOURCE_REPLAY : SOURCE_LOAD;
	}

	/**
	 * Where the game ended up, as the result tells the launcher: the campaign follows the game actually played, so a
	 * save loaded in the game from another campaign (or from none) is that campaign's mission (or a loaded game), not
	 * the one the game was started as.
	 */
	public String source(final GameResult played) {
		if ((played == null) || (played.campaign == GameResult.CAMPAIGN_UNKNOWN) || (this.replayFile != null)) {
			return source();
		}
		if (played.campaign >= 0) {
			return SOURCE_CAMPAIGN;
		}
		return (this.campaign >= 0) || (this.saveFile != null) ? SOURCE_LOAD : source();
	}

	/** Sets up the engine's menu screen to show only this game's loading screen; before the menu is built. */
	public void prepare(final WarsmashGdxMenuScreen menuScreen) {
		menuScreen.setLauncherMode(true);
		menuScreen.setWaitForTap(true);
		menuScreen.setReforgedLoadingScreens(SKIN_REFORGED.equals(this.skin));
		// A won mission's result comes before the next mission, as on the Reforged campaign screen; a game loaded in
		// the game from a campaign's mission ends the same way, whatever this game was started as.
		menuScreen.setEndsAtCampaignVictory(true);
		menuScreen.setReforgedPortrait(this.portrait);
		menuScreen.setStartupMap(this.mapFile);
		menuScreen.setStartupSlots(this.slots);
		menuScreen.setStartupProfile(this.profile);
	}

	/** Starts the game on the built menu; false when the engine cannot start it. */
	public boolean start(final MenuUI menu) {
		try {
			menu.useProfile(this.profile);
			// A campaign mission's save names its campaign too; the save is what is played.
			if (this.saveFile != null) {
				return menu.startSavedGame(this.saveFile);
			}
			if (this.campaign >= 0) {
				return menu.startCampaignMission(this.campaign, this.mission, difficulty(this.difficulty),
						CAMPAIGN_MAPS_REFORGED.equals(this.campaignMaps));
			}
			if (this.customMap != null) {
				return menu.startSkirmish(this.customMap, this.slots, this.options);
			}
			if (this.replayFile != null) {
				return menu.startReplay(Gdx.files.absolute(this.replayFile), 0, this.replayForced);
			}
		}
		catch (final RuntimeException e) {
			LauncherLog.e("Unable to start the game", e);
		}
		return false;
	}

	private static CMapDifficulty difficulty(final String name) {
		if ("easy".equalsIgnoreCase(name)) {
			return CMapDifficulty.EASY;
		}
		if ("hard".equalsIgnoreCase(name)) {
			return CMapDifficulty.HARD;
		}
		return CMapDifficulty.NORMAL;
	}

	@Override
	public String toString() {
		return "campaign=" + this.campaign + " mission=" + this.mission + " custom=" + this.customMap + " save="
				+ this.saveFile + " replay=" + this.replayFile + (this.replayForced ? " (forced)" : "") + " map=" + this.mapFile;
	}
}
