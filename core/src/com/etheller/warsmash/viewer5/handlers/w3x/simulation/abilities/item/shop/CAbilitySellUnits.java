package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CShopEvents;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitTypeRequirement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Units sold by a neutral building (Sellunits): tavern heroes, mercenaries and goblin units. The stock is shared by
 * all players; the unit is made at once next to the shop for the buyer, who needs a unit near the shop.
 */
public final class CAbilitySellUnits extends AbstractCAbility {
	private final Set<War3ID> unitsSold;
	private final Map<War3ID, SellStockState> unitToSellState = new LinkedHashMap<>();
	private final List<War3ID> scriptStockedUnits = new ArrayList<>();
	private int unitTypeSlots = -1;

	public CAbilitySellUnits(final int handleId, final List<War3ID> unitsSold) {
		super(handleId, War3ID.fromString("Asud"));
		this.unitsSold = new LinkedHashSet<>(unitsSold);
	}

	public Set<War3ID> getUnitsSold() {
		return this.unitsSold;
	}

	public int getScriptStockSlot(final War3ID unitTypeId) {
		return this.scriptStockedUnits.indexOf(unitTypeId);
	}

	public int getStock(final War3ID unitTypeId) {
		final SellStockState state = this.unitToSellState.get(unitTypeId);
		if (state != null) {
			return state.getStock();
		}
		return 0;
	}

	public static int getGoldCost(final CPlayer buyer, final CUnitType unitType) {
		if (unitType.isHero() && (buyer.getHeroTokens() > 0)) {
			return 0;
		}
		return unitType.getGoldCost();
	}

