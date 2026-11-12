package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.ABBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.CBehaviorAbilityBuilderBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.CBehaviorAbilityBuilderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.CBehaviorFinishTransformation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.CBehaviorSendOrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.effects.ABGroupEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.effects.ABTerrainBowlEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.effects.ABTerrainEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.effects.ABTerrainRippleEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.effects.ABTerrainWaveEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer.ABTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer.AltitudeAdjustmentTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer.DelayInstantTransformationTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer.DelayTimerTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer.ManaDepletedCheckTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer.TransformationMorphAnimationTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorBoardTransport;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorFollow;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMoveIntoRangeFor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMoveThroughWayGate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorPatrol;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStun;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.AbstractCBehaviorBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.AbstractCBehaviorRepair;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorHumanBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorHumanRepair;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorNightElfBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorOrcBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorRepair;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorUndeadBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.cargohold.CBehaviorBoard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.cargohold.CBehaviorDrop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.cargohold.CBehaviorLoad;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorAcolyteHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorReturnResources;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory.CBehaviorDropItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory.CBehaviorGetItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory.CBehaviorGiveItemToHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.jass.CAbstractRangedBehaviorJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.jass.CBehaviorJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.jass.CRangedBehaviorJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.root.CBehaviorRoot;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.root.CBehaviorUproot;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.undead.dreadlord.CBehaviorCarrionSwarmDummy;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.channel.CBehaviorChannel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectileInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectileMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CCollisionProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CJassProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CPseudoProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerDamagePoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerFacing;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerFlyHeight;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerJassBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerJassStruct;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerNativeEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerSleepAction;

/**
 * The kinds of simulation objects the checksum tells apart, by codes written here: a release build renames classes,
 * and a lambda's class name changes from run to run, so no class name may reach the checksum. An object of a class
 * without a code of its own (an anonymous or private class) counts as its nearest superclass with one, else as its
 * family's root.
 */
final class SimulationTypeCodes {
	private static final Map<Class<?>, String> CODES = new HashMap<>();

