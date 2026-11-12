package com.etheller.warsmash.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManager;

/**
 * The data-source dependent constants one game runs with, read once when the game is created. The menu and the
 * launcher keep changing {@link WarsmashConstants} (version switch, another data source loaded) while a game runs,
 * so everything inside a game reads this copy instead.
 */
public final class GameDataConstants {
	private final int gameVersion;
	private final CRaceManager raceManager;
	private final int maxPlayers;
	private final List<String> abilityCompatibility;
	private final boolean fixFlatFilesTilesetLoading;
	private final boolean enableMusic;
	private final boolean loadUnitsFromWorldEditData;
	private final int inputHotkeyMode;
	private final boolean useNineItemInventory;
	private final boolean catchCursor;

	public GameDataConstants(final int gameVersion, final CRaceManager raceManager, final int maxPlayers,
			final List<String> abilityCompatibility, final boolean fixFlatFilesTilesetLoading,
			final boolean enableMusic, final boolean loadUnitsFromWorldEditData, final int inputHotkeyMode,
			final boolean useNineItemInventory, final boolean catchCursor) {
		this.gameVersion = gameVersion;
		this.raceManager = raceManager;
		this.maxPlayers = maxPlayers;
		this.abilityCompatibility = (abilityCompatibility == null) ? Collections.<String>emptyList()
				: Collections.unmodifiableList(new ArrayList<>(abilityCompatibility));
		this.fixFlatFilesTilesetLoading = fixFlatFilesTilesetLoading;
		this.enableMusic = enableMusic;
		this.loadUnitsFromWorldEditData = loadUnitsFromWorldEditData;
		this.inputHotkeyMode = inputHotkeyMode;
		this.useNineItemInventory = useNineItemInventory;
		this.catchCursor = catchCursor;
	}

	public static GameDataConstants current() {
		return new GameDataConstants(WarsmashConstants.GAME_VERSION, WarsmashConstants.RACE_MANAGER,
				WarsmashConstants.MAX_PLAYERS, WarsmashConstants.ABILITY_COMPATIBILITY,
				WarsmashConstants.FIX_FLAT_FILES_TILESET_LOADING, WarsmashConstants.ENABLE_MUSIC,
				WarsmashConstants.LOAD_UNITS_FROM_WORLDEDIT_DATA, WarsmashConstants.INPUT_HOTKEY_MODE,
				WarsmashConstants.USE_NINE_ITEM_INVENTORY, WarsmashConstants.CATCH_CURSOR);
	}

	public int getGameVersion() {
		return this.gameVersion;
	}

	public CRaceManager getRaceManager() {
		return this.raceManager;
	}

	public int getMaxPlayers() {
		return this.maxPlayers;
	}

	/** Never null; empty when the data source names no compatibility list. */
	public List<String> getAbilityCompatibility() {
		return this.abilityCompatibility;
	}

	public boolean isFixFlatFilesTilesetLoading() {
		return this.fixFlatFilesTilesetLoading;
	}

	public boolean isEnableMusic() {
		return this.enableMusic;
	}

	public boolean isLoadUnitsFromWorldEditData() {
		return this.loadUnitsFromWorldEditData;
	}

	public int getInputHotkeyMode() {
		return this.inputHotkeyMode;
	}

	public boolean isUseNineItemInventory() {
		return this.useNineItemInventory;
	}

	public boolean isCatchCursor() {
		return this.catchCursor;
	}

	public int mapGameVersion(final String mapFileName, final long gameDataVersion) {
		return WarsmashConstants.mapGameVersion(mapFileName, gameDataVersion, this.gameVersion);
	}
}