	public static int getLumberCost(final CPlayer buyer, final CUnitType unitType) {
		if (unitType.isHero() && (buyer.getHeroTokens() > 0)) {
			return 0;
		}
		return unitType.getLumberCost();
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		final War3ID unitTypeId = new War3ID(orderId);
		final SellStockState sellState = this.unitToSellState.get(unitTypeId);
		final CUnitType unitType = game.getUnitData().getUnitType(unitTypeId);
		if ((sellState == null) || (unitType == null)) {
			receiver.useOk();
			return;
		}
		final CPlayer buyer = game.getPlayer(playerIndex);
		final boolean allowedByMax = buyer.isTechtreeAllowedByMax(unitTypeId)
				&& (!unitType.isHero() || buyer.isHeroCountAllowedByMax(game));
		if (!allowedByMax) {
			receiver.techtreeMaximumReached();
			return;
		}
		boolean requirementsMet = true;
		for (final CUnitTypeRequirement requirement : unitType.getRequirements()) {
			if (buyer.getTechtreeUnlocked(requirement.getRequirement()) < requirement.getRequiredLevel()) {
				requirementsMet = false;
				receiver.missingRequirement(requirement.getRequirement(), requirement.getRequiredLevel());
			}
		}
		if (unitType.isHero()) {
			for (final CUnitTypeRequirement requirement : unitType
					.getRequirementsTier(buyer.getHeroCount(game, true))) {
				if (buyer.getTechtreeUnlocked(requirement.getRequirement()) < requirement.getRequiredLevel()) {
					requirementsMet = false;
					receiver.missingRequirement(requirement.getRequirement(), requirement.getRequiredLevel());
				}
			}
		}
		if (!requirementsMet) {
			return;
		}
		if (!CAbilityNeutralBuilding.hasPatron(unit, playerIndex)) {
			receiver.activationCheckFailed(CommandStringErrorKeys.A_VALID_PATRON_MUST_BE_NEARBY);
		}
		else if (sellState.getStock() <= 0) {
			receiver.noChargesRemaining();
			receiver.cooldownNotYetReady(sellState.getCooldownRemaining(game.getGameTurnTick()),
					sellState.getCooldownMax());
		}
		else if (buyer.getGold() < getGoldCost(buyer, unitType)) {
			receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_GOLD);
		}
		else if (buyer.getLumber() < getLumberCost(buyer, unitType)) {
			receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_LUMBER);
		}
		else if ((unitType.getFoodUsed() != 0)
				&& ((buyer.getFoodUsed() + unitType.getFoodUsed()) > buyer.getFoodCap())) {
			receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_FOOD);
		}
		else {
			receiver.useOk();
		}
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		if (this.unitsSold.contains(new War3ID(orderId))) {
			receiver.targetOk(null);
		}
		else {
			receiver.orderIdNotAccepted();
		}
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		return true;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		for (final War3ID unitTypeId : this.unitsSold) {
			final CUnitType unitType = game.getUnitData().getUnitType(unitTypeId);
			if (unitType != null) {
				this.unitToSellState.put(unitTypeId,
						SellStockState.fromData(game.getGameTurnTick(), unitType.getStockMax(),
								unitType.getStockReplenishInterval(), unitType.getStockStartDelay(),
								unitType.getStockInitial()));
			}
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		this.unitToSellState.clear();
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (!isDisabled()) {
			final int gameTurnTick = game.getGameTurnTick();
			for (final SellStockState sellState : this.unitToSellState.values()) {
				sellState.update(gameTurnTick);
			}
		}
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return null;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		final War3ID unitTypeId = new War3ID(orderId);
		final SellStockState sellState = this.unitToSellState.get(unitTypeId);
		final CUnitType unitType = game.getUnitData().getUnitType(unitTypeId);
		if ((sellState == null) || (unitType == null) || (sellState.getStock() <= 0)
				|| !CAbilityNeutralBuilding.hasPatron(caster, playerIndex)) {
			return null;
		}
		final CPlayer buyer = game.getPlayer(playerIndex);
		if (!buyer.isTechtreeAllowedByMax(unitTypeId) || (unitType.isHero() && !buyer.isHeroCountAllowedByMax(game))
				|| ((unitType.getFoodUsed() != 0)
						&& ((buyer.getFoodUsed() + unitType.getFoodUsed()) > buyer.getFoodCap()))) {
			return null;
		}
		if (unitType.isHero() && (buyer.getHeroTokens() > 0)) {
			buyer.setHeroTokens(buyer.getHeroTokens() - 1);
		}
		else if (!buyer.charge(unitType.getGoldCost(), unitType.getLumberCost())) {
			return null;
		}
		sellState.chargeStock(1);
		final CUnit soldUnit = game.createUnit(unitTypeId, playerIndex, caster.getX(), caster.getY(),
				game.getGameplayConstants().getBuildingAngle());
		if (soldUnit == null) {
			return null;
		}
		buyer.setUnitFoodUsed(soldUnit, unitType.getFoodUsed());
		buyer.setUnitFoodMade(soldUnit, unitType.getFoodMade());
		buyer.addTechtreeUnlocked(game, unitTypeId);
		buyer.scoreUnitGained(unitType);
		if (!unitType.isHero()) {
			buyer.addScore(CPlayerScore.MERCS_HIRED, 1);
		}
		soldUnit.nudgeAround(game, caster);
		game.unitTrainedEvent(caster, soldUnit);
		final CAbilityNeutralBuilding neutralBuildingData = caster.getNeutralBuildingData();
		final CUnit buyingUnit = neutralBuildingData == null ? null
				: neutralBuildingData.getSelectedPlayerUnit(playerIndex);
		CShopEvents.fireSellEvents(game, caster, soldUnit, buyingUnit);
		return null;
	}

	@Override
	public <T> T visit(final CAbilityVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}

	@Override
	public boolean isPhysical() {
		return false;
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

	public void setUnitTypeSlots(final int unitTypeSlots) {
		this.unitTypeSlots = unitTypeSlots;
	}

	public void addToStock(final CSimulation game, final War3ID unitTypeId, final int currentStock,
			final int stockMax) {
		final CUnitType unitType = game.getUnitData().getUnitType(unitTypeId);
		if (unitType == null) {
			return;
		}
		SellStockState state = this.unitToSellState.get(unitTypeId);
		if (state == null) {
			if ((this.unitTypeSlots >= 0) && (this.unitsSold.size() >= this.unitTypeSlots)) {
				return;
			}
			state = new SellStockState(stockMax,
					(int) (unitType.getStockReplenishInterval() / WarsmashConstants.SIMULATION_STEP_TIME),
					unitType.getStockInitial());
			this.unitToSellState.put(unitTypeId, state);
			this.unitsSold.add(unitTypeId);
			this.scriptStockedUnits.add(unitTypeId);
		}
		state.setStock(currentStock, stockMax);
	}

	/**
	 * Only stock a script added can be removed (RemoveItemFromStock, RemoveUnitFromStock); the types the shop sells
	 * by its data stay.
	 */
	public void removeFromStock(final War3ID unitTypeId) {
		if (!this.scriptStockedUnits.contains(unitTypeId)) {
			return;
		}
		this.unitToSellState.remove(unitTypeId);
		this.unitsSold.remove(unitTypeId);
		this.scriptStockedUnits.remove(unitTypeId);
	}
}