	static {
		add(CBehavior.class, "Behavior");
		add(ABBehavior.class, "ABBehavior");
		add(CAbstractRangedBehavior.class, "AbstractRangedBehavior");
		add(AbstractCBehaviorBuild.class, "AbstractBehaviorBuild");
		add(AbstractCBehaviorRepair.class, "AbstractBehaviorRepair");
		add(CRangedBehavior.class, "RangedBehavior");
		add(CAbstractRangedBehaviorJass.class, "AbstractRangedBehaviorJass");
		add(CBehaviorAbilityBuilderBase.class, "BehaviorAbilityBuilderBase");
		add(CBehaviorAbilityBuilderNoTarget.class, "BehaviorAbilityBuilderNoTarget");
		add(CBehaviorAcolyteHarvest.class, "BehaviorAcolyteHarvest");
		add(CBehaviorAttack.class, "BehaviorAttack");
		add(CBehaviorAttackMove.class, "BehaviorAttackMove");
		add(CBehaviorBoard.class, "BehaviorBoard");
		add(CBehaviorBoardTransport.class, "BehaviorBoardTransport");
		add(CBehaviorCarrionSwarmDummy.class, "BehaviorCarrionSwarmDummy");
		add(CBehaviorChannel.class, "BehaviorChannelTest");
		add(CBehaviorDrop.class, "BehaviorDrop");
		add(CBehaviorDropItem.class, "BehaviorDropItem");
		add(CBehaviorFinishTransformation.class, "BehaviorFinishTransformation");
		add(CBehaviorFollow.class, "BehaviorFollow");
		add(CBehaviorGetItem.class, "BehaviorGetItem");
		add(CBehaviorGiveItemToHero.class, "BehaviorGiveItemToHero");
		add(CBehaviorHarvest.class, "BehaviorHarvest");
		add(CBehaviorHoldPosition.class, "BehaviorHoldPosition");
		add(CBehaviorHumanBuild.class, "BehaviorHumanBuild");
		add(CBehaviorHumanRepair.class, "BehaviorHumanRepair");
		add(CBehaviorJass.class, "BehaviorJass");
		add(CBehaviorLoad.class, "BehaviorLoad");
		add(CBehaviorMove.class, "BehaviorMove");
		add(CBehaviorMoveIntoRangeFor.class, "BehaviorMoveIntoRangeFor");
		add(CBehaviorMoveThroughWayGate.class, "BehaviorMoveThroughWayGate");
		add(CBehaviorNightElfBuild.class, "BehaviorNightElfBuild");
		add(CBehaviorNoTargetSpellBase.class, "BehaviorNoTargetSpellBase");
		add(CBehaviorOrcBuild.class, "BehaviorOrcBuild");
		add(CBehaviorPatrol.class, "BehaviorPatrol");
		add(CBehaviorRepair.class, "BehaviorRepair");
		add(CBehaviorReturnResources.class, "BehaviorReturnResources");
		add(CBehaviorRoot.class, "BehaviorRoot");
		add(CBehaviorSendOrder.class, "BehaviorSendOrder");
		add(CBehaviorStop.class, "BehaviorStop");
		add(CBehaviorStun.class, "BehaviorStun");
		add(CBehaviorTargetSpellBase.class, "BehaviorTargetSpellBase");
		add(CBehaviorUndeadBuild.class, "BehaviorUndeadBuild");
		add(CBehaviorUproot.class, "BehaviorUproot");
		add(CBehaviorWispHarvest.class, "BehaviorWispHarvest");
		add(CRangedBehaviorJass.class, "RangedBehaviorJass");

		add(CEffect.class, "Effect");
		add(CProjectile.class, "Projectile");
		add(CAttackProjectile.class, "AttackProjectile");
		add(CAbilityProjectile.class, "AbilityProjectile");
		add(CAttackProjectileInstant.class, "AttackProjectileInstant");
		add(CAttackProjectileMissile.class, "AttackProjectileMissile");
		add(CCollisionProjectile.class, "CollisionProjectile");
		add(CJassProjectile.class, "JassProjectile");
		add(CPseudoProjectile.class, "PseudoProjectile");

		add(CTimer.class, "Timer");
		add(CTimerJassBase.class, "TimerJassBase");
		add(CTimerJass.class, "TimerJass");
		add(CTimerJassStruct.class, "TimerJassStruct");
		add(CTimerNativeEvent.class, "TimerNativeEvent");
		add(CTimerSleepAction.class, "TimerSleepAction");
		add(CTimerDamagePoint.class, "TimerDamagePoint");
		add(CTimerFacing.class, "TimerFacing");
		add(CTimerFlyHeight.class, "TimerFlyHeight");
		add(CUnit.CTimerUnitFade.class, "TimerUnitFade");
		add(CSimulation.FogUpdateTimer.class, "FogUpdateTimer");
		add(CTerrainDeformations.UpdateTimer.class, "TerrainDeformationTimer");
		add(ABTimer.class, "ABTimer");
		add(AltitudeAdjustmentTimer.class, "AltitudeAdjustmentTimer");
		add(DelayInstantTransformationTimer.class, "DelayInstantTransformationTimer");
		add(DelayTimerTimer.class, "DelayTimerTimer");
		add(ManaDepletedCheckTimer.class, "ManaDepletedCheckTimer");
		add(TransformationMorphAnimationTimer.class, "TransformationMorphAnimationTimer");
		add(ABGroupEffect.class, "ABGroupEffect");
		add(ABTerrainEffect.class, "ABTerrainEffect");
		add(ABTerrainBowlEffect.class, "ABTerrainBowlEffect");
		add(ABTerrainRippleEffect.class, "ABTerrainRippleEffect");
		add(ABTerrainWaveEffect.class, "ABTerrainWaveEffect");
	}

	private SimulationTypeCodes() {
	}

	private static void add(final Class<?> type, final String code) {
		if (CODES.put(type, code) != null) {
			throw new IllegalStateException("Two codes for one type: " + code);
		}
	}

	/** Whether the type has a code of its own. */
	static boolean has(final Class<?> type) {
		return CODES.containsKey(type);
	}

	/**
	 * The code of the object's type: its own, its nearest superclass's, else that of the first family root it
	 * implements, else the fallback.
	 */
	static String of(final Object object, final String fallback) {
		if (object == null) {
			return "-";
		}
		for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
			final String code = CODES.get(type);
			if (code != null) {
				return code;
			}
		}
		if (object instanceof CBehavior) {
			return CODES.get(CBehavior.class);
		}
		if (object instanceof CEffect) {
			return CODES.get(CEffect.class);
		}
		return fallback;
	}
}
