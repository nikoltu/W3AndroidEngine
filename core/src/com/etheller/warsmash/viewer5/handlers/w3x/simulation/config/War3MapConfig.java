package com.etheller.warsmash.viewer5.handlers.w3x.simulation.config;

import java.util.EnumMap;
import java.util.function.UnaryOperator;

import com.etheller.warsmash.parsers.w3x.w3i.Player;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapPlacement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CGameSpeed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CGameType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDensity;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;

public class War3MapConfig implements CPlayerAPI {
	private String mapName;
	private String mapDescription;
	private int teamCount;
	private int playerCount;
	private final War3MapConfigStartLoc[] startLocations;
	private final CBasePlayer[] players;
	private final EnumMap<CGameType, Boolean> gameTypeToSupported = new EnumMap<>(CGameType.class);
	private final EnumMap<CMapFlag, Boolean> mapFlagToEnabled = new EnumMap<>(CMapFlag.class);
	private CMapPlacement placement;
	private CGameSpeed gameSpeed;
	private CMapDifficulty gameDifficulty = CMapDifficulty.NORMAL;
	private CMapDensity resourceDensity;
	private CMapDensity creatureDensity;
	private CGameType gameTypeSelected;
	private boolean lobbyTeams;

	public War3MapConfig(final int maxPlayers) {
		// TODO should this be WarsmashConstants.MAX_PLAYERS instead of local
		// constructor arg?
		this.startLocations = new War3MapConfigStartLoc[maxPlayers];
		this.players = new CBasePlayer[maxPlayers];
		for (int i = 0; i < maxPlayers; i++) {
			this.startLocations[i] = new War3MapConfigStartLoc();
			this.players[i] = new War3MapConfigPlayer(i, maxPlayers);
			if (i >= (maxPlayers - 4)) {
				this.players[i].setController(CMapControl.NEUTRAL);
			}
		}
	}

	public int getMaxPlayers() {
		return this.players.length;
	}

	public void setMapName(final String mapName) {
		this.mapName = mapName;
	}

	public void setMapDescription(final String mapDescription) {
		this.mapDescription = mapDescription;
	}

	public String getMapName() {
		return this.mapName;
	}

	public String getMapDescription() {
		return this.mapDescription;
	}

	public void setTeamCount(final int teamCount) {
		this.teamCount = teamCount;
	}

	public void setPlayerCount(final int playerCount) {
		this.playerCount = playerCount;
	}

	public War3MapConfigStartLoc getStartLoc(final int whichStartLoc) {
		return this.startLocations[whichStartLoc];
	}

	/** The start location, or null for an index outside the player slots (scripts may pass any integer). */
	public War3MapConfigStartLoc findStartLoc(final int whichStartLoc) {
		return (whichStartLoc >= 0) && (whichStartLoc < this.startLocations.length)
				? this.startLocations[whichStartLoc]
				: null;
	}

	public void setGameTypeSupported(final CGameType gameType, final boolean supported) {
		this.gameTypeToSupported.put(gameType, supported);
	}

	/**
	 * Before the map's config function, which may still change the supported types. Measured on 1.29: a game started
	 * with -loadfile is a melee game, on a melee and on a campaign map alike, and only that type is supported.
	 */
	public void selectGameType(final CGameType gameType) {
		this.gameTypeSelected = gameType;
		this.gameTypeToSupported.clear();
		this.gameTypeToSupported.put(gameType, true);
	}

	/**
	 * Before the map's config function. Measured on 1.29 (-loadfile, melee and campaign maps): custom forces lock
	 * alliance changes, and a single player game always locks the random seed; the other flags start cleared. The
	 * speed reads Normal.
	 */
	/**
	 * The map information lists only the players the map uses, each with its own player number: a campaign map
	 * without Player 1 starts its list with Player 2.
	 */
	public void applyPlayerNames(final War3MapW3i mapInfo, final UnaryOperator<String> trigStr) {
		for (final Player mapPlayer : mapInfo.getPlayers()) {
			final int id = mapPlayer.getId();
			if ((id >= 0) && (id < this.players.length)) {
				this.players[id].setName(trigStr.apply(mapPlayer.getName()));
			}
		}
	}

	public void applyMapInformation(final War3MapW3i mapInfo) {
		this.mapFlagToEnabled.clear();
		setMapFlag(CMapFlag.MAP_LOCK_ALLIANCE_CHANGES, mapInfo.hasFlag(War3MapW3iFlags.USE_CUSTOM_FORCES));
		setMapFlag(CMapFlag.MAP_LOCK_RANDOM_SEED, true);
		this.gameSpeed = CGameSpeed.NORMAL;
	}

	public void setMapFlag(final CMapFlag mapFlag, final boolean set) {
		this.mapFlagToEnabled.put(mapFlag, set);
	}

	public void setPlacement(final CMapPlacement placement) {
		this.placement = placement;
	}

	public void setGameSpeed(final CGameSpeed gameSpeed) {
		this.gameSpeed = gameSpeed;
	}

	public void setGameDifficulty(final CMapDifficulty gameDifficulty) {
		this.gameDifficulty = gameDifficulty;
	}

	public void setResourceDensity(final CMapDensity resourceDensity) {
		this.resourceDensity = resourceDensity;
	}

	public void setCreatureDensity(final CMapDensity creatureDensity) {
		this.creatureDensity = creatureDensity;
	}

	public int getTeamCount() {
		return this.teamCount;
	}

	public int getPlayerCount() {
		return this.playerCount;
	}

	public boolean isGameTypeSupported(final CGameType gameType) {
		final Boolean supported = this.gameTypeToSupported.get(gameType);
		return (supported != null) && supported;
	}

	public CGameType getGameTypeSelected() {
		return this.gameTypeSelected;
	}

	public boolean isMapFlagSet(final CMapFlag mapFlag) {
		final Boolean flag = this.mapFlagToEnabled.get(mapFlag);
		return (flag != null) && flag;
	}

	public CMapPlacement getPlacement() {
		return this.placement;
	}

	public CGameSpeed getGameSpeed() {
		return this.gameSpeed;
	}

	public CMapDifficulty getGameDifficulty() {
		return this.gameDifficulty;
	}

	public CMapDensity getResourceDensity() {
		return this.resourceDensity;
	}

	public CMapDensity getCreatureDensity() {
		return this.creatureDensity;
	}

	public float getStartLocationX(final int startLocIndex) {
		return this.startLocations[startLocIndex].getX();
	}

	public float getStartLocationY(final int startLocIndex) {
		return this.startLocations[startLocIndex].getY();
	}

	@Override
	public CBasePlayer getPlayer(final int index) {
		return this.players[index];
	}

	@Override
	public void setColor(CPlayerJass player, CPlayerColor color) {
		player.setColor(color.ordinal());
	}
	/**
	 * True when the players' teams come from game setup (a melee map without fixed player settings), so team mates
	 * start allied.
	 */
	public boolean isLobbyTeams() {
		return this.lobbyTeams;
	}

	public void setLobbyTeams(final boolean lobbyTeams) {
		this.lobbyTeams = lobbyTeams;
	}
}
