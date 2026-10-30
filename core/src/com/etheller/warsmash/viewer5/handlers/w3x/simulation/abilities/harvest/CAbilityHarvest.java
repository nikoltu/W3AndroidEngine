package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest;

import java.util.EnumSet;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMinable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorReturnResources;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CWeaponType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackNormal;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;

public class CAbilityHarvest extends AbstractGenericSingleIconActiveAbility {
	private int damageToTree;
	private int goldCapacity;
	private int lumberCapacity;
	private float castRange;
	private float duration;
	private CBehaviorHarvest behaviorHarvest;
	private CBehaviorReturnResources behaviorReturnResources;
	private int carriedResourceAmount;
	private ResourceType carriedResourceType;
	private CUnitAttack treeAttack;
	private CWidget lastHarvestTarget;
	private CBehaviorAttack behaviorTreeAttack;
	// Measured in 1.29: at every mine entry a gold worker's speed is set so that its trip from the mine and back
	// takes the target cycle minus the time inside (the mean of its last two trips), between half and 1.2 times its
	// own speed. A cycle twice the target or any other order clears it.
	private static final float MINE_CYCLE_TARGET = 5.0f;
	private static final float MINE_SPEED_MIN = 0.5f;
	private static final float MINE_SPEED_MAX = 1.2f;
	private static final float MINE_SPEED_IDLE_RESET = 0.5f;
	private int mineExitTick = -1;
	private float lastTripSpeed;
	private NonStackingStatBuff mineSpeedBuff;
	private int lastTick;
	private int idleSinceTick = -1;

