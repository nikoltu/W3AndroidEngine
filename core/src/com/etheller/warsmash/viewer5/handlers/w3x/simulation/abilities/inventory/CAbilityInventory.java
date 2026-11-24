package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.item.CEquipmentType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitTypeRequirement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericNoIconAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.SingleOrderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.CAbilityItemSpellBook;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActiveFlexTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActivePointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActiveUnitTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory.CBehaviorDropItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory.CBehaviorGetItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory.CBehaviorGiveItemToHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ExternStringMsgAbilityActivationReceiver;

public class CAbilityInventory extends AbstractGenericNoIconAbility {
	private boolean canDropItems;
	private boolean canGetItems;
	private boolean canUseItems;
	private boolean dropItemsOnDeath;
	private final List<CUnitTypeRequirement> requirements;
	private final CItem[] itemsHeld;
	private final List<CAbility>[] itemsHeldAbilities;
	// Worn items by loadout slot (the 3.0 equipment); null until something is equipped.
	private CItem[] loadout;
	private List<CAbility>[] loadoutAbilities;
	private CItem droppingItem;
	private CBehaviorGetItem behaviorGetItem;
	private CBehaviorDropItem behaviorDropItem;
	private CBehaviorGiveItemToHero behaviorGiveItem;
	private CAbility chargeOnEffectAbility;
	private CItem chargeOnEffectItem;

	public CAbilityInventory(final int handleId, final War3ID code, final War3ID alias, final boolean canDropItems,
			final boolean canGetItems, final boolean canUseItems, final boolean dropItemsOnDeath,
			final int itemCapacity, final List<CUnitTypeRequirement> requirements) {
		super(handleId, code, alias);
		this.requirements = requirements;
		this.canDropItems = canDropItems;
		this.canGetItems = canGetItems;
		this.canUseItems = canUseItems;
		this.dropItemsOnDeath = dropItemsOnDeath;
		this.itemsHeld = new CItem[itemCapacity];
		this.itemsHeldAbilities = new List[itemCapacity];
		for (int i = 0; i < this.itemsHeldAbilities.length; i++) {
			this.itemsHeldAbilities[i] = new ArrayList<>();
		}
	}

