package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CShopEvents;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitTypeRequirement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.AbstractCAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public final class CAbilitySellItems extends AbstractCAbility {
	private final Set<War3ID> itemsSold;
	private final Map<War3ID, SellStockState> itemToSellState;
	private final List<War3ID> scriptStockedItems = new ArrayList<>();
	private final boolean makeItems;
	private int itemTypeSlots = -1;

	public CAbilitySellItems(final int handleId, final List<War3ID> itemsSold, final boolean makeItems) {
		super(handleId, War3ID.fromString("Asei"));
		this.itemsSold = new LinkedHashSet<>(itemsSold);
		this.itemToSellState = new LinkedHashMap<>();
		this.makeItems = makeItems;
	}

	public Set<War3ID> getItemsSold() {
		return this.itemsSold;
	}

	/**
	 * Items added by a script (the marketplace) take the command card slots in the order they were stocked,
	 * instead of the button positions of the item types.
	 */
	public int getScriptStockSlot(final War3ID itemTypeId) {
		return this.scriptStockedItems.indexOf(itemTypeId);
	}

	public boolean isMakeItems() {
		return this.makeItems;
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		final War3ID itemTypeId = new War3ID(orderId);
		final SellStockState itemSellState = this.itemToSellState.get(itemTypeId);
		if (itemSellState != null) {
			final CItemType itemType = game.getItemData().getItemType(itemTypeId);
			if (itemType != null) {
				final CPlayer requirementsPlayer = game.getPlayer(unit.getPlayerIndex());
				if (this.makeItems) {
					final List<CUnitTypeRequirement> requirements = itemType.getRequirements();
					boolean requirementsMet = true;
					for (final CUnitTypeRequirement requirement : requirements) {
						if (requirementsPlayer.getTechtreeUnlocked(requirement.getRequirement()) < requirement
								.getRequiredLevel()) {
							requirementsMet = false;
							receiver.missingRequirement(requirement.getRequirement(), requirement.getRequiredLevel());
						}
					}
					if (!requirementsMet) {
						return; // call made by missingRequirement above
					}
				}
				if (!CAbilityNeutralBuilding.hasPatron(unit, playerIndex)) {
					receiver.activationCheckFailed(CommandStringErrorKeys.A_VALID_PATRON_MUST_BE_NEARBY);
				}
				else if (itemSellState.getStock() > 0) {
					final CPlayer player = game.getPlayer(playerIndex);
					if ((player.getGold() >= itemType.getGoldCost())) {
						if ((player.getLumber() >= itemType.getLumberCost())) {
							receiver.useOk();
						}
						else {
							receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_LUMBER);
						}
					}
					else {
						receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_GOLD);
					}
				}
				else {
					receiver.noChargesRemaining();
					receiver.cooldownNotYetReady(itemSellState.getCooldownRemaining(game.getGameTurnTick()),
							itemSellState.getCooldownMax());
				}
			}
			else {
				receiver.useOk();
			}
		}
		else {
			receiver.useOk();
		}
	}

	@Override
	public final void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public final void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public final void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		final War3ID itemTypeId = new War3ID(orderId);
		if (this.itemsSold.contains(itemTypeId)) {
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
		for (final War3ID itemId : this.itemsSold) {
			final CItemType itemType = game.getItemData().getItemType(itemId);
			if (itemType != null) {
				this.itemToSellState.put(itemId,
						SellStockState.fromData(game.getGameTurnTick(), itemType.getStockMax(),
								itemType.getStockReplenishInterval(), itemType.getStockStartDelay(),
								itemType.getStockInitial()));
			}
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		this.itemToSellState.clear();
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (!isDisabled()) {
			final CPlayer requirementsPlayer = game.getPlayer(unit.getPlayerIndex());
			final int gameTurnTick = game.getGameTurnTick();
			for (final Map.Entry<War3ID, SellStockState> entry : this.itemToSellState.entrySet()) {
				final SellStockState itemSellState = entry.getValue();
				final CItemType itemType = game.getItemData().getItemType(entry.getKey());
				if (itemType != null) {
					final List<CUnitTypeRequirement> requirements = itemType.getRequirements();
					boolean requirementsMet = true;
					for (final CUnitTypeRequirement requirement : requirements) {
						if (requirementsPlayer.getTechtreeUnlocked(requirement.getRequirement()) < requirement
								.getRequiredLevel()) {
							requirementsMet = false;
						}
					}
					if (requirementsMet) {
						itemSellState.update(gameTurnTick);
					}
				}
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
		final War3ID itemTypeId = new War3ID(orderId);
		final CAbilityNeutralBuilding neutralBuildingData = caster.getNeutralBuildingData();
		final SellStockState itemSellState = this.itemToSellState.get(itemTypeId);
		final CItemType itemType = game.getItemData().getItemType(itemTypeId);
		if ((itemSellState != null) && (itemSellState.getStock() > 0) && (itemType != null)) {
			if ((neutralBuildingData != null) && (neutralBuildingData.getSelectedPlayerUnit(playerIndex) != null)) {
				final CUnit purchasingHero = neutralBuildingData.getSelectedPlayerUnit(playerIndex);
				final CAbilityInventory purchasingInventoryData = purchasingHero.getInventoryData();
				if (purchasingInventoryData != null) {
					if (game.getPlayer(playerIndex).charge(itemType.getGoldCost(), itemType.getLumberCost())) {
						itemSellState.chargeStock(1);
						final CItem newItem = game.createItem(itemTypeId, caster.getX(), caster.getY());
						purchasingInventoryData.giveItem(game, purchasingHero, newItem, false);
						CShopEvents.fireSellItemEvents(game, caster, newItem, purchasingHero);
					}
				}
			}
			else if ((neutralBuildingData == null) || (playerIndex == caster.getPlayerIndex())) {
				if (game.getPlayer(playerIndex).charge(itemType.getGoldCost(), itemType.getLumberCost())) {
					itemSellState.chargeStock(1);
					final CItem newItem = game.createItem(itemTypeId, caster.getX(), caster.getY());
					CShopEvents.fireSellItemEvents(game, caster, newItem, null);
				}
			}
		}
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
	public void onSetUnitType(final CSimulation game, final CUnit cUnit) {
		// NOTE: this method not actually used, because CAbilityQueue is not Aliased
		onRemove(game, cUnit);
		final CUnitType unitType = cUnit.getUnitType();
		this.itemsSold.clear();
		this.scriptStockedItems.clear();
		this.itemsSold.addAll(unitType.getItemsSold());
		onAdd(game, cUnit);
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

	public int getStock(final War3ID itemType) {
		final SellStockState itemSellState = this.itemToSellState.get(itemType);
		if (itemSellState != null) {
			return itemSellState.getStock();
		}
		return 0;
	}

	public void setItemTypeSlots(final int itemTypeSlots) {
		this.itemTypeSlots = itemTypeSlots;
	}

	public void addToStock(final CSimulation game, final War3ID itemTypeId, final int currentStock,
			final int stockMax) {
		final CItemType itemType = game.getItemData().getItemType(itemTypeId);
		if (itemType == null) {
			return;
		}
		SellStockState state = this.itemToSellState.get(itemTypeId);
		if (state == null) {
			if ((this.itemTypeSlots >= 0) && (this.itemsSold.size() >= this.itemTypeSlots)) {
				return;
			}
			state = new SellStockState(stockMax,
					(int) (itemType.getStockReplenishInterval() / WarsmashConstants.SIMULATION_STEP_TIME),
					itemType.getStockInitial());
			this.itemToSellState.put(itemTypeId, state);
			this.itemsSold.add(itemTypeId);
			this.scriptStockedItems.add(itemTypeId);
		}
		state.setStock(currentStock, stockMax);
	}

	/**
	 * Only stock a script added can be removed (RemoveItemFromStock, RemoveUnitFromStock); the types the shop sells
	 * by its data stay.
	 */
	public void removeFromStock(final War3ID itemTypeId) {
		if (!this.scriptStockedItems.contains(itemTypeId)) {
			return;
		}
		this.itemToSellState.remove(itemTypeId);
		this.itemsSold.remove(itemTypeId);
		this.scriptStockedItems.remove(itemTypeId);
	}
}
