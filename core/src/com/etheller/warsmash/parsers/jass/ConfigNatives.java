package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.UnknownEnumHandle;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.jass.triggers.LocationJass;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.HandleIdAllocator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CPlayerAPI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfigStartLoc;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapPlacement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreference;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CStartLocPrio;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CGameSpeed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CGameType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDensity;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import java.util.HashMap;
import java.util.Map;

import static com.etheller.warsmash.parsers.jass.CommonNatives.enumValue;
import static com.etheller.warsmash.parsers.jass.CommonNatives.intArg;
import static com.etheller.warsmash.parsers.jass.CommonNatives.nullable;

/**
 * Natives of the map configuration phase (start locations, players, teams, game settings), shared by the configuration and the game environments.
 */
final class ConfigNatives {
	private static boolean isNeutralSlot(final CPlayerJass player) {
		return player.getId() >= (WarsmashConstants.MAX_PLAYERS - 4);
	}

	private ConfigNatives() {
	}

	static void register(final JassProgram jassProgramVisitor, final War3MapConfig mapConfig,
			final HandleJassType startlocprioType, final HandleJassType gametypeType,
			final HandleJassType placementType, final HandleJassType gamespeedType,
			final HandleJassType gamedifficultyType, final HandleJassType mapdensityType,
			final HandleJassType locationType, final HandleJassType playerType, final HandleJassType playercolorType,
			final HandleJassType mapcontrolType, final HandleJassType playerslotstateType, final CPlayerAPI playerAPI,
			final HandleIdAllocator handleIdAllocator, final GameUI mapStrings) {
		// Colors the engine cannot draw: kept for GetPlayerColor only, the units keep their last color.
		final Map<Integer, Integer> unknownPlayerColors = new HashMap<>();
		jassProgramVisitor.getJassNativeManager().createNative("SetMapName", (arguments, globalScope, triggerScope) -> {
			final String name = mapStrings.getTrigStr(arguments.get(0).visit(StringJassValueVisitor.getInstance()));
			mapConfig.setMapName(name);
			return null;
		});
		jassProgramVisitor.getJassNativeManager().createNative("SetMapDescription",
				(arguments, globalScope, triggerScope) -> {
					final String name = mapStrings
							.getTrigStr(arguments.get(0).visit(StringJassValueVisitor.getInstance()));
					mapConfig.setMapDescription(name);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTeams", (arguments, globalScope, triggerScope) -> {
			final Integer teamCount = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			mapConfig.setTeamCount(teamCount);
			return null;
		});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayers", (arguments, globalScope, triggerScope) -> {
			final Integer playerCount = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			mapConfig.setPlayerCount(playerCount);
			return null;
		});
		jassProgramVisitor.getJassNativeManager().createNative("DefineStartLocation",
				(arguments, globalScope, triggerScope) -> {
					final Integer whichStartLoc = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final Double x = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final Double y = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					mapConfig.getStartLoc(whichStartLoc).setX(x.floatValue());
					mapConfig.getStartLoc(whichStartLoc).setY(y.floatValue());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DefineStartLocationLoc",
				(arguments, globalScope, triggerScope) -> {
					final Integer whichStartLoc = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					mapConfig.getStartLoc(whichStartLoc).setX(whichLocation.x);
					mapConfig.getStartLoc(whichStartLoc).setY(whichLocation.y);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetStartLocPrioCount",
				(arguments, globalScope, triggerScope) -> {
					final War3MapConfigStartLoc startLoc = mapConfig.findStartLoc(intArg(arguments, 0));
					if (startLoc != null) {
						startLoc.setStartLocPrioCount(intArg(arguments, 1));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetStartLocPrio",
				(arguments, globalScope, triggerScope) -> {
					final War3MapConfigStartLoc startLoc = mapConfig.findStartLoc(intArg(arguments, 0));
					if (startLoc != null) {
						startLoc.setStartLocPrio(intArg(arguments, 1), intArg(arguments, 2),
								nullable(arguments, 3, ObjectJassValueVisitor.<CStartLocPrio>getInstance()));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetStartLocPrioSlot",
				(arguments, globalScope, triggerScope) -> {
					final War3MapConfigStartLoc startLoc = mapConfig.findStartLoc(intArg(arguments, 0));
					return IntegerJassValue.of(startLoc == null ? 0 : startLoc.getStartLocPrioSlot(intArg(arguments, 1)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetStartLocPrio",
				(arguments, globalScope, triggerScope) -> {
					final War3MapConfigStartLoc startLoc = mapConfig.findStartLoc(intArg(arguments, 0));
					return new HandleJassValue(startlocprioType,
							startLoc == null ? null : startLoc.getStartLocPrio(intArg(arguments, 1)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetEnemyStartLocPrioCount",
				(arguments, globalScope, triggerScope) -> {
					final War3MapConfigStartLoc startLoc = mapConfig.findStartLoc(intArg(arguments, 0));
					if (startLoc != null) {
						startLoc.setEnemyStartLocPrioCount(intArg(arguments, 1));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetEnemyStartLocPrio",
				(arguments, globalScope, triggerScope) -> {
					final War3MapConfigStartLoc startLoc = mapConfig.findStartLoc(intArg(arguments, 0));
					if (startLoc != null) {
						startLoc.setEnemyStartLocPrio(intArg(arguments, 1), intArg(arguments, 2),
								nullable(arguments, 3, ObjectJassValueVisitor.<CStartLocPrio>getInstance()));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetGameTypeSupported",
				(arguments, globalScope, triggerScope) -> {
					final CGameType gameType = arguments.get(0).visit(ObjectJassValueVisitor.<CGameType>getInstance());
					final Boolean value = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					mapConfig.setGameTypeSupported(gameType, value);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetMapFlag", (arguments, globalScope, triggerScope) -> {
			final CMapFlag mapFlag = arguments.get(0).visit(ObjectJassValueVisitor.<CMapFlag>getInstance());
			final Boolean value = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
			mapConfig.setMapFlag(mapFlag, value);
			return null;
		});
		jassProgramVisitor.getJassNativeManager().createNative("SetGamePlacement",
				(arguments, globalScope, triggerScope) -> {
					final CMapPlacement placement = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CMapPlacement>getInstance());
					mapConfig.setPlacement(placement);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetGameSpeed",
				(arguments, globalScope, triggerScope) -> {
					final CGameSpeed gameSpeed = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CGameSpeed>getInstance());
					mapConfig.setGameSpeed(gameSpeed);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetGameDifficulty",
				(arguments, globalScope, triggerScope) -> {
					final CMapDifficulty gameDifficulty = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CMapDifficulty>getInstance());
					mapConfig.setGameDifficulty(gameDifficulty);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetResourceDensity",
				(arguments, globalScope, triggerScope) -> {
					final CMapDensity resourceDensity = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CMapDensity>getInstance());
					mapConfig.setResourceDensity(resourceDensity);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetCreatureDensity",
				(arguments, globalScope, triggerScope) -> {
					final CMapDensity creatureDensity = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CMapDensity>getInstance());
					mapConfig.setCreatureDensity(creatureDensity);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTeams", (arguments, globalScope, triggerScope) -> {
			return IntegerJassValue.of(mapConfig.getTeamCount());
		});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayers", (arguments, globalScope, triggerScope) -> {
			return IntegerJassValue.of(mapConfig.getPlayerCount());
		});
		jassProgramVisitor.getJassNativeManager().createNative("IsGameTypeSupported",
				(arguments, globalScope, triggerScope) -> {
					final Object gameType = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (gameType instanceof UnknownEnumHandle) {
						// Game types are bits; 1.29 answers true for a mixed value (99) holding a supported bit.
						final int bits = ((UnknownEnumHandle) gameType).getHandleId();
						for (final CGameType type : CGameType.VALUES) {
							if (((bits & type.getId()) != 0) && mapConfig.isGameTypeSupported(type)) {
								return BooleanJassValue.TRUE;
							}
						}
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(mapConfig.isGameTypeSupported((CGameType) gameType));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetGameTypeSelected",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(gametypeType, mapConfig.getGameTypeSelected());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsMapFlagSet",
				(arguments, globalScope, triggerScope) -> {
					final CMapFlag mapFlag = arguments.get(0).visit(ObjectJassValueVisitor.<CMapFlag>getInstance());
					return BooleanJassValue.of(mapConfig.isMapFlagSet(mapFlag));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetGamePlacement",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(placementType, mapConfig.getPlacement());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetGameSpeed",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(gamespeedType, mapConfig.getGameSpeed());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetGameDifficulty",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(gamedifficultyType, mapConfig.getGameDifficulty());
				});
		// Measured: the default difficulty is its own setting, until a script sets it the game's one.
		jassProgramVisitor.getJassNativeManager().createNative("GetDefaultDifficulty",
				(arguments, globalScope, triggerScope) -> {
					final int stored = CCampaignProgress.getDefaultDifficulty();
					return new HandleJassValue(gamedifficultyType,
							((stored >= 0) && (stored < CMapDifficulty.VALUES.length)) ? CMapDifficulty.VALUES[stored]
									: mapConfig.getGameDifficulty());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetDefaultDifficulty",
				(arguments, globalScope, triggerScope) -> {
					final CMapDifficulty difficulty = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (difficulty != null) {
						CCampaignProgress.setDefaultDifficulty(difficulty.ordinal());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetResourceDensity",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(mapdensityType, mapConfig.getResourceDensity());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCreatureDensity",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(mapdensityType, mapConfig.getCreatureDensity());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetStartLocationX",
				(arguments, globalScope, triggerScope) -> {
					final Integer whichStartLoc = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return RealJassValue.of(mapConfig.getStartLoc(whichStartLoc).getX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetStartLocationY",
				(arguments, globalScope, triggerScope) -> {
					final Integer whichStartLoc = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return RealJassValue.of(mapConfig.getStartLoc(whichStartLoc).getY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetStartLocationLoc",
				(arguments, globalScope, triggerScope) -> {
					final Integer whichStartLoc = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return new HandleJassValue(locationType,
							new LocationJass(mapConfig.getStartLoc(whichStartLoc).getX(),
									mapConfig.getStartLoc(whichStartLoc).getY(), handleIdAllocator.createHandleId()));
				});
		// PlayerAPI: with a null player the setters do nothing and the getters read what 1.29 reads.

		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerTeam",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final Integer whichTeam = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (player != null) {
						player.setTeam(whichTeam);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerStartLocation",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final Integer startLocIndex = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (player != null) {
						player.setStartLocationIndex(startLocIndex);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForcePlayerStartLocation",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final Integer startLocIndex = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (player != null) {
						player.forceStartLocation(startLocIndex);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerColor",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final Object playerColor = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (player != null) {
						if (playerColor instanceof CPlayerColor) {
							unknownPlayerColors.remove(player.getId());
							playerAPI.setColor(player, (CPlayerColor) playerColor);
						}
						else if (playerColor instanceof UnknownEnumHandle) {
							unknownPlayerColors.put(player.getId(), ((UnknownEnumHandle) playerColor).getHandleId());
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerAlliance",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerJass otherPlayer = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CAllianceType whichAllianceSetting = arguments.get(2)
							.visit(ObjectJassValueVisitor.<CAllianceType>getInstance());
					final Boolean value = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());
					// Measured (1.29): a null player on either side changes nothing.
					if ((player != null) && (otherPlayer != null) && (whichAllianceSetting != null)) {
						player.setAlliance(otherPlayer.getId(), whichAllianceSetting, value);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerAlliance",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerJass otherPlayer = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CAllianceType whichAllianceSetting = arguments.get(2)
							.visit(ObjectJassValueVisitor.<CAllianceType>getInstance());
					// 1.29 reads false for a null player; a null other player crashes the game there.
					if ((player == null) || (otherPlayer == null) || (whichAllianceSetting == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(player.hasAlliance(otherPlayer.getId(), whichAllianceSetting));
				});
		// Measured: a player is another's ally exactly when it is passive toward it, and its enemy otherwise; with a null
		// player on either side 1.29 reads both as false.
		jassProgramVisitor.getJassNativeManager().createNative("IsPlayerAlly",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerJass otherPlayer = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					if ((player == null) || (otherPlayer == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(player.hasAlliance(otherPlayer.getId(), CAllianceType.PASSIVE));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsPlayerEnemy",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerJass otherPlayer = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					if ((player == null) || (otherPlayer == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(!player.hasAlliance(otherPlayer.getId(), CAllianceType.PASSIVE));
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerTaxRate",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerJass otherPlayer = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerState whichResource = arguments.get(2)
							.visit(ObjectJassValueVisitor.<CPlayerState>getInstance());
					final int taxRate = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					if ((player != null) && (otherPlayer != null) && (whichResource != null)) {
						player.setTaxRate(otherPlayer.getId(), whichResource, taxRate);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerRacePreference",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CRacePreference whichRacePreference = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CRacePreference>getInstance());
					if (player != null) {
						player.setRacePref(whichRacePreference);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerRaceSkin",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = nullable(arguments, 0, ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final Object skin = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if (player != null) {
						player.setRaceSkin(skin instanceof CRacePreference ? (CRacePreference) skin : null);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerRaceSelectable",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final Boolean value = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (player != null) {
						player.setRaceSelectable(value);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerController",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CMapControl controlType = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CMapControl>getInstance());
					if (player != null) {
						player.setController(controlType);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerName",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final String name = mapStrings
							.getTrigStr(arguments.get(1).visit(StringJassValueVisitor.getInstance()));
					if (player != null) {
						player.setName(name);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerOnScoreScreen",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final Boolean value = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (player != null) {
						player.setOnScoreScreen(value);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerTeam",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					if (player == null) {
						return IntegerJassValue.ZERO;
					}
					// An empty slot is in no team (the neutral slots always read playing, so keep their own team).
					if (!isNeutralSlot(player) && (player.getSlotState() == CPlayerSlotState.EMPTY)) {
						return IntegerJassValue.of(-1);
					}
					return IntegerJassValue.of(player.getTeam());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerStartLocation",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					return IntegerJassValue.of(player == null ? 0 : player.getStartLocationIndex());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerColor",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					if ((player != null) && unknownPlayerColors.containsKey(player.getId())) {
						return playercolorType.getUnknownEnumValue(unknownPlayerColors.get(player.getId()));
					}
					// 1.29 reads color 0 (red) for a null player.
					return new HandleJassValue(playercolorType,
							CPlayerColor.getColorByIndex(player == null ? 0 : player.getColor()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerSelectable",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					return BooleanJassValue.of((player != null) && player.isRaceSelectable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerController",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					// 1.29 reads MAP_CONTROL_NONE for a null player.
					if (player == null) {
						return new HandleJassValue(mapcontrolType, CMapControl.NONE);
					}
					// Measured: the four neutral slots read as playing creep slots.
					if (isNeutralSlot(player)) {
						return new HandleJassValue(mapcontrolType, CMapControl.CREEP);
					}
					return new HandleJassValue(mapcontrolType, player.getController());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerSlotState",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					return new HandleJassValue(playerslotstateType, player == null ? CPlayerSlotState.EMPTY
							: isNeutralSlot(player) ? CPlayerSlotState.PLAYING : player.getSlotState());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerTaxRate",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerJass otherPlayer = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CPlayerState whichResource = arguments.get(2)
							.visit(ObjectJassValueVisitor.<CPlayerState>getInstance());
					if ((player == null) || (otherPlayer == null) || (whichResource == null)) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(player.getTaxRate(otherPlayer.getId(), whichResource));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsPlayerRacePrefSet",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final CRacePreference racePref = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CRacePreference>getInstance());
					return BooleanJassValue.of((player != null) && player.isRacePrefSet(racePref));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerName",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass player = arguments.get(0)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					if (player == null) {
						return JassType.STRING.getNullValue();
					}
					return new StringJassValue(player.getName());
				});

		jassProgramVisitor.getJassNativeManager().createNative("Player", (arguments, globalScope, triggerScope) -> {
			final int playerIndex = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
			if ((playerIndex < 0) || (playerIndex >= WarsmashConstants.MAX_PLAYERS)) {
				return playerType.getNullValue();
			}
			final CBasePlayer player = playerAPI.getPlayer(playerIndex);
			// As in 1.29, a player's handle is made when the script first asks for it.
			if (player.getHandleId() == 0) {
				player.setHandleId(handleIdAllocator.createHandleId());
			}
			return new HandleJassValue(playerType, player);
		});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerId",
				(arguments, globalScope, triggerScope) -> {
					final CPlayerJass whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return (whichPlayer == null) ? IntegerJassValue.of(0) : IntegerJassValue.of(whichPlayer.getId());
				});

		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerNeutralAggressive",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(WarsmashConstants.MAX_PLAYERS - 4);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetBJPlayerNeutralVictim",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(WarsmashConstants.MAX_PLAYERS - 3);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetBJPlayerNeutralExtra",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(WarsmashConstants.MAX_PLAYERS - 2);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerNeutralPassive",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(WarsmashConstants.MAX_PLAYERS - 1);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetBJMaxPlayers",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(WarsmashConstants.MAX_PLAYERS - 4);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetBJMaxPlayerSlots",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(WarsmashConstants.MAX_PLAYERS);
				});
	}
}