	public CAbilityHarvest(final int handleId, final War3ID code, final War3ID alias, final int damageToTree,
			final int goldCapacity, final int lumberCapacity, final float castRange, final float duration) {
		super(handleId, code, alias);
		this.damageToTree = damageToTree;
		this.goldCapacity = goldCapacity;
		this.lumberCapacity = lumberCapacity;
		this.castRange = castRange;
		this.duration = duration;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {

		this.behaviorTreeAttack = new CBehaviorAttack(unit);
		this.behaviorHarvest = new CBehaviorHarvest(unit, this);
		this.behaviorReturnResources = new CBehaviorReturnResources(unit, this);

		final List<CUnitAttack> unitAttacks = unit.getUnitSpecificAttacks();
		CUnitAttack bestFitTreeAttack = null;
		for (final CUnitAttack attack : unitAttacks) {
			if (attack.getTargetsAllowed().contains(CTargetType.TREE)) {
				bestFitTreeAttack = attack;
			}
		}
		this.treeAttack = new CUnitAttackNormal(
				bestFitTreeAttack == null ? 0.433f : bestFitTreeAttack.getAnimationBackswingPoint(),
				bestFitTreeAttack == null ? 0.433f : bestFitTreeAttack.getAnimationDamagePoint(), CAttackType.NORMAL,
				this.duration, this.damageToTree, 0, 0, 0, (int) this.castRange,
				bestFitTreeAttack == null ? 250 : bestFitTreeAttack.getRangeMotionBuffer(),
				bestFitTreeAttack == null ? false : bestFitTreeAttack.isShowUI(),
				bestFitTreeAttack == null ? EnumSet.of(CTargetType.TREE) : bestFitTreeAttack.getTargetsAllowed(),
				bestFitTreeAttack == null ? "AxeMediumChop" : bestFitTreeAttack.getWeaponSound(),
				bestFitTreeAttack == null ? CWeaponType.NORMAL : bestFitTreeAttack.getWeaponType(), SequenceUtils.WORK);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		final int tick = game.getGameTurnTick();
		final int skipped = tick - this.lastTick;
		this.lastTick = tick;
		if ((this.mineExitTick < 0) && (this.mineSpeedBuff == null)) {
			return;
		}
		final int resetTicks = (int) (MINE_SPEED_IDLE_RESET / WarsmashConstants.SIMULATION_STEP_TIME);
		if (skipped > resetTicks) {
			resetMineSpeed(game, unit);
			return;
		}
		final CBehavior behavior = unit.getCurrentBehavior();
		final boolean harvesting = (behavior != null)
				&& ((behavior == this.behaviorHarvest) || (behavior == this.behaviorReturnResources)
						|| (behavior.getHighlightOrderId() == OrderIds.harvest)
						|| (behavior.getHighlightOrderId() == OrderIds.returnresources));
		if (harvesting) {
			this.idleSinceTick = -1;
		}
		else if (this.idleSinceTick < 0) {
			this.idleSinceTick = tick;
		}
		else if ((tick - this.idleSinceTick) > resetTicks) {
			resetMineSpeed(game, unit);
		}
	}

	public void onMineExit(final CSimulation game) {
		this.mineExitTick = game.getGameTurnTick();
	}

	public void onMineEntry(final CSimulation game, final CUnit unit, final float miningDuration) {
		if (this.mineExitTick < 0) {
			return;
		}
		final float tripTime = (game.getGameTurnTick() - this.mineExitTick) * WarsmashConstants.SIMULATION_STEP_TIME;
		this.mineExitTick = -1;
		final float tripTarget = MINE_CYCLE_TARGET - miningDuration;
		if (((tripTime + miningDuration) > (2 * MINE_CYCLE_TARGET)) || (tripTarget <= 0)) {
			resetMineSpeed(game, unit);
			return;
		}
		final float tripSpeed = (unit.getSpeed() * tripTime) / tripTarget;
		final float wantedSpeed = this.lastTripSpeed > 0 ? (this.lastTripSpeed + tripSpeed) / 2 : tripSpeed;
		this.lastTripSpeed = tripSpeed;
		removeMineSpeedBuff(game, unit);
		final int ownSpeed = unit.getSpeed();
		if (ownSpeed <= 0) {
			return;
		}
		final float factor = Math.max(MINE_SPEED_MIN, Math.min(MINE_SPEED_MAX, wantedSpeed / ownSpeed));
		this.mineSpeedBuff = new NonStackingStatBuff(NonStackingStatBuffType.MVSPDPCT, "harvestMineSpeed",
				factor - 1);
		unit.addNonStackingStatBuff(game, this.mineSpeedBuff);
	}

	public void resetMineSpeed(final CSimulation game, final CUnit unit) {
		this.mineExitTick = -1;
		this.lastTripSpeed = 0;
		this.idleSinceTick = -1;
		removeMineSpeedBuff(game, unit);
	}

	private void removeMineSpeedBuff(final CSimulation game, final CUnit unit) {
		if (this.mineSpeedBuff != null) {
			unit.removeNonStackingStatBuff(game, this.mineSpeedBuff);
			this.mineSpeedBuff = null;
		}
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return this.behaviorHarvest.reset(game, target);
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		if (isToggleOn() && (orderId == OrderIds.returnresources)) {
			return this.behaviorReturnResources.reset(game);
		}
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	public int getBaseOrderId() {
		return isToggleOn() ? OrderIds.returnresources : OrderIds.harvest;
	}

	@Override
	public boolean isToggleOn() {
		return this.carriedResourceAmount > 0;
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.useOk();
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (target instanceof CUnit) {
			if (this.goldCapacity <= 0) {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_TREE);
				return;
			}
			final CUnit targetUnit = (CUnit) target;
			for (final CAbility ability : targetUnit.getAbilities()) {
				if (ability instanceof CAbilityGoldMinable) {
					receiver.targetOk(target);
					return;
				}
				else if ((this.carriedResourceType != null) && (ability instanceof CAbilityReturnResources)) {
					final CAbilityReturnResources abilityReturn = (CAbilityReturnResources) ability;
					if (abilityReturn.accepts(this.carriedResourceType)) {
						receiver.targetOk(target);
						return;
					}
				}
			}
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_RESOURCES);
		}
		else if (target instanceof CDestructable) {
			if (this.lumberCapacity <= 0) {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_GOLD_MINE);
				return;
			}
			if (target.canBeTargetedBy(game, unit, this.treeAttack.getTargetsAllowed(), receiver)) {
				receiver.targetOk(target);
			}
			// else receiver called by "canBeTargetedBy"
		}
		else {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_RESOURCES);
		}
	}

	@Override
	protected void innerCheckCanSmartTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		innerCheckCanTarget(game, unit, orderId, target, receiver);
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityPointTarget target, final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	protected void innerCheckCanSmartTarget(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityPointTarget target, final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	protected void innerCheckCanTargetNoTarget(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityTargetCheckReceiver<Void> receiver) {
		if ((orderId == OrderIds.returnresources) && isToggleOn()) {
			receiver.targetOk(null);
		}
		else {
			receiver.orderIdNotAccepted();
		}
	}

	public int getDamageToTree() {
		return this.damageToTree;
	}

	public int getGoldCapacity() {
		return this.goldCapacity;
	}

	public int getLumberCapacity() {
		return this.lumberCapacity;
	}

	public int getCarriedResourceAmount() {
		return this.carriedResourceAmount;
	}

	public ResourceType getCarriedResourceType() {
		return this.carriedResourceType;
	}

	public void setCarriedResources(final ResourceType carriedResourceType, final int carriedResourceAmount) {
		this.carriedResourceType = carriedResourceType;
		this.carriedResourceAmount = carriedResourceAmount;
	}

	public CBehaviorHarvest getBehaviorHarvest() {
		return this.behaviorHarvest;
	}

	public CBehaviorReturnResources getBehaviorReturnResources() {
		return this.behaviorReturnResources;
	}

	public CUnitAttack getTreeAttack() {
		return this.treeAttack;
	}

	public void setLastHarvestTarget(final CWidget lastHarvestTarget) {
		this.lastHarvestTarget = lastHarvestTarget;
	}

	public CWidget getLastHarvestTarget() {
		return this.lastHarvestTarget;
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	public CBehaviorAttack getBehaviorTreeAttack() {
		return this.behaviorTreeAttack;
	}

	public void setDamageToTree(final int damageToTree) {
		this.damageToTree = damageToTree;
	}

	public void setGoldCapacity(final int goldCapacity) {
		this.goldCapacity = goldCapacity;
	}

	public void setLumberCapacity(final int lumberCapacity) {
		this.lumberCapacity = lumberCapacity;
	}

	public void setCastRange(final float castRange) {
		this.castRange = castRange;
	}

	public void setDuration(final float duration) {
		this.duration = duration;
	}

	@Override
	public boolean isPhysical() {
		return true;
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean isUniversal() {
		return false;
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.CORE;
	}
}