	// A unit inventory such as the 3.0 backpack stays disabled until its research is done.
	@Override
	public boolean isRequirementsMet(final CSimulation game, final CUnit unit) {
		if (this.requirements != null) {
			for (final CUnitTypeRequirement requirement : this.requirements) {
				if (!game.getPlayer(unit.getPlayerIndex())
						.isRequirementMet(requirement.getRequirement(), requirement.getRequiredLevel())) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void checkRequirementsMet(final CSimulation game, final CUnit unit,
			final AbilityActivationReceiver receiver) {
		if (this.requirements != null) {
			for (final CUnitTypeRequirement requirement : this.requirements) {
				if (!game.getPlayer(unit.getPlayerIndex())
						.isRequirementMet(requirement.getRequirement(), requirement.getRequiredLevel())) {
					receiver.missingRequirement(requirement.getRequirement(), requirement.getRequiredLevel());
				}
			}
		}
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.behaviorGetItem = new CBehaviorGetItem(unit, this);
		this.behaviorDropItem = new CBehaviorDropItem(unit, this);
		this.behaviorGiveItem = new CBehaviorGiveItemToHero(unit, this);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		for (int i = 0; i < this.itemsHeld.length; i++) {
			if (this.itemsHeld[i] != null) {
				dropItem(game, unit, i, unit.getX(), unit.getY(), false);
			}
		}
		dropEquipment(game, unit, false);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {

	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if ((orderId >= OrderIds.itemDragFirst(nineSlots(game))) && (orderId <= OrderIds.itemDragLast(nineSlots(game)))) {
			if (moveItemToSlot(target, orderId - OrderIds.itemDragFirst(nineSlots(game)))) {
				return false;
			}
		}
		else if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
			final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
			final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
			if (!itemsHeldAbilitiesForSlot.isEmpty()) {
				final CAbility cAbility = itemsHeldAbilitiesForSlot.get(0);
				int forwardedOrderId = orderId;
				if (cAbility instanceof SingleOrderAbility) {
					forwardedOrderId = ((SingleOrderAbility) cAbility).getBaseOrderId();
				}
				final boolean checkResult = cAbility.checkBeforeQueue(game, caster, playerIndex, forwardedOrderId,
						false, target);
				if (!checkResult) {
					// we will never call begin, so we need to consume a charge of perishables here
					// assuming this is a no-queue instant use perishable... later if we have some
					// other weird case where "check before queue" false is supposed to mean you
					// can't use the skill, then this would consume charges without using it, and
					// that would be stupid but I don't think we will do that since checkCanUse
					// should be failing at that point. So then we should have never called
					// checkBeforeQueue.
					final CItem cItem = this.itemsHeld[slot];
					consumePerishableCharge(game, caster, slot, cItem);
				}
				return checkResult;
			}
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	/**
	 * Spends a charge of a held item whose ability does it only when its effect happens (Town Portal).
	 */
	public void spendItemCharge(final CSimulation game, final CUnit caster, final CItem item) {
		final int slot = getSlot(item);
		if (slot >= 0) {
			consumePerishableCharge(game, caster, slot, item);
		}
	}

	/**
	 * Measured on 1.29: a cast item spell spends the charge and fires the use event when the spell takes effect, after
	 * the turn and the cast point; an order interrupted before that keeps the charge.
	 */
	public boolean isItemSpellCast(final CAbility ability) {
		return ability == this.chargeOnEffectAbility;
	}

	public void onItemAbilityEffect(final CSimulation game, final CUnit caster, final CAbility ability) {
		if ((ability == this.chargeOnEffectAbility) && (this.chargeOnEffectItem != null)) {
			final CItem item = this.chargeOnEffectItem;
			this.chargeOnEffectAbility = null;
			this.chargeOnEffectItem = null;
			spendItemCharge(game, caster, item);
		}
	}

	private void useItemCharge(final CSimulation game, final CUnit caster, final int slot, final CAbility ability,
			final CBehavior behavior) {
		final CItem cItem = this.itemsHeld[slot];
		if (ability instanceof ItemChargeSpentOnEffect) {
			return;
		}
		// A spell with a point or unit target walks into range first (its behavior then moves, it is not yet the
		// spell's own), so its charge goes with the effect as well.
		if ((behavior instanceof CBehaviorTargetSpellBase) || (behavior instanceof CBehaviorNoTargetSpellBase)
				|| isRangedAbilityBuilderSpell(ability)
				|| ((behavior != null) && (ability instanceof CAbilitySpellBase))) {
			this.chargeOnEffectAbility = ability;
			this.chargeOnEffectItem = cItem;
			return;
		}
		consumePerishableCharge(game, caster, slot, cItem);
	}

	/** An ability builder spell with a target walks into range first, so its item charge is spent when it takes effect. */
	private static boolean isRangedAbilityBuilderSpell(final CAbility ability) {
		return (ability instanceof CAbilityAbilityBuilderActiveUnitTarget)
				|| (ability instanceof CAbilityAbilityBuilderActivePointTarget)
				|| ((ability instanceof CAbilityAbilityBuilderActiveFlexTarget)
						&& ((CAbilityAbilityBuilderActiveFlexTarget) ability).isTargetedSpell());
	}

	private void consumePerishableCharge(final CSimulation game, final CUnit caster, final int slot,
			final CItem cItem) {
		if (cItem == null) {
			return;
		}
		final int updatedCharges = cItem.getCharges() - 1;
		if (updatedCharges >= 0) {
			cItem.setCharges(updatedCharges);
		}
		// Measured on 1.29: the use event sees the charge already spent and the item still held; a used-up
		// perishable item then fires a drop event before it goes.
		caster.fireUseItemEvents(game, cItem);
		if ((updatedCharges == 0) && cItem.isPerishable() && (this.itemsHeld[slot] == cItem)) {
			dropItem(game, caster, slot, caster.getX(), caster.getY(), false);
			game.removeItem(cItem);
		}
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {

	}

	/** What the level data of the ability allows; the slots and what they hold stay as they are. */
	public void setPermissions(final boolean canDropItems, final boolean canGetItems, final boolean canUseItems,
			final boolean dropItemsOnDeath) {
		this.canDropItems = canDropItems;
		this.canGetItems = canGetItems;
		this.canUseItems = canUseItems;
		this.dropItemsOnDeath = dropItemsOnDeath;
	}

	public int getItemCapacity() {
		return this.itemsHeld.length;
	}

	/**
	 * Whether picking this item up would succeed: a free slot, room in a stack of the same type, or a powerup that is used on
	 * pickup and never takes a slot.
	 */
	public boolean hasRoomFor(final CItem item) {
		if (item.isUseAutomaticallyWhenAcquired()) {
			return true;
		}
		final int stackMax = item.getStackMax();
		for (final CItem held : this.itemsHeld) {
			if (held == null) {
				return true;
			}
			if ((stackMax > 0) && (item.getCharges() > 0) && held.getTypeId().equals(item.getTypeId())
					&& (held.getCharges() < stackMax)) {
				return true;
			}
		}
		return false;
	}

	public CItem getItemInSlot(final int slotIndex) {
		if ((slotIndex < 0) || (slotIndex >= this.itemsHeld.length)) {
			return null;
		}
		return this.itemsHeld[slotIndex];
	}

	/** The spell book carried in the slot an item-use order names, or null if the order is not for one. */
	public CAbilityItemSpellBook getSpellBookForOrder(final CSimulation game, final int orderId) {
		if ((orderId < OrderIds.itemUseFirst(nineSlots(game))) || (orderId > OrderIds.itemUseLast(nineSlots(game)))) {
			return null;
		}
		for (final CAbility ability : this.itemsHeldAbilities[orderId - OrderIds.itemUseFirst(nineSlots(game))]) {
			if (ability instanceof CAbilityItemSpellBook) {
				return (CAbilityItemSpellBook) ability;
			}
		}
		return null;
	}

	/** Whether the ability is one of the abilities of an item carried or worn in this inventory. */
	public boolean isHeldItemAbility(final CAbility ability) {
		for (final List<CAbility> slotAbilities : this.itemsHeldAbilities) {
			if (slotAbilities.contains(ability)) {
				return true;
			}
		}
		if (this.loadoutAbilities != null) {
			for (final List<CAbility> slotAbilities : this.loadoutAbilities) {
				if ((slotAbilities != null) && slotAbilities.contains(ability)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isCanGetItems() {
		return this.canGetItems;
	}

	public boolean isDropItemsOnDeath() {
		return this.dropItemsOnDeath;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
			final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
			final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
			if (!itemsHeldAbilitiesForSlot.isEmpty()) {
				final CAbility ability = itemsHeldAbilitiesForSlot.get(0);
				int forwardedOrderId = orderId;
				if (ability instanceof SingleOrderAbility) {
					forwardedOrderId = ((SingleOrderAbility) ability).getBaseOrderId();
				}
				final CBehavior behavior = ability.begin(game, caster, playerIndex, forwardedOrderId, false, target);
				useItemCharge(game, caster, slot, ability, behavior);
				return behavior;
			}
		}
		final CItem targetItem = target.visit(AbilityTargetVisitor.ITEM);
		if (targetItem != null) {
			return this.behaviorGetItem.reset(game, (CItem) target);
		}
		return caster.pollNextOrderBehavior(game);
	}

	public CBehavior beginDropItem(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final CItem itemToDrop, final AbilityPointTarget target) {
		return this.behaviorDropItem.reset(game, itemToDrop, target);
	}

	public CBehavior beginDropItem(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final CItem itemToDrop, final CUnit targetHero) {
		return this.behaviorGiveItem.reset(game, itemToDrop, targetHero);
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
			final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
			final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
			if (!itemsHeldAbilitiesForSlot.isEmpty()) {
				final CAbility ability = itemsHeldAbilitiesForSlot.get(0);
				int forwardedOrderId = orderId;
				if (ability instanceof SingleOrderAbility) {
					forwardedOrderId = ((SingleOrderAbility) ability).getBaseOrderId();
				}
				final CBehavior behavior = ability.begin(game, caster, playerIndex, forwardedOrderId, false, point);
				useItemCharge(game, caster, slot, ability, behavior);
				return behavior;
			}
		}
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
			final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
			final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
			if (!itemsHeldAbilitiesForSlot.isEmpty()) {
				final CAbility ability = itemsHeldAbilitiesForSlot.get(0);
				int forwardedOrderId = orderId;
				if (ability instanceof SingleOrderAbility) {
					forwardedOrderId = ((SingleOrderAbility) ability).getBaseOrderId();
				}
				final CBehavior behavior = ability.beginNoTarget(game, caster, playerIndex, forwardedOrderId, false);
				useItemCharge(game, caster, slot, ability, behavior);
				return behavior;
			}
		}
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (((orderId == OrderIds.getitem) || (orderId == OrderIds.smart)) && !target.isDead()) {
			if (target instanceof CItem) {
				if (this.canGetItems && !isDisabled()) {
					final CItem targetItem = (CItem) target;
					if (targetItem.isHidden()) {
						receiver.orderIdNotAccepted();
					}
					else if (!hasRoomFor(targetItem)) {
						// Measured on 1.29: with no room the order is refused at once, the unit does not walk there.
						receiver.targetCheckFailed(CommandStringErrorKeys.INVENTORY_IS_FULL);
					}
					else {
						receiver.targetOk(target);
					}
				}
				else {
					receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_PICK_UP_THIS_ITEM);
				}
			}
			else {
				receiver.orderIdNotAccepted();
			}
		}
		else {
			if ((orderId >= OrderIds.itemDragFirst(nineSlots(game))) && (orderId <= OrderIds.itemDragLast(nineSlots(game)))) {
				if (target instanceof CItem) {
					final int slot = getSlot((CItem) target);
					if (slot != -1) {
						receiver.targetOk(target);
					}
					else {
						receiver.orderIdNotAccepted();
					}
				}
				else {
					receiver.orderIdNotAccepted();
				}
			}
			else if (orderId == OrderIds.dropitem) {
				if (target instanceof CUnit) {
					final CUnit hero = (CUnit) target;
					if (game.getPlayer(hero.getPlayerIndex()).hasAlliance(unit.getPlayerIndex(), CAllianceType.PASSIVE)
							&& (hero != unit)) {
						// Measured on 1.29: a unit with neither inventory nor shop is accepted too, the item lands at its feet.
						receiver.targetOk(target);
					}
					else {
						receiver.orderIdNotAccepted();
					}
				}
				else {
					receiver.orderIdNotAccepted();
				}
			}
			else {
				if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
					final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
					final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
					if (!itemsHeldAbilitiesForSlot.isEmpty()) {
						final CAbility ability = itemsHeldAbilitiesForSlot.get(0);
						int forwardedOrderId = orderId;
						if (ability instanceof SingleOrderAbility) {
							forwardedOrderId = ((SingleOrderAbility) ability).getBaseOrderId();
						}
						ability.checkCanTarget(game, unit, playerIndex, forwardedOrderId, false, target, receiver);
					}
					else {
						receiver.orderIdNotAccepted();
					}
				}
				else {
					receiver.orderIdNotAccepted();
				}
			}
		}
	}

	public boolean moveItemToSlot(final Object item, final int destinationSlot) {
		if ((destinationSlot < 0) || (destinationSlot >= this.itemsHeld.length)) {
			return false;
		}
		for (int i = 0; i < this.itemsHeld.length; i++) {
			if ((item != null) && (this.itemsHeld[i] == item)) {
				final CItem temp = this.itemsHeld[i];
				final List<CAbility> swapList = this.itemsHeldAbilities[i];
				this.itemsHeld[i] = this.itemsHeld[destinationSlot];
				this.itemsHeldAbilities[i] = this.itemsHeldAbilities[destinationSlot];
				this.itemsHeld[destinationSlot] = temp;
				this.itemsHeldAbilities[destinationSlot] = swapList;
				return true;
			}
		}
		return false;
	}

	public int getSlot(final CItem target) {
		int slot = -1;
		for (int i = 0; i < this.itemsHeld.length; i++) {
			if (this.itemsHeld[i] == target) {
				slot = i;
			}
		}
		return slot;
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		if (orderId != OrderIds.dropitem) {
			if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
				final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
				final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
				if (!itemsHeldAbilitiesForSlot.isEmpty()) {
					final CAbility ability = itemsHeldAbilitiesForSlot.get(0);
					int forwardedOrderId = orderId;
					if (ability instanceof SingleOrderAbility) {
						forwardedOrderId = ((SingleOrderAbility) ability).getBaseOrderId();
					}
					ability.checkCanTarget(game, unit, playerIndex, forwardedOrderId, false, target, receiver);
				}
				else {
					receiver.orderIdNotAccepted();
				}
			}
			else {
				receiver.orderIdNotAccepted();
			}
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
			final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
			final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
			if (!itemsHeldAbilitiesForSlot.isEmpty()) {
				final CAbility ability = itemsHeldAbilitiesForSlot.get(0);
				int forwardedOrderId = orderId;
				if (ability instanceof SingleOrderAbility) {
					forwardedOrderId = ((SingleOrderAbility) ability).getBaseOrderId();
				}
				ability.checkCanTargetNoTarget(game, unit, playerIndex, forwardedOrderId, false, receiver);
			}
			else {
				receiver.orderIdNotAccepted();
			}
		}
		else {
			receiver.orderIdNotAccepted();
		}
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		if (unit.isIllusion()) {
			receiver.activationCheckFailed(CommandStringErrorKeys.ILLUSIONS_CANNOT_PICK_UP_ITEMS);
			return;
		}
		if ((orderId >= OrderIds.itemUseFirst(nineSlots(game))) && (orderId <= OrderIds.itemUseLast(nineSlots(game)))) {
			if (this.canUseItems) {
				final int slot = orderId - OrderIds.itemUseFirst(nineSlots(game));
				if (this.itemsHeldAbilities[slot].size() < 1) {
					receiver.notAnActiveAbility();
				}
				else {
					final List<CAbility> itemsHeldAbilitiesForSlot = this.itemsHeldAbilities[slot];
					if (!itemsHeldAbilitiesForSlot.isEmpty()) {
						final CAbility ability = itemsHeldAbilitiesForSlot.get(0);
						int forwardedOrderId = orderId;
						if (ability instanceof SingleOrderAbility) {
							forwardedOrderId = ((SingleOrderAbility) ability).getBaseOrderId();
						}
						ability.checkCanUse(game, unit, playerIndex, forwardedOrderId, false, receiver);
					}
					else {
						receiver.notAnActiveAbility();
					}
				}
			}
			else {
				receiver.activationCheckFailed(CommandStringErrorKeys.UNABLE_TO_USE_THIS_ITEM);
			}
		}
		else if ((orderId == OrderIds.dropitem) && !this.canDropItems) {
			receiver.activationCheckFailed(CommandStringErrorKeys.UNABLE_TO_DROP_THIS_ITEM);
		}
		else {
			receiver.useOk();
		}
	}

	public int giveItem(final CSimulation simulation, final CUnit hero, final CItem item,
			final boolean playUserUISounds) {
		return giveItem(simulation, hero, item, 0, playUserUISounds);
	}

	/**
	 * Attempts to give the hero the specified item, returning the item slot to
	 * which the item is added or -1 if no available slot is found
	 *
	 * @param item
	 * @return
	 */
	public int giveItem(final CSimulation simulation, final CUnit hero, final CItem item, final int slotPreference,
			final boolean playUserUISounds) {
		if ((item != null) && !item.isDead() && !item.isHidden() && isRequirementsMet(simulation, hero)) {
			final CItemType itemType = item.getItemType();
			if (this.canUseItems && item.isUseAutomaticallyWhenAcquired()) {
				if (item.isActivelyUsed()) {

					// TODO when we give unit ability here, then use ability
					final List<CAbility> addedAbilities = new ArrayList<>();
					for (final War3ID abilityId : item.getAbilityIds()) {
						final CAbilityType<?> abilityType = simulation.getAbilityData().getAbilityType(abilityId);
						if (abilityType != null) {
							final CAbility abilityFromItem = abilityType
									.createAbility(simulation.getHandleIdAllocator().createId());
							abilityFromItem.setIconShowing(false);
							abilityFromItem.setItemAbility(item, -1);
							hero.add(simulation, abilityFromItem);
							if (abilityFromItem instanceof SingleOrderAbility) {
								final int baseOrderId = ((SingleOrderAbility) abilityFromItem).getBaseOrderId();

								final ExternStringMsgAbilityActivationReceiver act = ExternStringMsgAbilityActivationReceiver.INSTANCE;
								act.reset();
								abilityFromItem.checkCanUse(simulation, hero, hero.getPlayerIndex(), baseOrderId, false,
										act);
								if (act.isUseOk()) {
									item.setLife(simulation, 0);
									final BooleanAbilityTargetCheckReceiver<CWidget> booleanUnitTargetReceiver = BooleanAbilityTargetCheckReceiver
											.<CWidget>getInstance().reset();
									abilityFromItem.checkCanTarget(simulation, hero, hero.getPlayerIndex(), baseOrderId,
											false, hero, booleanUnitTargetReceiver);
									if (booleanUnitTargetReceiver.isTargetable()) {
										hero.order(simulation, new COrderTargetWidget(hero.getPlayerIndex(),
												abilityFromItem.getHandleId(), baseOrderId, hero.getHandleId(), false),
												false);

									}
									else {
										final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
												.<AbilityPointTarget>getInstance().reset();
										final AbilityPointTarget tar = new AbilityPointTarget(hero.getX(), hero.getY());
										abilityFromItem.checkCanTarget(simulation, hero, hero.getPlayerIndex(),
												baseOrderId, false, tar, booleanTargetReceiver);

										if (booleanTargetReceiver.isTargetable()) {
											hero.order(simulation,
													new COrderTargetPoint(hero.getPlayerIndex(),
															abilityFromItem.getHandleId(), baseOrderId, tar, false),
													false);
										}
										else {
											hero.order(simulation, new COrderNoTarget(hero.getPlayerIndex(),
													abilityFromItem.getHandleId(), baseOrderId, false), false);
										}
									}
								}
								else {
									final String externStringKey = act.getExternStringKey();
									if ((externStringKey != null) && !externStringKey.isEmpty()) {
										simulation.getCommandErrorListener().showInterfaceError(hero.getPlayerIndex(),
												externStringKey);
									}
								}
							}
							addedAbilities.add(abilityFromItem);
						}
					}
					hero.onPickUpItem(simulation, item, true);
					for (final CAbility ability : addedAbilities) {
						hero.remove(simulation, ability);
					}
				}
				else if (item.getAbilityIds().isEmpty()) {
					// Measured on 1.29: a power up with no ability is picked up (the event sees it alive and not
					// held) and then dies; UnitAddItem returns true.
					hero.onPickUpItem(simulation, item, true);
					item.setLife(simulation, 0);
				}
			}
			else {
				final int stackedSlot = stackCharges(simulation, hero, item);
				if (stackedSlot != -1) {
					return stackedSlot;
				}
				for (int i = 0; i < this.itemsHeld.length; i++) {
					final int itemIndex = (i + slotPreference) % this.itemsHeld.length;
					if (this.itemsHeld[itemIndex] == null) {
						this.itemsHeld[itemIndex] = item;
						item.setHidden(true);
						item.setContainedInventory(this, hero);
						item.setGroundAbilities(null);
						if (this.canUseItems) {
							for (final War3ID abilityId : item.getAbilityIds()) {
								final CAbility abilityFromItem = createItemAbility(simulation, hero, item, abilityId,
										itemIndex);
								if (abilityFromItem != null) {
									this.itemsHeldAbilities[itemIndex].add(abilityFromItem);
								}
							}
							applyCarriedCooldowns(simulation, hero, item);
						}
						hero.onPickUpItem(simulation, item, true);
						return itemIndex;
					}
				}
				if (playUserUISounds) {
					simulation.getCommandErrorListener().showInterfaceError(hero.getPlayerIndex(),
							CommandStringErrorKeys.INVENTORY_IS_FULL);
				}
			}
		}
		return -1;
	}

	private CAbility createItemAbility(final CSimulation simulation, final CUnit hero, final CItem item,
			final War3ID abilityId, final int slot) {
		final CAbilityType<?> abilityType = simulation.getAbilityData().getAbilityType(abilityId);
		if (abilityType == null) {
			return null;
		}
		final CAbility abilityFromItem = abilityType.createAbility(simulation.getHandleIdAllocator().createId());
		abilityFromItem.setIconShowing(false);
		abilityFromItem.setItemAbility(item, slot);
		hero.add(simulation, abilityFromItem);
		return abilityFromItem;
	}

	/**
	 * Takes the charges of a picked up item into the held items of its type that have room (stackMax), in slot
	 * order. An item whose charges all went is absorbed: it is picked up into the stack that took the last of it, and
	 * then removed. Returns that slot, or -1 when the item still has charges and goes to a free slot as usual.
	 */
	private int stackCharges(final CSimulation simulation, final CUnit hero, final CItem item) {
		final int stackMax = item.getStackMax();
		if ((stackMax <= 0) || (item.getCharges() <= 0)) {
			return -1;
		}
		int lastSlot = -1;
		for (int i = 0; (i < this.itemsHeld.length) && (item.getCharges() > 0); i++) {
			final CItem target = this.itemsHeld[i];
			if ((target == null) || (target == item) || !target.getTypeId().equals(item.getTypeId())) {
				continue;
			}
			final int previousCharges = target.getCharges();
			final int moved = Math.min(stackMax - previousCharges, item.getCharges());
			if (moved <= 0) {
				continue;
			}
			target.setCharges(previousCharges + moved);
			item.setCharges(item.getCharges() - moved);
			lastSlot = i;
			hero.fireStackItemEvents(simulation, item, target, previousCharges);
			if (item.getCharges() <= 0) {
				hero.onPickUpItem(simulation, item, true, target);
				simulation.removeItem(item);
				return i;
			}
		}
		return -1;
	}

	/** The abilities a held item gives its carrier, in the order they were added. */
	public List<CAbility> getItemAbilities(final CItem item) {
		final List<CAbility> held = heldAbilities(item);
		return (held == null) ? new ArrayList<>() : new ArrayList<>(held);
	}

	/** The live list of the abilities a carried or worn item gives, or null when this unit does not hold it. */
	private List<CAbility> heldAbilities(final CItem item) {
		final int slot = getSlot(item);
		if (slot >= 0) {
			return this.itemsHeldAbilities[slot];
		}
		final int worn = getLoadoutSlot(item);
		return (worn >= 0) ? this.loadoutAbilities[worn] : null;
	}

	/** Gives the hero an ability through a held item; the item has it from then on. */
	public CAbility addItemAbility(final CSimulation simulation, final CUnit hero, final CItem item,
			final War3ID abilityId) {
		final List<CAbility> held = heldAbilities(item);
		if ((held == null) || !this.canUseItems) {
			return null;
		}
		final CAbility ability = createItemAbility(simulation, hero, item, abilityId, getSlot(item));
		if (ability != null) {
			held.add(ability);
		}
		return ability;
	}

	public void removeItemAbility(final CSimulation simulation, final CUnit hero, final CItem item,
			final War3ID abilityId) {
		final List<CAbility> slotAbilities = heldAbilities(item);
		if (slotAbilities == null) {
			return;
		}
		for (int i = 0; i < slotAbilities.size(); i++) {
			final CAbility ability = slotAbilities.get(i);
			if (abilityId.equals(ability.getAlias())) {
				slotAbilities.remove(i);
				hero.remove(simulation, ability);
				return;
			}
		}
	}

	private static void carryCooldowns(final CSimulation simulation, final CUnit hero, final CItem item,
			final List<CAbility> itemAbilities) {
		item.clearCarriedCooldowns();
		if (item.getCharges() != 0) {
			return;
		}
		for (final CAbility ability : itemAbilities) {
			final War3ID key = cooldownKey(item, ability);
			if (key != null) {
				final int remaining = hero.getCooldownRemainingTicks(simulation, key);
				if (remaining > 0) {
					item.carryCooldown(key, simulation.getGameTurnTick() + remaining);
				}
			}
		}
	}

	/** The cooldown an item's ability runs on the unit: the item's group for the ability builder kind, its code otherwise. */
	public static War3ID cooldownKey(final CItem item, final CAbility ability) {
		if (ability instanceof AbilityBuilderAbility) {
			if (item.getItemType().isIgnoreCooldown()) {
				return null;
			}
			final War3ID group = item.getItemType().getCooldownGroup();
			return (group != null) ? group : ability.getCode();
		}
		return (ability instanceof CAbilitySpellBase) ? ability.getCode() : null;
	}

	private static void applyCarriedCooldowns(final CSimulation simulation, final CUnit hero, final CItem item) {
		for (final java.util.Map.Entry<War3ID, Integer> entry : item.getCarriedCooldownEnds().entrySet()) {
			final int remaining = entry.getValue() - simulation.getGameTurnTick();
			if (remaining > hero.getCooldownRemainingTicks(simulation, entry.getKey())) {
				hero.beginCooldown(simulation, entry.getKey(), remaining * WarsmashConstants.SIMULATION_STEP_TIME);
			}
		}
		item.clearCarriedCooldowns();
	}

	public void dropItem(final CSimulation simulation, final CUnit hero, final int slotIndex, final float x,
			final float y, final boolean playUserUISounds) {
		final CItem droppedItem = this.itemsHeld[slotIndex];
		if (droppedItem != null) {
			dropItem(simulation, hero, droppedItem, x, y, playUserUISounds);
		}
	}

	public void dropItem(final CSimulation simulation, final CUnit hero, final CItem itemToDrop, final float x,
			final float y, final boolean playUserUISounds) {
		final int index = getSlot(itemToDrop);
		final int worn = (index < 0) ? getLoadoutSlot(itemToDrop) : -1;
		if (((index < 0) && (worn < 0)) || (itemToDrop == this.droppingItem)) {
			// Not held, or a drop event's trigger removed the item while it is being dropped: the outer drop
			// finishes it.
			return;
		}
		// Measured on 1.29: the drop event fires while the hero still holds the item.
		this.droppingItem = itemToDrop;
		try {
			if (worn >= 0) {
				hero.fireUnequipItemEvents(simulation, itemToDrop);
			}
			hero.fireDropItemEvents(simulation, itemToDrop);
		}
		finally {
			this.droppingItem = null;
		}
		final List<CAbility> itemAbilities;
		if (index >= 0) {
			if (this.itemsHeld[index] != itemToDrop) {
				return;
			}
			this.itemsHeld[index] = null;
			itemAbilities = this.itemsHeldAbilities[index];
		}
		else {
			if (this.loadout[worn] != itemToDrop) {
				return;
			}
			this.loadout[worn] = null;
			itemAbilities = this.loadoutAbilities[worn];
		}
		hero.onDropItem(simulation, itemToDrop, playUserUISounds);
		carryCooldowns(simulation, hero, itemToDrop, itemAbilities);
		itemToDrop.setLastHolder(hero);
		itemToDrop.setContainedInventory(null, null);
		for (final CAbility ability : itemAbilities) {
			hero.remove(simulation, ability);
		}
		itemAbilities.clear();
		if (!itemToDrop.isDead()) {
			itemToDrop.setHidden(false);
			itemToDrop.setPointAndCheckUnstuck(x, y, simulation);
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit hero) {
		if (hero.isIllusion()) {
			// An illusion's items are copies and vanish with it.
			for (int i = 0; i < this.itemsHeld.length; i++) {
				if (this.itemsHeld[i] != null) {
					game.removeItem(this.itemsHeld[i]);
				}
			}
			for (int i = 0; (this.loadout != null) && (i < this.loadout.length); i++) {
				if (this.loadout[i] != null) {
					game.removeItem(this.loadout[i]);
				}
			}
			return;
		}
		if (this.dropItemsOnDeath) {
			for (int i = 0; i < this.itemsHeld.length; i++) {
				if (this.itemsHeld[i] != null) {
					dropItem(game, hero, i, hero.getX(), hero.getY(), false);
				}
			}
			dropEquipment(game, hero, false);
		}
	}

	private void dropEquipment(final CSimulation game, final CUnit hero, final boolean playUserUISounds) {
		for (int i = 0; (this.loadout != null) && (i < this.loadout.length); i++) {
			if (this.loadout[i] != null) {
				dropItem(game, hero, this.loadout[i], hero.getX(), hero.getY(), playUserUISounds);
			}
		}
	}

	public CItem getItemInLoadoutSlot(final int slot) {
		if ((this.loadout == null) || (slot < 0) || (slot >= this.loadout.length)) {
			return null;
		}
		return this.loadout[slot];
	}

	/** The loadout slot a worn item is in, or -1 when it is not worn by this unit. */
	public int getLoadoutSlot(final CItem item) {
		for (int i = 0; (item != null) && (this.loadout != null) && (i < this.loadout.length); i++) {
			if (this.loadout[i] == item) {
				return i;
			}
		}
		return -1;
	}

	public boolean hasEquipment() {
		for (int i = 0; (this.loadout != null) && (i < this.loadout.length); i++) {
			if (this.loadout[i] != null) {
				return true;
			}
		}
		return false;
	}

	/** Whether a worn item is of this equipment type; ANY matches any worn item. */
	public boolean hasEquipmentOfType(final CEquipmentType type) {
		for (int i = 0; (this.loadout != null) && (i < this.loadout.length); i++) {
			if ((this.loadout[i] != null)
					&& ((type == CEquipmentType.ANY) || (this.loadout[i].getEquipmentType() == type))) {
				return true;
			}
		}
		return false;
	}

	/** The first free loadout slot for an item of this type, or -1 when it has none or all of them are taken. */
	public int getFreeLoadoutSlot(final CEquipmentType type) {
		for (final int slot : type.getLoadoutSlots()) {
			if (getItemInLoadoutSlot(slot) == null) {
				return slot;
			}
		}
		return -1;
	}

	/**
	 * Wears an item that lies on the ground or is carried in the slots of this unit, in the first free loadout slot
	 * of its equipment type. A worn item keeps giving the unit its abilities; it is not one of the numbered slots,
	 * so its abilities cannot be used from the slot buttons.
	 */
	public boolean equipItem(final CSimulation simulation, final CUnit hero, final CItem item) {
		return equipItem(simulation, hero, item, -1);
	}

	/**
	 * As {@link #equipItem(CSimulation, CUnit, CItem)}, into the given loadout slot when the item's equipment type
	 * has it and it is free (a unit restored from a campaign wears its items where it wore them); any other value
	 * takes the first free slot.
	 */
	public boolean equipItem(final CSimulation simulation, final CUnit hero, final CItem item,
			final int preferredSlot) {
		if ((item == null) || item.isDead() || !this.canGetItems || !isRequirementsMet(simulation, hero)
				|| (getLoadoutSlot(item) >= 0)) {
			return false;
		}
		int slot = getFreeLoadoutSlot(item.getEquipmentType());
		if (slot < 0) {
			return false;
		}
		if ((preferredSlot >= 0) && (preferredSlot < CEquipmentType.LOADOUT_SLOTS)
				&& (getItemInLoadoutSlot(preferredSlot) == null)) {
			for (final int allowed : item.getEquipmentType().getLoadoutSlots()) {
				if (allowed == preferredSlot) {
					slot = preferredSlot;
				}
			}
		}
		final int carried = getSlot(item);
		final List<CAbility> itemAbilities;
		if (carried >= 0) {
			itemAbilities = this.itemsHeldAbilities[carried];
			this.itemsHeldAbilities[carried] = new ArrayList<>();
			this.itemsHeld[carried] = null;
			for (final CAbility ability : itemAbilities) {
				ability.setItemAbility(item, -1);
			}
		}
		else if ((item.getContainedInventory() == null) && !item.isHidden()) {
			itemAbilities = new ArrayList<>();
			item.setHidden(true);
			item.setContainedInventory(this, hero);
			item.setGroundAbilities(null);
		}
		else {
			return false;
		}
		if (this.loadout == null) {
			this.loadout = new CItem[CEquipmentType.LOADOUT_SLOTS];
			this.loadoutAbilities = new List[CEquipmentType.LOADOUT_SLOTS];
		}
		this.loadout[slot] = item;
		this.loadoutAbilities[slot] = itemAbilities;
		if ((carried < 0) && this.canUseItems) {
			for (final War3ID abilityId : item.getAbilityIds()) {
				final CAbility abilityFromItem = createItemAbility(simulation, hero, item, abilityId, -1);
				if (abilityFromItem != null) {
					itemAbilities.add(abilityFromItem);
				}
			}
		}
		hero.onEquipItem(simulation, item);
		return true;
	}

	/**
	 * Takes a worn item off and carries it in the first free slot; a unit with no free slot puts it down where it
	 * stands. Returns the item, or null when nothing was worn in that slot.
	 */
	public CItem unequipSlot(final CSimulation simulation, final CUnit hero, final int loadoutSlot) {
		final CItem item = getItemInLoadoutSlot(loadoutSlot);
		if (item == null) {
			return null;
		}
		final List<CAbility> itemAbilities = this.loadoutAbilities[loadoutSlot];
		for (int i = 0; i < this.itemsHeld.length; i++) {
			if (this.itemsHeld[i] == null) {
				this.loadout[loadoutSlot] = null;
				this.loadoutAbilities[loadoutSlot] = null;
				this.itemsHeld[i] = item;
				this.itemsHeldAbilities[i] = itemAbilities;
				for (final CAbility ability : itemAbilities) {
					ability.setItemAbility(item, i);
				}
				hero.onUnequipItem(simulation, item);
				return item;
			}
		}
		dropItem(simulation, hero, item, hero.getX(), hero.getY(), false);
		return item;
	}


	private static boolean nineSlots(final CSimulation game) {
		return game.getGameData().isUseNineItemInventory();
	}
}
