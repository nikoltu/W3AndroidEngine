package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.debug.JassException;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.parsers.jass.triggers.CForce;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedFormerly;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerGameResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CCrippledPlayerFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Force and player natives: player state, score, tech, handicap, races and unit counts.
 */
final class PlayerNatives extends CommonNatives {
	private final Map<String, Set<War3ID>> typeAndUpgradesByLegacyName = new HashMap<>();
	private final Map<Long, CCrippledPlayerFogModifier> crippleModifiers = new HashMap<>();

	PlayerNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerForces();
		registerPlayers();
	}

	private void registerForces() {
		final HandleJassType playerType = type("player");
		final HandleJassType forceType = type("force");

		// ============================================================================
		// Force API
		//
		// Measured on 1.29: neither the units' owners nor the players' food change.
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerUnitsOwner",
				(arguments, globalScope, triggerScope) -> null);
		jassProgramVisitor.getJassNativeManager().createNative("CreateForce",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(forceType, new CForce());
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyForce",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					release(force);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceAddPlayer",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final CPlayerJass player = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					if (force == null) {
						throw new JassException(globalScope, "force is null", new NullPointerException());
					}
					if (player != null) {
						force.add(player);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceRemovePlayer",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final CPlayerJass player = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					if (force != null) {
						force.remove(player);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceClear",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					force.clear();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceEnumPlayers",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 1,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
						final CPlayerJass jassPlayer = this.simulation.getPlayer(i);
						// 1.29 skips empty slots (also the neutral players) but keeps players who left.
						if (jassPlayer.getSlotState() == CPlayerSlotState.EMPTY) {
							continue;
						}
						if ((filter == null) || filter.evaluate(globalScope,
								CommonTriggerExecutionScope.filterScope(triggerScope, jassPlayer))) {
							force.add(jassPlayer);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceEnumPlayersCounted",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 1,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final Integer countLimit = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					int count = 0;
					for (int i = 0; (i < this.simulation.getGameData().getMaxPlayers()) && (count < countLimit); i++) {
						final CPlayerJass jassPlayer = this.simulation.getPlayer(i);
						if (jassPlayer.getSlotState() == CPlayerSlotState.EMPTY) {
							continue;
						}
						if ((filter == null) || filter.evaluate(globalScope,
								CommonTriggerExecutionScope.filterScope(triggerScope, jassPlayer))) {
							force.add(jassPlayer);
							count++;
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceEnumAllies",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final CPlayerJass player = nullable(arguments, 1,
							ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					if (player == null) {
						return null;
					}
					// 1.29 runs the filter for every used slot (players who left too) and then keeps the players
					// whose own alliance toward the given player matches.
					for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
						final CPlayerJass jassPlayer = this.simulation.getPlayer(i);
						if (jassPlayer.getSlotState() == CPlayerSlotState.EMPTY) {
							continue;
						}
						if (((filter == null) || filter.evaluate(globalScope,
								CommonTriggerExecutionScope.filterScope(triggerScope, jassPlayer)))
								&& jassPlayer.hasAlliance(player.getId(), CAllianceType.PASSIVE)) {
							force.add(jassPlayer);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceEnumEnemies",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final CPlayerJass player = nullable(arguments, 1,
							ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					if (player == null) {
						return null;
					}
					// 1.29 runs the filter for every used slot (players who left too) and then keeps the players
					// whose own alliance toward the given player matches.
					for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
						final CPlayerJass jassPlayer = this.simulation.getPlayer(i);
						if (jassPlayer.getSlotState() == CPlayerSlotState.EMPTY) {
							continue;
						}
						if (((filter == null) || filter.evaluate(globalScope,
								CommonTriggerExecutionScope.filterScope(triggerScope, jassPlayer)))
								&& !jassPlayer.hasAlliance(player.getId(), CAllianceType.PASSIVE)) {
							force.add(jassPlayer);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForForce",
				(arguments, globalScope, triggerScope) -> {
					final List<CPlayerJass> force = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final CodeJassValue callback = arguments.get(1).visit(CodeJassValueVisitor.getInstance());
					try {
						for (final CPlayerJass player : force) {
							globalScope.runThreadUntilCompletion(globalScope.createThread(callback,
									CommonTriggerExecutionScope.enumScope(triggerScope, player)));
						}
					}
					catch (final Exception e) {
						throw new JassException(globalScope, "Exception during ForForce", e);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsPlayerInForce",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final List<CPlayerJass> force = arguments.get(1)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					return BooleanJassValue.of((whichPlayer != null) && (force != null) && force.contains(whichPlayer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetFilterPlayer",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(playerType,
							((CommonTriggerExecutionScope) triggerScope).getFilterPlayer());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEnumPlayer",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(playerType,
							((CommonTriggerExecutionScope) triggerScope).getEnumPlayer());
				});
	}

	private void registerPlayers() {
		final HandleJassType playerType = type("player");
		final HandleJassType raceType = type("race");

		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerState",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerState whichPlayerState = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					final int value = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					if ((player != null) && (whichPlayerState != null)) {
						player.setPlayerState(this.simulation, whichPlayerState, value);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerHandicapXP",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					// 1.29 reads 1.0 (no handicap) for a null player.
					return RealJassValue.of(whichPlayer == null ? 1.0 : whichPlayer.getHandicapXP());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerHandicap",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float handicap = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichPlayer == null) {
						return null;
					}
					whichPlayer.setHandicap(handicap);
					for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
						if (unit.getPlayerIndex() == whichPlayer.getId()) {
							unit.setHandicap(handicap);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CripplePlayer",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final List<CPlayerJass> toWhichPlayers = arguments.get(1)
							.visit(ObjectJassValueVisitor.<List<CPlayerJass>>getInstance());
					final boolean flag = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					if (whichPlayer != null) {
						crippleExpose(whichPlayer.getId(), toWhichPlayers, flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemovePlayer",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerGameResult result = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (player.getSlotState() != CPlayerSlotState.PLAYING) {
						// The melee scripts remove an observer once more when the game ends, as a neutral result.
						if (player.getRemovedResult() == null) {
							player.setRemovedResult(result);
						}
						return null;
					}
					player.setRemovedResult(result);
					player.setSlotState(CPlayerSlotState.LEFT);
					if (player.getId() != war3MapViewer.getLocalPlayerIndex()) {
						final JassGameEventsWar3 event;
						if (result == CPlayerGameResult.DEFEAT) {
							event = JassGameEventsWar3.EVENT_PLAYER_DEFEAT;
						}
						else if (result == CPlayerGameResult.VICTORY) {
							event = JassGameEventsWar3.EVENT_PLAYER_VICTORY;
						}
						else {
							event = JassGameEventsWar3.EVENT_PLAYER_LEAVE;
						}
						player.firePlayerEvents(CommonTriggerExecutionScope::triggerPlayerScope, event);
					}
					else if ((result == CPlayerGameResult.DEFEAT)
							&& war3MapViewer.getMapConfig().isMapFlagSet(CMapFlag.MAP_OBSERVERS_ON_DEATH)) {
						// 1.29 reports IsPlayerObserver false right after RemovePlayer and true at the next check.
						final CTimer becomeObserver = new ObserverTimer(player, this.war3MapViewer);
						becomeObserver.setTimeoutTime(0.05f);
						becomeObserver.start(this.simulation);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerState",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerState whichPlayerState = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					if ((player == null) || (whichPlayerState == null)) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue
							.of(player.getPlayerState(this.simulation, whichPlayerState));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerScore",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerScore whichScore = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if ((player == null) || (whichScore == null)) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(player.getScore(whichScore));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetFoodMade",
				(arguments, globalScope, triggerScope) -> {
					final Integer id = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final War3ID war3id = new War3ID(id);
					final CUnitType t = this.simulation.getUnitData().getUnitType(war3id);
					return IntegerJassValue.of(t != null ? t.getFoodMade() : 0);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetFoodUsed",
				(arguments, globalScope, triggerScope) -> {
					final Integer id = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final War3ID war3id = new War3ID(id);
					final CUnitType t = this.simulation.getUnitData().getUnitType(war3id);
					return IntegerJassValue.of(t != null ? t.getFoodUsed() : 0);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerTechResearched",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int techIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean specificOnly = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					return BooleanJassValue.of((player != null) && (player.getTechCount(new War3ID(techIdRawcode)) > 0));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerTechCount",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int techIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean specificOnly = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					if (player != null) {
						return IntegerJassValue.of(player.getTechCount(new War3ID(techIdRawcode)));
					}
					return IntegerJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerTechResearched",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int techIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int setToLevel = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final War3ID techIdRawcodeId = new War3ID(techIdRawcode);
					final CUpgradeType upgradeType = this.simulation.getUpgradeData()
							.getType(techIdRawcodeId);
					if ((player != null) && (upgradeType != null)) {
						player.setTechResearched(this.simulation, techIdRawcodeId, setToLevel);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddPlayerTechResearched",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int techIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int levels = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final War3ID techIdRawcodeId = new War3ID(techIdRawcode);
					final CUpgradeType upgradeType = this.simulation.getUpgradeData()
							.getType(techIdRawcodeId);
					if ((player != null) && (upgradeType != null)) {
						player.addTechResearched(this.simulation, techIdRawcodeId, levels);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerTechMaxAllowed",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int techIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int maximum = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					if (player != null) {
						player.setTechtreeMaxAllowed(new War3ID(techIdRawcode), maximum);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerTechMaxAllowed",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int techIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (player == null) {
						return IntegerJassValue.ZERO;
					}
					final int maximum = player.getTechtreeMaxAllowed(new War3ID(techIdRawcode));
					return IntegerJassValue.of(maximum < 0 ? Integer.MAX_VALUE : maximum);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerAbilityAvailable",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int abilityIdRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final boolean enabled = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					if (player != null) {
						player.setAbilityEnabled(this.simulation, new War3ID(abilityIdRawcode), enabled);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsPlayerObserver",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (player == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(player.isObserver());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetPlayerHandicapXP",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float handicap = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (player != null) {
						player.setHandicapXP(handicap);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerHandicap",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					// 1.29 reads 1.0 (no handicap) for a null player.
					return RealJassValue.of(whichPlayer == null ? 1.0 : whichPlayer.getHandicap());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerRace",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					// 1.29 reads RACE_HUMAN for a null player.
					if (whichPlayer == null) {
						return new HandleJassValue(raceType, this.war3MapViewer.getGameData().getRaceManager().getRace(1));
					}
					return new HandleJassValue(raceType, whichPlayer.getRace());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLocalPlayer",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(playerType,
							this.simulation.getPlayer(war3MapViewer.getLocalPlayerIndex()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerStructureCount",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichPlayer == null) {
						return IntegerJassValue.ZERO;
					}
					final boolean includeIncomplete = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final List<CUnit> units = this.simulation.getUnitsIncludingNew();
					int count = 0;
					for (final CUnit unit : units) {
						if ((unit.getPlayerIndex() == whichPlayer.getId()) && !unit.isDead()) {
							if (unit.isBuilding()) {
								if (includeIncomplete || !unit.isConstructing()) {
									count++;
								}
							}
							else if (unit.isUprooted() && includeIncomplete) {
								// Measured on 1.29: an uprooted ancient adds two to this count and none without
								// incomplete structures.
								count += 2;
							}
						}
					}
					return IntegerJassValue.of(count);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetPlayerTypedUnitCount",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichPlayer == null) {
						return IntegerJassValue.ZERO;
					}
					final String legacySystemUnitTypeName = arguments.get(1)
							.visit(StringJassValueVisitor.getInstance());
					final boolean includeIncomplete = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final boolean includeUpgrades = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());
					final List<CUnit> units = this.simulation.getUnitsIncludingNew();
					int count = 0;
					final Set<War3ID> countedTypes = includeUpgrades
							? typeAndUpgradesForLegacyName(legacySystemUnitTypeName)
							: null;
					for (final CUnit unit : units) {
						if ((unit.getPlayerIndex() == whichPlayer.getId())
								&& (!unit.isDead() || unit.isFalseDeath()) && !unit.isAnimatedCorpse()) {
							final boolean matches = countedTypes != null
									? countedTypes.contains(unit.getTypeId())
									: legacySystemUnitTypeName.equals(unit.getUnitType().getLegacyName());
							if (matches && (includeIncomplete || !unit.isConstructing())) {
								count++;
							}
							for (int i = 0; includeIncomplete && (i < WarsmashConstants.BUILD_QUEUE_SIZE); i++) {
								final War3ID trainingTypeId = unit.getQueuedTrainingUnitTypeId(i);
								if (trainingTypeId == null) {
									continue;
								}
								final CUnitType trainingType = this.simulation.getUnitData().getUnitType(trainingTypeId);
								if (countedTypes != null ? countedTypes.contains(trainingTypeId)
										: ((trainingType != null)
												&& legacySystemUnitTypeName.equals(trainingType.getLegacyName()))) {
									count++;
								}
							}
						}
					}
					return IntegerJassValue.of(count);
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetPlayerTownHallCount",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer whichPlayer = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichPlayer == null) {
						return IntegerJassValue.ZERO;
					}
					int count = 0;
					for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
						if ((unit.getPlayerIndex() == whichPlayer.getId()) && !unit.isDead()
								&& unit.isUnitType(CUnitTypeJass.TOWNHALL)) {
							count++;
						}
					}
					return IntegerJassValue.of(count);
				});
	}

	/**
	 * CripplePlayer: with the flag the player's units are shown to every player in the force; without it they are
	 * hidden from those players again, or from everyone when the force is empty (Blizzard.j resets that way).
	 */
	private void crippleExpose(final int crippledIndex, final List<CPlayerJass> toWhichPlayers,
			final boolean flag) {
		if (!flag && ((toWhichPlayers == null) || toWhichPlayers.isEmpty())) {
			for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
				setCrippleExposed(crippledIndex, i, false);
			}
			return;
		}
		if (toWhichPlayers != null) {
			for (final CPlayerJass viewer : toWhichPlayers) {
				setCrippleExposed(crippledIndex, viewer.getId(), flag);
			}
		}
	}

	private void setCrippleExposed(final int crippledIndex, final int viewerIndex, final boolean exposed) {
		final long key = ((long) crippledIndex << 32) | viewerIndex;
		final CCrippledPlayerFogModifier current = this.crippleModifiers.get(key);
		if (exposed && (current == null)) {
			final CCrippledPlayerFogModifier modifier = new CCrippledPlayerFogModifier(crippledIndex);
			this.crippleModifiers.put(key, modifier);
			this.simulation.getPlayer(viewerIndex).addFogModifer(this.simulation, modifier, false);
		}
		else if (!exposed && (current != null)) {
			this.crippleModifiers.remove(key);
			this.simulation.getPlayer(viewerIndex).removeFogModifer(this.simulation, current);
		}
	}

	/**
	 * The unit types named by a legacy name plus every type they upgrade into, so that a Keep counts as a
	 * "townhall" when GetPlayerTypedUnitCount includes upgrades (the melee town hall check).
	 */
	private Set<War3ID> typeAndUpgradesForLegacyName(final String legacyName) {
		return this.typeAndUpgradesByLegacyName.computeIfAbsent(legacyName, (name) -> {
			final CUnitData unitData = this.simulation.getUnitData();
			final Set<War3ID> result = new HashSet<>();
			final List<War3ID> open = new ArrayList<>();
			for (final CUnitType type : unitData.getAllUnitTypesLightweight()) {
				if (name.equals(type.getLegacyName())) {
					open.add(type.getTypeId());
				}
			}
			while (!open.isEmpty()) {
				final War3ID typeId = open.remove(open.size() - 1);
				if (result.add(typeId)) {
					final CUnitType type = unitData.getUnitType(typeId);
					if (type != null) {
						open.addAll(type.getUpgradesTo());
					}
				}
			}
			return result;
		});
	}

	/** Makes a player that RemovePlayer defeated an observer, when the map lets the defeated watch. */
	private static final class ObserverTimer extends CTimer {
		// Saves from before the natives were split hold the fields the anonymous timer class captured.
		@SavedFormerly("val$player")
		private final CPlayer player;
		@SavedFormerly("val$war3MapViewer")
		private final War3MapViewer war3MapViewer;

		private ObserverTimer(final CPlayer player, final War3MapViewer war3MapViewer) {
			this.player = player;
			this.war3MapViewer = war3MapViewer;
		}

		@Override
		public void onFire(final CSimulation simulation) {
			this.player.setPlayerState(simulation, CPlayerState.OBSERVER, 1);
			this.war3MapViewer.startObserverView();
		}
	}
}
