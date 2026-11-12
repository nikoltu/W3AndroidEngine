package com.etheller.warsmash.viewer5.handlers.w3x.simulation.config;

import java.util.EnumMap;
import java.util.EnumSet;

import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreference;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreferences;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

public abstract class CBasePlayer implements CPlayerJass, CHandle {
	private final int id;
	private int handleId;
	private String name;
	private int team;
	private int startLocationIndex;
	private int forcedStartLocationIndex = -1;
	private int color;
	private final CRacePreferences racePrefs;
	private final EnumSet<CAllianceType>[] alliances;
	private final EnumMap<CPlayerState, Integer>[] taxRates;
	private final EnumMap<CPlayerState, Integer> configPlayerStates;
	private boolean onScoreScreen;
	private boolean raceSelectable;
	private CRacePreference raceSkin;
	private CMapControl mapControl = CMapControl.NONE;
	private CPlayerSlotState slotState = CPlayerSlotState.EMPTY;
	private AIDifficulty aiDifficulty = null;
	private float handicap = 1.0f;

	public CBasePlayer(final CBasePlayer other) {
		this.id = other.id;
		this.name = other.name;
		this.team = other.team;
		this.startLocationIndex = other.startLocationIndex;
		this.forcedStartLocationIndex = other.forcedStartLocationIndex;
		this.color = other.color;
		this.racePrefs = other.racePrefs;
		this.alliances = other.alliances;
		this.taxRates = other.taxRates;
		this.configPlayerStates = other.configPlayerStates;
		this.onScoreScreen = other.onScoreScreen;
		this.raceSelectable = other.raceSelectable;
		this.raceSkin = other.raceSkin;
		this.mapControl = other.mapControl;
		this.slotState = other.slotState;
		this.handicap = other.handicap;
	}

	@Override
	public int getHandleId() {
		return this.handleId;
	}

	public void setHandleId(final int handleId) {
		this.handleId = handleId;
	}

	public CBasePlayer(final int id, final int maxPlayers) {
		this.id = id;
		this.color = id;
		// A slot the map information does not list reads as "Player N" in the original.
		this.name = "Player " + (id + 1);
		this.alliances = new EnumSet[maxPlayers];
		this.taxRates = new EnumMap[maxPlayers];
		this.racePrefs = new CRacePreferences();
		this.configPlayerStates = new EnumMap<>(CPlayerState.class);
		for (int i = 0; i < this.alliances.length; i++) {
			if (i == id) {
				// player is fully allied with self
				this.alliances[i] = EnumSet.allOf(CAllianceType.class);
			}
			else {
				this.alliances[i] = EnumSet.noneOf(CAllianceType.class);
			}
			this.taxRates[i] = new EnumMap<>(CPlayerState.class);
		}
	}

	@Override
	public int getId() {
		return this.id;
	}

	@Override
	public void setName(final String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public void setOnScoreScreen(final boolean onScoreScreen) {
		this.onScoreScreen = onScoreScreen;
	}

	public boolean isOnScoreScreen() {
		return this.onScoreScreen;
	}

	@Override
	public void setRaceSelectable(final boolean raceSelectable) {
		this.raceSelectable = raceSelectable;
	}

	/**
	 * The race whose interface skin this player sees; null (the user selectable preference) means the skin of the
	 * player's own race.
	 */
	@Override
	public void setRaceSkin(final CRacePreference raceSkin) {
		this.raceSkin = raceSkin;
	}

	public CRacePreference getRaceSkin() {
		return this.raceSkin;
	}

	@Override
	public void setTeam(final int team) {
		this.team = team;
	}

	@Override
	public int getTeam() {
		return this.team;
	}

	@Override
	public int getStartLocationIndex() {
		return this.startLocationIndex;
	}

	@Override
	public void setStartLocationIndex(final int startLocationIndex) {
		this.startLocationIndex = startLocationIndex;
	}

	@Override
	public void setColor(final int color) {
		this.color = color;
	}

	@Override
	public int getColor() {
		return this.color;
	}

	public CRacePreferences getRacePreferences() {
		return this.racePrefs;
	}

	@Override
	public boolean isRacePrefSet(final CRacePreference racePref) {
		return this.racePrefs.contains(racePref);
	}

	@Override
	public void setRacePref(final CRacePreference racePref) {
		// TODO should this clear? there's a case to be made for keeping old value
		// perhaps
		this.racePrefs.clear();
		this.racePrefs.add(racePref);
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final CAllianceType allianceType, final boolean value) {
		final EnumSet<CAllianceType> alliancesWithOtherPlayer = this.alliances[otherPlayerIndex];
		if (value) {
			alliancesWithOtherPlayer.add(allianceType);
		}
		else {
			alliancesWithOtherPlayer.remove(allianceType);
		}
	}

	@Override
	public boolean hasAlliance(final int otherPlayerIndex, final CAllianceType allianceType) {
		final EnumSet<CAllianceType> alliancesWithOtherPlayer = this.alliances[otherPlayerIndex];
		return alliancesWithOtherPlayer.contains(allianceType);
	}

	@Override
	public void forceStartLocation(final int startLocIndex) {
		this.forcedStartLocationIndex = startLocIndex;
	}

	@Override
	public void setTaxRate(final int otherPlayerIndex, final CPlayerState whichResource, final int rate) {
		this.taxRates[otherPlayerIndex].put(whichResource, rate);
	}

	@Override
	public void setController(final CMapControl mapControl) {
		this.mapControl = mapControl;

	}

	@Override
	public boolean isRaceSelectable() {
		return this.raceSelectable;
	}

	@Override
	public CMapControl getController() {
		return this.mapControl;
	}

	@Override
	public CPlayerSlotState getSlotState() {
		return this.slotState;
	}

	@Override
	public void setSlotState(final CPlayerSlotState slotState) {
		this.slotState = slotState;
	}

	@Override
	public AIDifficulty getAIDifficulty() {
		return this.aiDifficulty;
	}

	@Override
	public void setAIDifficulty(final AIDifficulty aiDifficulty) {
		this.aiDifficulty = aiDifficulty;
	}

	@Override
	public int getTaxRate(final int otherPlayerIndex, final CPlayerState whichResource) {
		final Integer taxRate = this.taxRates[otherPlayerIndex].get(whichResource);
		if (taxRate == null) {
			return 0;
		}
		return taxRate;
	}

	/**
	 * Player states a map's config function sets; 1.29 carries all of them into the game.
	 */
	public void setConfigPlayerState(final CPlayerState whichPlayerState, final int value) {
		this.configPlayerStates.put(whichPlayerState, value);
	}

	public EnumMap<CPlayerState, Integer> getConfigPlayerStates() {
		return this.configPlayerStates;
	}

	public void setHandicap(final float handicap) {
		this.handicap = handicap;
	}

	public float getHandicap() {
		return this.handicap;
	}
}
