package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.UnknownEnumHandle;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CCircleFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CFogModifierJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CFogModifierJassMulti;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CFogModifierJassSingle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CRectFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CGameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentWeather;
import java.util.ArrayList;

/**
 * Fog of war, visibility, weather, time of day, game state and cheats.
 */
final class WorldNatives extends CommonNatives {
	WorldNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerGameState();
		registerTimeOfDay();
		registerFog();
	}

	private void registerGameState() {
		final HandleJassType weathereffectType = type("weathereffect");

		// Measured on 1.29: outside a Battle.net tournament there is no finish rule, player or time, and the score of
		// a player is what its living units are worth: gold and lumber cost, and a hero's experience.
		jassProgramVisitor.getJassNativeManager().createNative("GetTournamentFinishSoonTimeRemaining",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(0));
		jassProgramVisitor.getJassNativeManager().createNative("GetTournamentFinishNowRule",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(0));
		jassProgramVisitor.getJassNativeManager().createNative("GetTournamentFinishNowPlayer",
				(arguments, globalScope, triggerScope) -> new HandleJassValue(type("player"), null));
		jassProgramVisitor.getJassNativeManager().createNative("GetTournamentScore",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(player == null ? 0 : tournamentScore(player.getId()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSaveBasicFilename",
				(arguments, globalScope, triggerScope) -> new StringJassValue(this.simulation.getSaveBasicFilename()));
		jassProgramVisitor.getJassNativeManager().createNative("SetTerrainFogEx",
				(arguments, globalScope, triggerScope) -> {
					final Integer fogStyle = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final Double start = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final Double end = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final Double density = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final Double r = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					final Double g = arguments.get(5).visit(RealJassValueVisitor.getInstance());
					final Double b = arguments.get(6).visit(RealJassValueVisitor.getInstance());
					war3MapViewer.worldScene.fogSettings.color = new Color(r.floatValue(), g.floatValue(),
							b.floatValue(), 1.0f);
					war3MapViewer.worldScene.fogSettings.density = density.floatValue();
					war3MapViewer.worldScene.fogSettings.start = start.floatValue();
					war3MapViewer.worldScene.fogSettings.end = end.floatValue();
					war3MapViewer.worldScene.fogSettings.setStyleByIndex(fogStyle + 1);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResetTerrainFog",
				(arguments, globalScope, triggerScope) -> {
					war3MapViewer.resetTerrainFog();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetFloatGameState",
				(arguments, globalScope, triggerScope) -> {
					final CGameState gameState = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					switch (gameState) {
					case TIME_OF_DAY:
						return RealJassValue.of(this.simulation.getScriptTimeOfDay());
					}
					throw new IllegalArgumentException("Not a float game state: " + gameState);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetIntegerGameState",
				(arguments, globalScope, triggerScope) -> {
					final CGameState gameState = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					switch (gameState) {
					case DISCONNECTED:
						return IntegerJassValue.of(this.simulation.getDisconnectedGameState());
					case DIVINE_INTERVENTION:
						return IntegerJassValue
								.of(this.simulation.getDivineInterventionGameState());
					}
					throw new IllegalArgumentException("Not an integer game state: " + gameState);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetIntegerGameState",
				(arguments, globalScope, triggerScope) -> {
					final CGameState gameState = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int value = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (gameState == CGameState.DISCONNECTED) {
						this.simulation.setDisconnectedGameState(value);
					}
					else if (gameState == CGameState.DIVINE_INTERVENTION) {
						this.simulation.setDivineInterventionGameState(value);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetFloatGameState",
				(arguments, globalScope, triggerScope) -> {
					final CGameState gameState = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float value = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					switch (gameState) {
					case TIME_OF_DAY:
						this.simulation.setGameTimeOfDay(value);
						return null;
					}
					throw new IllegalArgumentException("Not a float game state: " + gameState);
				});
		jassProgramVisitor.getJassNativeManager().createNative("PauseGame",
				(arguments, globalScope, triggerScope) -> {
					final boolean flag = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					war3MapViewer.setGamePaused(War3MapViewer.PauseSource.SCRIPT, flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetWaterBaseColor",
				(arguments, globalScope, triggerScope) -> {
					final int red = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final int green = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int blue = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final int alpha = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					war3MapViewer.setWaterBaseColor(
							new float[] { red / 255f, green / 255f, blue / 255f, alpha / 255f });
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddWeatherEffect",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle where = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int effectId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (where == null) {
						return new HandleJassValue(weathereffectType, null);
					}
					return new HandleJassValue(weathereffectType,
							war3MapViewer.createWeatherEffect(new War3ID(effectId), where.x, where.y,
									where.x + where.width, where.y + where.height));
				});
		jassProgramVisitor.getJassNativeManager().createNative("EnableWeatherEffect",
				(arguments, globalScope, triggerScope) -> {
					final SimulationRenderComponentWeather weather = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final boolean enable = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (weather != null) {
						weather.setEnabled(enable);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveWeatherEffect",
				(arguments, globalScope, triggerScope) -> {
					final SimulationRenderComponentWeather weather = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					if (weather != null) {
						weather.remove();
					}
					return null;
				});
		// Measured on 1.29: only the chat cheats itvexesme and strengthandhonor turn these on, not Cheat().
		jassProgramVisitor.getJassNativeManager().createNative("IsNoVictoryCheat",
				(arguments, globalScope, triggerScope) -> BooleanJassValue
						.of(com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats.isNoVictory(simulation)));
		jassProgramVisitor.getJassNativeManager().createNative("IsNoDefeatCheat",
				(arguments, globalScope, triggerScope) -> BooleanJassValue
						.of(com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats.isNoDefeat(simulation)));
		// Measured on 1.29: the release chat cheats work from a script and affect every
		// player; the developer cheats campaign AI scripts call (showmap, av, dg, vision N, gold N, ...) do
		// nothing.
		jassProgramVisitor.getJassNativeManager().createNative("Cheat",
				(arguments, globalScope, triggerScope) -> {
					final String cheat = nullable(arguments, 0, StringJassValueVisitor.getInstance());
					com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats.apply(simulation, cheat);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetGameTurnTick",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(this.simulation.getGameTurnTick());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSimulationStepTime",
				(arguments, globalScope, triggerScope) -> {
					return RealJassValue.of(WarsmashConstants.SIMULATION_STEP_TIME);
				});
	}

	private void registerTimeOfDay() {
		jassProgramVisitor.getJassNativeManager().createNative("SetDayNightModels",
				(arguments, globalScope, triggerScope) -> {
					final String terrainDNCFile = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final String unitDNCFile = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					war3MapViewer.setDayNightModels(terrainDNCFile, unitDNCFile);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SuspendTimeOfDay",
				(arguments, globalScope, triggerScope) -> {
					final boolean flag = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					this.simulation.setTimeOfDaySuspended(flag);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetTimeOfDayScale",
				(arguments, globalScope, triggerScope) -> {
					final float r = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					this.simulation.setTimeOfDayScale(r);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTimeOfDayScale",
				(arguments, globalScope, triggerScope) -> {
					return RealJassValue.of(this.simulation.getTimeOfDayScale());
				});
		// CinematicModeBJ saves and restores this flag; the dawn and dusk sounds it switches are not played here.
		final boolean[] dawnDuskEnabled = { true };
		jassProgramVisitor.getJassNativeManager().createNative("EnableDawnDusk",
				(arguments, globalScope, triggerScope) -> {
					dawnDuskEnabled[0] = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsDawnDuskEnabled",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(dawnDuskEnabled[0]));
		jassProgramVisitor.getJassNativeManager().createNative("SetFalseTimeOfDay",
				(arguments, globalScope, triggerScope) -> {
					final int hour = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final int minute = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final float duration = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();

					this.simulation.addFalseTimeOfDay(hour, minute, duration);
					return null;
				});
	}

	private void registerFog() {
		final HandleJassType fogmodifierType = type("fogmodifier");

		jassProgramVisitor.getJassNativeManager().createNative("SetFogStateRect",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CFogState whichState = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final Rectangle whichRect = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					final boolean sharedVision = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());

					if ((player == null) || (whichRect == null)) {
						return null;
					}
					final PathingGrid pathingGrid = this.simulation.getPathingGrid();
					if (sharedVision) {
						for (int playerIndex = 0; playerIndex < this.simulation.getGameData().getMaxPlayers(); playerIndex++) {
							if (player.hasAlliance(playerIndex, CAllianceType.SHARED_VISION)
									|| player.hasAlliance(playerIndex, CAllianceType.SHARED_VISION_FORCED)) {
								final CPlayer alliedPlayer = this.simulation.getPlayer(playerIndex);
								alliedPlayer.getFogOfWar().setFogStateRect(pathingGrid, whichRect, whichState);
							}
						}
					}
					else {
						player.getFogOfWar().setFogStateRect(pathingGrid, whichRect, whichState);
					}

					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetFogStateRadius",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CFogState whichState = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final float centerX = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float centerY = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float radius = arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean sharedVision = arguments.get(5).visit(BooleanJassValueVisitor.getInstance());

					if (player == null) {
						return null;
					}
					final PathingGrid pathingGrid = this.simulation.getPathingGrid();
					if (sharedVision) {
						for (int playerIndex = 0; playerIndex < this.simulation.getGameData().getMaxPlayers(); playerIndex++) {
							if (player.hasAlliance(playerIndex, CAllianceType.SHARED_VISION)
									|| player.hasAlliance(playerIndex, CAllianceType.SHARED_VISION_FORCED)) {
								final CPlayer alliedPlayer = this.simulation.getPlayer(playerIndex);
								alliedPlayer.getFogOfWar().setFogStateRadius(pathingGrid, centerX, centerY, radius,
										whichState);
							}
						}
					}
					else {
						player.getFogOfWar().setFogStateRadius(pathingGrid, centerX, centerY, radius, whichState);
					}

					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetFogStateRadiusLoc",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CFogState whichState = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final AbilityPointTarget center = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					final float radius = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean sharedVision = arguments.get(4).visit(BooleanJassValueVisitor.getInstance());

					if ((player == null) || (center == null)) {
						return null;
					}
					final float centerX = center.x;
					final float centerY = center.y;
					final PathingGrid pathingGrid = this.simulation.getPathingGrid();
					if (sharedVision) {
						for (int playerIndex = 0; playerIndex < this.simulation.getGameData().getMaxPlayers(); playerIndex++) {
							if (player.hasAlliance(playerIndex, CAllianceType.SHARED_VISION)
									|| player.hasAlliance(playerIndex, CAllianceType.SHARED_VISION_FORCED)) {
								final CPlayer alliedPlayer = this.simulation.getPlayer(playerIndex);
								alliedPlayer.getFogOfWar().setFogStateRadius(pathingGrid, centerX, centerY, radius,
										whichState);
							}
						}
					}
					else {
						player.getFogOfWar().setFogStateRadius(pathingGrid, centerX, centerY, radius, whichState);
					}

					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FogMaskEnable",
				(arguments, globalScope, triggerScope) -> {
					final boolean enable = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					this.simulation.setFogMaskEnabled(enable);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FogEnable",
				(arguments, globalScope, triggerScope) -> {
					final boolean enable = arguments.get(0).visit(BooleanJassValueVisitor.getInstance());
					if (enable && !this.simulation.isFogEnabled()) {
						// Measured on 1.29: turning fog back on fogs what was seen until the next fog update.
						for (int i = 0; i < this.simulation.getGameData().getMaxPlayers(); i++) {
							this.simulation.getPlayer(i).getFogOfWar().convertVisibleToFogged();
						}
					}
					this.simulation.setFogEnabled(enable);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsFogEnabled",
				(arguments, globalScope, triggerScope) -> {
					return BooleanJassValue.of(this.simulation.isFogEnabled());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsFogMaskEnabled",
				(arguments, globalScope, triggerScope) -> {
					return BooleanJassValue.of(this.simulation.isFogMaskEnabled());
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateFogModifierRect",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final Object whichStateValue = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if ((player != null) && (whichStateValue instanceof UnknownEnumHandle)) {
						// 1.29 makes a modifier for a fog state it has no constant for; it changes nothing.
						return newFogModifier(fogmodifierType, new CFogModifierJassMulti(new ArrayList<>()));
					}
					final CFogState whichState = (CFogState) whichStateValue;
					final Rectangle whichRect = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					if (whichRect == null) {
						// Measured on 1.29: no modifier without a rect.
						return fogmodifierType.getNullValue();
					}
					final boolean sharedVision = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());
					final boolean afterUnits = arguments.get(4).visit(BooleanJassValueVisitor.getInstance());

					final CRectFogModifier fogModifier = new CRectFogModifier(whichState, whichRect);
					if (player == null) {
						return fogmodifierType.getNullValue();
					}
					fogModifier.setEnabled(false);
					// Measured on 1.29: a modifier belongs to its own player; allies get it through shared vision at each
					// fog update, and only when the modifier is made with the shared vision flag.
					fogModifier.setSharedVision(sharedVision);
					player.addFogModifer(this.simulation, fogModifier, afterUnits);
					return newFogModifier(fogmodifierType, new CFogModifierJassSingle(player.getId(), fogModifier));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateFogModifierRadius",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final Object whichStateValue = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if ((player != null) && (whichStateValue instanceof UnknownEnumHandle)) {
						// 1.29 makes a modifier for a fog state it has no constant for; it changes nothing.
						return newFogModifier(fogmodifierType, new CFogModifierJassMulti(new ArrayList<>()));
					}
					final CFogState whichState = (CFogState) whichStateValue;
					final float centerX = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float centerY = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float radius = arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean sharedVision = arguments.get(5).visit(BooleanJassValueVisitor.getInstance());
					final boolean afterUnits = arguments.get(6).visit(BooleanJassValueVisitor.getInstance());

					final CCircleFogModifier fogModifier = new CCircleFogModifier(whichState, radius, centerX,
							centerY);
					if (player == null) {
						return fogmodifierType.getNullValue();
					}
					fogModifier.setEnabled(false);
					// Measured on 1.29: a modifier belongs to its own player; allies get it through shared vision at each
					// fog update, and only when the modifier is made with the shared vision flag.
					fogModifier.setSharedVision(sharedVision);
					player.addFogModifer(this.simulation, fogModifier, afterUnits);
					return newFogModifier(fogmodifierType, new CFogModifierJassSingle(player.getId(), fogModifier));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateFogModifierRadiusLoc",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final Object whichStateValue = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if ((player != null) && (whichStateValue instanceof UnknownEnumHandle)) {
						// 1.29 makes a modifier for a fog state it has no constant for; it changes nothing.
						return newFogModifier(fogmodifierType, new CFogModifierJassMulti(new ArrayList<>()));
					}
					final CFogState whichState = (CFogState) whichStateValue;
					final AbilityPointTarget center = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					if (center == null) {
						// Measured on 1.29: no modifier without a location.
						return fogmodifierType.getNullValue();
					}
					final float radius = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean sharedVision = arguments.get(4).visit(BooleanJassValueVisitor.getInstance());
					final boolean afterUnits = arguments.get(5).visit(BooleanJassValueVisitor.getInstance());
					final float centerX = center.x;
					final float centerY = center.y;

					final CCircleFogModifier fogModifier = new CCircleFogModifier(whichState, radius, centerX,
							centerY);
					if (player == null) {
						return fogmodifierType.getNullValue();
					}
					fogModifier.setEnabled(false);
					// Measured on 1.29: a modifier belongs to its own player; allies get it through shared vision at each
					// fog update, and only when the modifier is made with the shared vision flag.
					fogModifier.setSharedVision(sharedVision);
					player.addFogModifer(this.simulation, fogModifier, afterUnits);
					return newFogModifier(fogmodifierType, new CFogModifierJassSingle(player.getId(), fogModifier));
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyFogModifier",
				(arguments, globalScope, triggerScope) -> {
					final CFogModifierJass modifier = nullable(arguments, 0,
							ObjectJassValueVisitor.<CFogModifierJass>getInstance());
					if (modifier != null) {
						modifier.destroy(this.simulation);
						release(modifier);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FogModifierStart",
				(arguments, globalScope, triggerScope) -> {
					final CFogModifierJass modifier = nullable(arguments, 0,
							ObjectJassValueVisitor.<CFogModifierJass>getInstance());
					if (modifier != null) {
						// Measured on 1.29: a started modifier shows its area from the next fog update on.
						modifier.setEnabled(true);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FogModifierStop",
				(arguments, globalScope, triggerScope) -> {
					final CFogModifierJass modifier = nullable(arguments, 0,
							ObjectJassValueVisitor.<CFogModifierJass>getInstance());
					if (modifier != null) {
						// Measured on 1.29: what the player sees stays seen until the next fog update, even the
						// modifier's own area.
						modifier.setEnabled(false);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsVisibleToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final CPlayer whichPlayer = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichPlayer != null) && (fogState(whichPlayer, x, y) == CFogState.VISIBLE));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsLocationVisibleToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichLocation != null) && (whichPlayer != null)
							&& (fogState(whichPlayer, whichLocation.getX(), whichLocation.getY()) == CFogState.VISIBLE));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsFoggedToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final CPlayer whichPlayer = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichPlayer != null) && (fogState(whichPlayer, x, y) == CFogState.FOGGED));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsLocationFoggedToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichLocation != null) && (whichPlayer != null)
							&& (fogState(whichPlayer, whichLocation.getX(), whichLocation.getY()) == CFogState.FOGGED));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsMaskedToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final float x = arguments.get(0).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final CPlayer whichPlayer = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichPlayer != null) && (fogState(whichPlayer, x, y) == CFogState.MASKED));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsLocationMaskedToPlayer",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichLocation != null) && (whichPlayer != null)
							&& (fogState(whichPlayer, whichLocation.getX(), whichLocation.getY()) == CFogState.MASKED));
				});
	}

	/**
	 * Measured on 1.29: the gold and lumber cost of the player's living units and the experience of its heroes; items,
	 * research and resources do not count. A removed unit still counts until the end of the step, when it leaves the
	 * game; a dead one no longer counts.
	 */
	private int tournamentScore(final int playerIndex) {
		int score = 0;
		for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
			if ((unit.getPlayerIndex() != playerIndex) || unit.isDead()) {
				continue;
			}
			score += unit.getUnitType().getGoldCost() + unit.getUnitType().getLumberCost();
			if (unit.getHeroData() != null) {
				score += unit.getHeroData().getXp();
			}
		}
		return score;
	}
}
