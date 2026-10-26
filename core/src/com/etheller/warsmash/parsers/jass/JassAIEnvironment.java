package com.etheller.warsmash.parsers.jass;

import java.util.List;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.function.JassNativeManager;
import com.etheller.interpreter.ast.util.JassLog;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.jass.ai.AIController;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;

/**
 * The natives that common.ai declares, for one computer player's AI script.
 */
public class JassAIEnvironment {
	private static final int MAX_REPORTED_ERRORS = 20;

	private final JassProgram jassProgram;
	private final CSimulation simulation;
	private final int playerIndex;
	private final AIController controller;
	private final HandleJassType unitType;
	private boolean paused;
	private int errors;

	public JassAIEnvironment(final JassProgram jassProgramVisitor, final DataSource dataSource,
			final Viewport uiViewport, final Scene uiScene, final CSimulation simulation, final int playerIndex,
			final WarsmashUI meleeUI) {
		this.jassProgram = jassProgramVisitor;
		this.simulation = simulation;
		this.playerIndex = playerIndex;
		this.controller = new AIController(simulation, jassProgramVisitor.getGlobalScope(), playerIndex);
		this.unitType = jassProgramVisitor.getGlobalScope().getHandleType("unit");

		final JassNativeManager natives = jassProgramVisitor.getJassNativeManager();
		final AIController ai = this.controller;

		natives.createNative("StartThread", (arguments, globalScope, triggerScope) -> {
			final CodeJassValue threadFunction = arguments.get(0).visit(CodeJassValueVisitor.getInstance());
			globalScope.queueThread(globalScope.createThread(threadFunction));
			return null;
		});
		natives.createNative("DoAiScriptDebug", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		natives.createNative("GetAiPlayer", (arguments, globalScope, triggerScope) -> IntegerJassValue.of(playerIndex));
		natives.createNative("MeleeDifficulty",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getMeleeDifficulty()));
		natives.createNative("GetHeroId",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getLevelingHeroId()));
		natives.createNative("GetHeroLevelAI",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getLevelingHeroLevel()));

		// Counting
		natives.createNative("GetUnitCount",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getUnitCount(integer(arguments, 0), false)));
		natives.createNative("GetUnitCountDone",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getUnitCount(integer(arguments, 0), true)));
		natives.createNative("GetPlayerUnitTypeCount", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			if (player == null) {
				return IntegerJassValue.ZERO;
			}
			return IntegerJassValue.of(ai.countUnits(player.getId(), new War3ID(integer(arguments, 1)), false, -1));
		});
		natives.createNative("GetTownUnitCount", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(ai.getTownUnitCount(integer(arguments, 0), integer(arguments, 1), bool(arguments, 2))));
		natives.createNative("GetUnitGoldCost", (arguments, globalScope, triggerScope) -> {
			final CUnitType type = simulation.getUnitData().getUnitType(new War3ID(integer(arguments, 0)));
			return IntegerJassValue.of(type == null ? 0 : type.getGoldCost());
		});
		natives.createNative("GetUnitWoodCost", (arguments, globalScope, triggerScope) -> {
			final CUnitType type = simulation.getUnitData().getUnitType(new War3ID(integer(arguments, 0)));
			return IntegerJassValue.of(type == null ? 0 : type.getLumberCost());
		});
		natives.createNative("IgnoredUnits", (arguments, globalScope, triggerScope) -> IntegerJassValue.ZERO);

		// Towns and expansions
		natives.createNative("GetMinesOwned",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getMinesOwned()));
		natives.createNative("GetGoldOwned",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getGoldOwned()));
		natives.createNative("TownWithMine",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.townWithMine()));
		natives.createNative("TownHasMine",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.townHasMine(integer(arguments, 0))));
		natives.createNative("TownHasHall",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.townHasHall(integer(arguments, 0))));
		natives.createNative("GetNextExpansion",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getNextExpansion()));
		natives.createNative("GetExpansionFoe", (arguments, globalScope, triggerScope) -> unit(ai.getExpansionFoe()));
		natives.createNative("GetExpansionX",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getExpansionX()));
		natives.createNative("GetExpansionY",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getExpansionY()));
		natives.createNative("GetExpansionPeon",
				(arguments, globalScope, triggerScope) -> unit(ai.getExpansionPeon()));
		natives.createNative("SetExpansion", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(ai.setExpansion(object(arguments, 0), integer(arguments, 1))));

		// Production
		natives.createNative("SetProduce", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(ai.setProduce(integer(arguments, 0), integer(arguments, 1), integer(arguments, 2))));
		natives.createNative("SetUpgrade",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.setUpgrade(integer(arguments, 0))));
		natives.createNative("GetUpgradeLevel",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getUpgradeLevel(integer(arguments, 0))));
		natives.createNative("GetUpgradeGoldCost", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(ai.getUpgradeGoldCost(integer(arguments, 0))));
		natives.createNative("GetUpgradeWoodCost", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(ai.getUpgradeWoodCost(integer(arguments, 0))));
		natives.createNative("SetHeroLevels", (arguments, globalScope, triggerScope) -> {
			ai.setHeroLevelsFunction(arguments.get(0) == null ? null
					: arguments.get(0).visit(CodeJassValueVisitor.getInstance()));
			return null;
		});
		natives.createNative("SetNewHeroes", setter((value) -> ai.setNewHeroes(value)));
		// Zeppelins come from goblin laboratories; the melee scripts only buy them late and can do without.
		natives.createNative("PurchaseZeppelin", (arguments, globalScope, triggerScope) -> null);
		natives.createNative("MergeUnits", (arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai
				.mergeUnits(integer(arguments, 0), integer(arguments, 1), integer(arguments, 2), integer(arguments, 3))));
		natives.createNative("ConvertUnits", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(ai.convertUnits(integer(arguments, 0), integer(arguments, 1))));
		natives.createNative("Unsummon", (arguments, globalScope, triggerScope) -> {
			ai.unsummon(object(arguments, 0));
			return null;
		});

		// Settings
		natives.createNative("SetCampaignAI", (arguments, globalScope, triggerScope) -> {
			ai.setCampaign(true);
			return null;
		});
		natives.createNative("SetMeleeAI", (arguments, globalScope, triggerScope) -> {
			ai.setCampaign(false);
			return null;
		});
		natives.createNative("SetTargetHeroes", setter((value) -> ai.setTargetHeroes(value)));
		natives.createNative("SetPeonsRepair", setter((value) -> ai.setPeonsRepair(value)));
		natives.createNative("SetHeroesFlee", setter((value) -> ai.setHeroesFlee(value)));
		natives.createNative("SetUnitsFlee", setter((value) -> ai.setUnitsFlee(value)));
		natives.createNative("SetGroupsFlee", setter((value) -> ai.setGroupsFlee(value)));
		natives.createNative("SetIgnoreInjured", setter((value) -> ai.setIgnoreInjured(value)));
		natives.createNative("SetHeroesTakeItems", setter((value) -> ai.setHeroesTakeItems(value)));
		for (final String ignored : new String[] { "SetRandomPaths", "SetDefendPlayer", "SetHeroesBuyItems",
				"SetWatchMegaTargets", "SetSlowChopping", "SetCaptainChanges", "SetSmartArtillery",
				"GroupTimedLife" }) {
			natives.createNative(ignored, setter((value) -> {
			}));
		}
		natives.createNative("SetReplacementCount", (arguments, globalScope, triggerScope) -> {
			ai.setReplacementCount(integer(arguments, 0));
			return null;
		});
		natives.createNative("DisablePathing", (arguments, globalScope, triggerScope) -> null);
		natives.createNative("SetAmphibious", (arguments, globalScope, triggerScope) -> null);

		// Harvesting
		natives.createNative("ClearHarvestAI", (arguments, globalScope, triggerScope) -> {
			ai.clearHarvestAI();
			return null;
		});
		natives.createNative("HarvestGold", (arguments, globalScope, triggerScope) -> {
			ai.harvestGold(integer(arguments, 0), integer(arguments, 1));
			return null;
		});
		natives.createNative("HarvestWood", (arguments, globalScope, triggerScope) -> {
			ai.harvestWood(integer(arguments, 0), integer(arguments, 1));
			return null;
		});
		natives.createNative("StopGathering", (arguments, globalScope, triggerScope) -> {
			ai.stopGathering();
			return null;
		});
		// Guard posts belong to campaign scripts; melee units defend through the defense captain.
		natives.createNative("AddGuardPost", (arguments, globalScope, triggerScope) -> null);
		natives.createNative("FillGuardPosts", (arguments, globalScope, triggerScope) -> null);
		natives.createNative("ReturnGuardPosts", (arguments, globalScope, triggerScope) -> null);

		// Captains
		natives.createNative("CreateCaptains", (arguments, globalScope, triggerScope) -> {
			ai.createCaptains();
			return null;
		});
		natives.createNative("SetCaptainHome", (arguments, globalScope, triggerScope) -> {
			ai.setCaptainHome(integer(arguments, 0), real(arguments, 1), real(arguments, 2));
			return null;
		});
		natives.createNative("ResetCaptainLocs", (arguments, globalScope, triggerScope) -> {
			ai.resetCaptainLocations();
			return null;
		});
		natives.createNative("ShiftTownSpot", (arguments, globalScope, triggerScope) -> {
			ai.shiftTownSpot(real(arguments, 0), real(arguments, 1));
			return null;
		});
		natives.createNative("TeleportCaptain", (arguments, globalScope, triggerScope) -> {
			ai.teleportCaptain(real(arguments, 0), real(arguments, 1));
			return null;
		});
		natives.createNative("ClearCaptainTargets", (arguments, globalScope, triggerScope) -> {
			ai.clearCaptainTargets();
			return null;
		});
		natives.createNative("CaptainAttack", (arguments, globalScope, triggerScope) -> {
			ai.attackMoveXY(real(arguments, 0), real(arguments, 1));
			return null;
		});
		natives.createNative("AttackMoveXY", (arguments, globalScope, triggerScope) -> {
			ai.attackMoveXY(integer(arguments, 0), integer(arguments, 1));
			return null;
		});
		natives.createNative("AttackMoveKill", (arguments, globalScope, triggerScope) -> {
			ai.attackMoveKill(object(arguments, 0));
			return null;
		});
		natives.createNative("CaptainVsUnits", (arguments, globalScope, triggerScope) -> {
			ai.captainVs(object(arguments, 0), false);
			return null;
		});
		natives.createNative("CaptainVsPlayer", (arguments, globalScope, triggerScope) -> {
			ai.captainVs(object(arguments, 0), true);
			return null;
		});
		natives.createNative("CaptainGoHome", (arguments, globalScope, triggerScope) -> {
			ai.captainGoHome();
			return null;
		});
		natives.createNative("CaptainIsHome",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isCaptainHome()));
		natives.createNative("CaptainIsFull",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isCaptainFull()));
		natives.createNative("CaptainIsEmpty",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isCaptainEmpty()));
		natives.createNative("CaptainGroupSize",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getCaptainGroupSize()));
		natives.createNative("CaptainReadiness",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getCaptainReadiness()));
		natives.createNative("CaptainReadinessHP",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getCaptainReadinessHP()));
		natives.createNative("CaptainReadinessMa",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getCaptainReadinessMa()));
		natives.createNative("CaptainRetreating",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isCaptainRetreating()));
		natives.createNative("CaptainAtGoal",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isCaptainAtGoal()));
		natives.createNative("CaptainInCombat",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isCaptainInCombat(bool(arguments, 0))));
		natives.createNative("InitAssault", (arguments, globalScope, triggerScope) -> {
			ai.initAssault();
			return null;
		});
		natives.createNative("AddAssault", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(ai.addAssault(integer(arguments, 0), integer(arguments, 1))));
		natives.createNative("AddDefenders", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(ai.addDefenders(integer(arguments, 0), integer(arguments, 1))));
		natives.createNative("RemoveInjuries", (arguments, globalScope, triggerScope) -> {
			ai.removeInjuries();
			return null;
		});
		natives.createNative("RemoveSiege", (arguments, globalScope, triggerScope) -> {
			ai.removeSiege();
			return null;
		});
		natives.createNative("SetStagePoint", (arguments, globalScope, triggerScope) -> null);
		natives.createNative("LoadZepWave", (arguments, globalScope, triggerScope) -> null);
		natives.createNative("SuicidePlayer", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(ai.suicidePlayer(object(arguments, 0), bool(arguments, 1), true)));
		natives.createNative("SuicidePlayerUnits", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(ai.suicidePlayer(object(arguments, 0), bool(arguments, 1), false)));
		natives.createNative("SuicideUnit", (arguments, globalScope, triggerScope) -> {
			ai.suicideUnit(integer(arguments, 0), integer(arguments, 1), -1);
			return null;
		});
		natives.createNative("SuicideUnitEx", (arguments, globalScope, triggerScope) -> {
			ai.suicideUnit(integer(arguments, 0), integer(arguments, 1), integer(arguments, 2));
			return null;
		});

		// Map knowledge
		natives.createNative("GetCreepCamp", (arguments, globalScope, triggerScope) -> unit(
				ai.getCreepCamp(integer(arguments, 0), integer(arguments, 1), bool(arguments, 2))));
		natives.createNative("CreepsOnMap",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.creepsOnMap()));
		natives.createNative("StartGetEnemyBase", (arguments, globalScope, triggerScope) -> {
			ai.startGetEnemyBase();
			return null;
		});
		natives.createNative("WaitGetEnemyBase", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		natives.createNative("GetEnemyBase", (arguments, globalScope, triggerScope) -> unit(ai.getEnemyBase()));
		natives.createNative("GetEnemyExpansion",
				(arguments, globalScope, triggerScope) -> unit(ai.getEnemyExpansion()));
		natives.createNative("GetMegaTarget", (arguments, globalScope, triggerScope) -> unit(ai.getMegaTarget()));
		natives.createNative("GetEnemyPower",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getEnemyPower()));
		natives.createNative("GetBuilding",
				(arguments, globalScope, triggerScope) -> unit(ai.getBuilding(object(arguments, 0))));
		natives.createNative("IsTowered",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isTowered(object(arguments, 0))));
		natives.createNative("TownThreatened",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.of(ai.isTownThreatened()));
		natives.createNative("SetAllianceTarget", (arguments, globalScope, triggerScope) -> {
			ai.setAllianceTarget(object(arguments, 0));
			return null;
		});
		natives.createNative("GetAllianceTarget",
				(arguments, globalScope, triggerScope) -> unit(getAllianceTarget()));
		natives.createNative("UnitAlive", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return BooleanJassValue.of((unit != null) && !unit.isDead());
		});
		natives.createNative("UnitInvis", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return BooleanJassValue.of((unit != null) && (unit.getInvisLevels() > 0));
		});

		// Commands from the map script
		natives.createNative("CommandsWaiting",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getCommandsWaiting()));
		natives.createNative("GetLastCommand",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getLastCommand()));
		natives.createNative("GetLastData",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(ai.getLastData()));
		natives.createNative("PopLastCommand", (arguments, globalScope, triggerScope) -> {
			ai.popLastCommand();
			return null;
		});
	}

	private interface BooleanSetter {
		void set(boolean value);
	}

	private static JassFunction setter(final BooleanSetter setter) {
		return (arguments, globalScope, triggerScope) -> {
			setter.set(bool(arguments, 0));
			return null;
		};
	}

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	private static float real(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static boolean bool(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(BooleanJassValueVisitor.getInstance());
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private JassValue unit(final CUnit unit) {
		if (unit == null) {
			return this.unitType.getNullValue();
		}
		return new HandleJassValue(this.unitType, unit);
	}

	/**
	 * Computer allies share one target: ours, else the first one an allied computer player set.
	 */
	private CUnit getAllianceTarget() {
		final CUnit own = this.controller.getOwnAllianceTarget();
		if (own != null) {
			return own;
		}
		final CPlayer player = this.simulation.getPlayer(this.playerIndex);
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CPlayer other = this.simulation.getPlayer(i);
			if ((i != this.playerIndex) && (other.getAiScript() != null)
					&& player.hasAlliance(i, CAllianceType.PASSIVE) && other.hasAlliance(this.playerIndex,
							CAllianceType.PASSIVE)) {
				final CUnit target = other.getAiScript().controller.getOwnAllianceTarget();
				if (target != null) {
					return target;
				}
			}
		}
		return null;
	}

	public JassProgram getJassProgram() {
		return this.jassProgram;
	}

	public void setPaused(final boolean paused) {
		this.paused = paused;
	}

	public boolean isPaused() {
		return this.paused;
	}

	public void command(final int command, final int data) {
		this.controller.command(command, data);
	}

	public void update() {
		if (this.paused) {
			return;
		}
		try {
			this.jassProgram.getGlobalScope().runThreads();
			this.controller.update();
		}
		catch (final Exception exception) {
			// One broken native must not end the game for every player; the AI keeps running on its other threads.
			if (this.errors++ < MAX_REPORTED_ERRORS) {
				exception.printStackTrace();
				JassLog.report(exception);
			}
		}
	}

}
