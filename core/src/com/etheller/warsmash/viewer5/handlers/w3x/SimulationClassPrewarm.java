package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.IOException;
import java.util.Arrays;

import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.MapLoader;

/**
 * Loads and links the classes the simulation first needs when a script makes its first units and the first orders
 * run, while the loading screen is still up. A class is read from the application's files and verified when it is
 * first used, which in the first step of a game made one frame wait for a few hundred of them.
 */
final class SimulationClassPrewarm {
	private static final String SIMULATION = "com.etheller.warsmash.viewer5.handlers.w3x.simulation.";

	private SimulationClassPrewarm() {
	}

	/** Names inside the simulation's package, or complete when they start with "com.". */
	static String[] names() {
		return new String[] {
				"campaign.CImage", "com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat",
				"sound.AbstractCSound", "sound.CSoundFromLabel", "sound.CSoundParameters",
				"com.etheller.warsmash.parsers.jass.WorldNatives$1", "sound.CSoundFilename",
				"com.etheller.warsmash.viewer5.handlers.w3x.SharedFileSounds$Entry",
				"com.etheller.warsmash.parsers.jass.triggers.CRect",
				"com.etheller.warsmash.viewer5.handlers.w3x.camera.CameraSetup$1", "CSimulation$1",
				"com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer$CreatedDestructable",
				"com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid$PathingType",
				"com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem",
				"com.etheller.warsmash.viewer5.handlers.w3x.SplatModel",
				"com.etheller.warsmash.viewer5.handlers.w3x.SplatModel$SplatMover",
				"com.etheller.warsmash.viewer5.handlers.w3x.SplatModel$Batch", "unit.StateModBuff",
				"unit.NonStackingStatBuffs", "CUnitDeathState", "CUnitAggroState", "CUnitProductionState",
				"CUnit$QueueItemType", "CUnitVisibilityState", "CUnitAcquisitionState", "CUnitOrderState",
				"CUnitRescueState", "CUnitStatsState", "CUnitDefenseState", "CUnitIllusionState",
				"CUnitMovementState", "CUnitInvulnerabilityState", "CUnitPlacementState", "CUnitBlockedState",
				"CUnitFoodState", "CUnitAutoState", "CUnitStateListener$CUnitStateNotifier",
				"behaviors.CBehaviorStop", "combat.attacks.listeners.CUnitDefaultAccuracyCheckListener",
				"players.vision.CUnitAttackVisionFogModifier", "CUnitStats", "unit.CUnitStateEvent",
				"CUnitStats$1", "combat.attacks.listeners.CUnitDefaultLifestealListener",
				"combat.attacks.listeners.CUnitDefaultThornsListener", "combat.CSpellDamageFlags",
				"com.etheller.warsmash.viewer5.handlers.mdx.EventObjectUbr",
				"com.etheller.warsmash.viewer5.handlers.mdx.EventObjectSpn",
				"com.etheller.warsmash.viewer5.handlers.w3x.rendersim.OrientationInterpolation",
				"abilities.GetAbilityByRawcodeVisitor", "abilities.generic.AbstractCAbilityDoNothing",
				"abilities.CAbilityGenericDoNothing", "CUnitPlacement", "region.CRegionEnumFunction",
				"CUnitPlacement$RegionCheckerImpl", "CUnitOrders", "CUnitAcquisition",
				"CUnitAcquisition$AutoAttackTargetFinderEnum", "CUnitAcquisition$AutocastTargetFinderEnum",
				"util.BooleanAbilityTargetCheckReceiver", "CUnitVisibility", "CUnitVisionStamp", "CUnitUpdate",
				"abilities.hero.CAbilityHero", "CUnitProduction", "CComputerWorkers",
				"abilities.types.CAbilityType$LevelDataFactory", "abilities.generic.GenericNoIconAbility",
				"abilities.generic.AbstractGenericNoIconAbility",
				"abilities.generic.AbstractCAbilityPassiveNoOrders", "abilities.combat.CAbilityInvulnerable",
				"abilitybuilder.types.impl.CAbilityTypeAbilityTemplateBuilder$1",
				"abilities.generic.GenericSingleIconPassiveAbility",
				"abilities.generic.AbilityGenericSingleIconPassiveAbility",
				"abilitybuilder.ability.template.AbstractCAbilityAbilityBuilderAuraTemplate",
				"abilitybuilder.ability.template.CAbilityAbilityBuilderStatAuraTemplate",
				"abilitybuilder.ability.template.AuraCheckClock",
				"abilitybuilder.parser.template.StatBuffType$1",
				"abilitybuilder.ability.template.CAbilityAbilityBuilderStatAuraTemplate$1",
				"abilitybuilder.buff.ABGenericAuraBuff",
				"com.etheller.warsmash.viewer5.handlers.w3x.MapViewerEffects$1",
				"util.SimulationRenderComponentModel$1",
				"com.etheller.interpreter.ast.scope.trigger.Trigger$ActionsThread",
				"com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent$1",
				"com.etheller.warsmash.parsers.jass.triggers.TriggerEventHandle",
				"com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor",
				"com.etheller.interpreter.ast.scope.trigger.Trigger$JassThreadActionFunc",
				"com.etheller.warsmash.parsers.jass.triggers.TriggerAction",
				"com.etheller.warsmash.viewer5.handlers.mdx.RibbonEmitter",
				"com.etheller.warsmash.viewer5.handlers.mdx.Ribbon", "abilities.upgrade.CAbilityUpgrade",
				"abilities.blight.CAbilityBlight", "abilitybuilder.types.impl.CAbilityTypeAbilityBuilder$1",
				"abilitybuilder.ability.AbilityBuilderAbility",
				"abilitybuilder.ability.AbilityBuilderPassiveAbility",
				"abilitybuilder.ability.CAbilityAbilityBuilderPassive", "abilities.CAbilityDisableType",
				"abilities.queue.CAbilityReviveHero",
				"com.etheller.warsmash.viewer5.handlers.mdx.EventObjectSpl", "abilities.CAbilityMove",
				"behaviors.CBehaviorMove", "behaviors.CBehaviorMove$TargetVisitingResetter",
				"behaviors.CRangedBehavior", "behaviors.CAbstractRangedBehavior", "behaviors.CBehaviorFollow",
				"behaviors.CBehaviorPatrol", "behaviors.CBehaviorHoldPosition",
				"behaviors.CBehaviorMoveIntoRangeFor$PairAbilityLocator",
				"behaviors.CBehaviorMoveIntoRangeFor", "behaviors.CBehaviorBoardTransport",
				"abilities.CAbilityRangedView", "abilities.CAbilityRanged",
				"behaviors.cargohold.CBehaviorBoard", "abilities.CAbilityAttack",
				"util.ExternStringMsgTargetCheckReceiver", "behaviors.CBehaviorAttack", "timers.CExactExpiry",
				"timers.CExactExpiry$1", "abilities.targeting.AbilityTargetStillAliveAndTargetableVisitor",
				"behaviors.CBehaviorAttackMove", "CUnitAggro", "CUnit$CreepCamp", "behaviors.CBehaviorStun",
				"combat.attacks.listeners.CUnitDefaultSleepListener",
				"com.etheller.warsmash.viewer5.handlers.w3x.rendersim.SpellEffectModel",
				"com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget$QueuedAnimation",
				"abilities.ScriptFieldOverlay", "behaviors.skills.CBehaviorTargetSpellBase",
				"abilitybuilder.listener.ABAttackEvasionListener", "abilities.neutral.CAbilityWander",
				"abilities.autocast.CAutocastAbility", "abilitybuilder.ability.AbilityBuilderActiveAbility",
				"abilitybuilder.ability.CAbilityAbilityBuilderGenericActive",
				"abilitybuilder.ability.CAbilityAbilityBuilderActiveNoTarget",
				"abilitybuilder.behavior.ABBehavior", "orders.OrderIdUtils", "orders.OrderIds",
				"abilities.inventory.CAbilityInventory", "abilities.hero.CAbilityHero$HeroStatValue",
				"abilities.hero.CAbilityHero$1", "behaviors.inventory.CBehaviorGetItem",
				"behaviors.inventory.CBehaviorDropItem", "behaviors.inventory.CBehaviorGiveItemToHero",
				"behaviors.build.AbstractCBehaviorBuild", "behaviors.build.CBehaviorUndeadBuild",
				"abilities.harvest.CAbilityAcolyteHarvest", "behaviors.harvest.CBehaviorAcolyteHarvest",
				"abilities.build.AbstractCAbilityRepair", "abilities.build.CAbilityRepair",
				"behaviors.build.AbstractCBehaviorRepair", "behaviors.build.CBehaviorRepair",
				"abilities.generic.CPairingAbility",
				"abilitybuilder.ability.CAbilityAbilityBuilderActivePairing",
				"abilities.harvest.CAbilityHarvest", "abilities.harvest.CAbilityWispHarvest",
				"abilitybuilder.ability.CAbilityAbilityBuilderActiveUnitTarget",
				"abilitybuilder.core.ABLocalStoreKeys",
				"abilitybuilder.ability.CAbilityAbilityBuilderActiveAutoTarget",
				"abilitybuilder.behavior.CBehaviorAbilityBuilderBase",
				"abilitybuilder.behavior.ABAbilityTargetStillTargetableVisitor",
				"abilitybuilder.listener.ABAttackPreDamageListener",
				"abilitybuilder.listener.ABDamageTakenModificationListener",
				"abilitybuilder.listener.ABAttackProjReactionListener", "CUnitTypes",
				"unit.CUnitAppliedUpgrade", "abilitybuilder.ability.CAbilityAbilityBuilderActivePointTarget",
				"upgrade.CUpgradeEffect$Util",
				"com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression",
				"com.etheller.warsmash.parsers.jass.triggers.BoolExprCondition",
				"com.etheller.interpreter.ast.value.visitor.SuperTypeVisitor",
				"com.etheller.warsmash.parsers.jass.triggers.CForce",
				"com.etheller.interpreter.ast.value.visitor.ArrayJassValueVisitor",
				"com.etheller.interpreter.ast.value.visitor.ArithmeticLeftHandHandleJassValueVisitor",
				"com.etheller.interpreter.ast.value.visitor.ArithmeticLeftHandBooleanJassValueVisitor",
				"com.etheller.interpreter.ast.scope.variableevent.VariableEvent",
				"CSimulation$TimeOfDayVariableEvent",
				"com.etheller.interpreter.ast.value.visitor.NotJassValueVisitor",
				"com.etheller.warsmash.parsers.jass.triggers.TriggerCondition", "region.CRegion",
				"region.CRegion$1", "region.CRegion$2", "region.CRegionTriggerEnter",
				"timers.CTimerNativeEvent", "region.CRegionTriggerLeave", "campaign.CSaveStorage$Writer",
				"com.etheller.interpreter.ast.scope.variableevent.VariableEvent$1",
				"sound.CStackedSounds$Stack", "sound.CStackedSounds$Area",
				"com.etheller.warsmash.util.JassRandom", "timers.CTimerSleepAction",
				"util.BooleanAbilityActivationReceiver",
				"abilitybuilder.behavior.CBehaviorAbilityBuilderNoTarget", "CUnitEvents",
				"com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope$UnitEventScopeBuilder",
				"campaign.CGameCache$Data", "com.etheller.warsmash.parsers.jass.triggers.LocationJass",
				"util.PointAbilityTargetCheckReceiver", "orders.COrderTargetPoint",
				"util.ExternStringMsgAbilityActivationReceiver", "behaviors.skills.CBehaviorNoTargetSpellBase",
				"players.CPlayerSelection", "players.CPlayerSelection$Events",
				"com.etheller.warsmash.viewer5.handlers.w3x.CasterSoundLoops$Caster",
				"CUnitAnimationListener$1", "CUnitOwnership",
				"abilities.skills.neutral.creeps.CCreepCastAbility", "behaviors.BehaviorTargetUnitVisitor",
				"abilitybuilder.ability.template.AbstractCAbilityAbilityBuilderAuraTemplate$1",
				"combat.CAreaTargetVisibility", "CUnitVisibility$1",
				"abilities.skills.neutral.creeps.CCreepCastAbility$Rule", "CUnitUpdate$1",
				"CUnit$StateListenerUpdateType", "abilities.targeting.AbilityTargetVisitor$1",
				"abilities.targeting.AbilityTargetVisitor$2", "abilities.targeting.AbilityTargetVisitor$3",
				"abilities.targeting.AbilityTargetVisitor$4", "pathing.CPathPoint", "CUnitAcquisition$1",
				"unit.NonStackingFx", "com.etheller.warsmash.viewer5.handlers.mdx.ShadowMesh$Key",
				"com.etheller.warsmash.viewer5.handlers.mdx.LightInstance$1",
				"pathing.CPathfindingProcessor$SharedGraph", "pathing.CPathfindingProcessor$Reachability",
				"pathing.CPathfindingProcessor$PathfindingJob", "pathing.CPathfindingProcessor$SearchGrid",
				"pathing.CPathfindingProcessor$GridMapping", "pathing.CPathfindingProcessor$GridMapping$1",
				"pathing.CPathfindingProcessor$GridMapping$2", "pathing.CPathfindingProcessor$1",
				"pathing.CPathfindingProcessor$Direction", "abilities.mine.CAbilityGoldMinable",
				"abilities.mine.CAbilityOverlayedMine", "SimulationTypeCodes",
				"behaviors.jass.CAbstractRangedBehaviorJass",
				"behaviors.cargohold.CBehaviorDrop", "abilitybuilder.behavior.CBehaviorFinishTransformation",
				"behaviors.CBehaviorAttackListener", "behaviors.harvest.CBehaviorHarvest",
				"behaviors.build.CBehaviorHumanBuild", "behaviors.build.CBehaviorHumanRepair",
				"behaviors.jass.CBehaviorJass", "behaviors.cargohold.CBehaviorLoad",
				"behaviors.CBehaviorMoveThroughWayGate", "behaviors.build.CBehaviorOrcBuild",
				"behaviors.build.CBehaviorNightElfBuild", "behaviors.harvest.CBehaviorReturnResources",
				"behaviors.root.CBehaviorRoot", "abilitybuilder.behavior.CBehaviorSendOrder",
				"behaviors.root.CBehaviorUproot", "behaviors.harvest.CBehaviorWispHarvest",
				"behaviors.jass.CRangedBehaviorJass", "combat.projectile.CAttackProjectileInstant",
				"timers.CTimerJassStruct", "timers.CTimerDamagePoint", "timers.CTimerFacing",
				"timers.CTimerFlyHeight", "CUnit$CTimerUnitFade",
				"abilitybuilder.timer.AltitudeAdjustmentTimer",
				"abilitybuilder.timer.DelayInstantTransformationTimer", "abilitybuilder.timer.DelayTimerTimer",
				"abilitybuilder.timer.ManaDepletedCheckTimer",
				"abilitybuilder.timer.TransformationMorphAnimationTimer",
		};
	}

	/** Queues the classes onto the loader, as many in each of its steps as take a frame's share of time. */
	static void queue(final MapLoader loader) throws IOException {
		loader.addSlices(Arrays.asList(names()), SimulationClassPrewarm::load, () -> {
		});
	}

	static String qualified(final String name) {
		return name.startsWith("com.") ? name : SIMULATION + name;
	}

	private static void load(final String name) {
		try {
			Class.forName(qualified(name), true, SimulationClassPrewarm.class.getClassLoader());
		}
		catch (final ClassNotFoundException | LinkageError e) {
			// Loaded when the game first needs it, and fails there as it would have anyway.
		}
	}
}
