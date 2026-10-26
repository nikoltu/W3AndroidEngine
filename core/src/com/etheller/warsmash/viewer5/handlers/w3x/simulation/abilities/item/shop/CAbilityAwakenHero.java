package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CGameplayConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericAliasedAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Awaken (tavern): a dead hero of the buyer comes back at once next to the tavern, for the Awaken costs of
 * miscgame.txt and with the HeroAwaken life and mana. The command card orders it by the hero's handle id, scripts
 * with the awaken orders targeting the hero.
 */
public class CAbilityAwakenHero extends AbstractGenericAliasedAbility {

	public CAbilityAwakenHero(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
	}

	public static boolean isAwakenOrder(final int orderId) {
		return (orderId == OrderIds.awaken)
				|| ((orderId >= OrderIds._awakenunit1tavern) && (orderId <= OrderIds._awakenunit4tavern));
	}

	public static int getGoldCost(final CSimulation game, final CUnit hero) {
		return game.getGameplayConstants().getHeroAwakenGoldCost(hero.getUnitType().getGoldCost(),
				hero.getHeroData().getHeroLevel());
	}

	public static int getLumberCost(final CSimulation game, final CUnit hero) {
		return game.getGameplayConstants().getHeroAwakenLumberCost(hero.getUnitType().getLumberCost(),
				hero.getHeroData().getHeroLevel());
	}

	public static boolean isAwakenable(final CUnit hero, final int playerIndex) {
		if ((hero == null) || (hero.getPlayerIndex() != playerIndex) || !hero.isDead()) {
			return false;
		}
		final CAbilityHero heroData = hero.getHeroData();
		return (heroData != null) && heroData.isAwaitingRevive() && !heroData.isReviving();
	}

	private void checkCanAwaken(final CSimulation game, final CUnit tavern, final int playerIndex, final CUnit hero,
			final AbilityActivationReceiver receiver) {
		final CPlayer player = game.getPlayer(playerIndex);
		final CUnitType heroType = hero.getUnitType();
		if (!CAbilityNeutralBuilding.hasPatron(tavern, playerIndex)) {
			receiver.activationCheckFailed(CommandStringErrorKeys.A_VALID_PATRON_MUST_BE_NEARBY);
		}
		else if (player.getGold() < getGoldCost(game, hero)) {
			receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_GOLD);
		}
		else if (player.getLumber() < getLumberCost(game, hero)) {
			receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_LUMBER);
		}
		else if ((heroType.getFoodUsed() != 0)
				&& ((player.getFoodUsed() + heroType.getFoodUsed()) > player.getFoodCap())) {
			receiver.activationCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_FOOD);
		}
		else {
			receiver.useOk();
		}
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		if (isAwakenOrder(orderId)) {
			receiver.useOk();
			return;
		}
		final CUnit hero = game.getUnit(orderId);
		if (isAwakenable(hero, playerIndex)) {
			checkCanAwaken(game, unit, playerIndex, hero, receiver);
		}
		else {
			receiver.useOk();
		}
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit hero = target.visit(AbilityTargetVisitor.UNIT);
		if (isAwakenOrder(orderId) && isAwakenable(hero, playerIndex)) {
			receiver.targetOk(target);
		}
		else {
			receiver.orderIdNotAccepted();
		}
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
		if (isAwakenable(game.getUnit(orderId), playerIndex)) {
			receiver.targetOk(null);
		}
		else {
			receiver.orderIdNotAccepted();
		}
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		awaken(game, caster, playerIndex, target.visit(AbilityTargetVisitor.UNIT));
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		awaken(game, caster, playerIndex, game.getUnit(orderId));
		return null;
	}

	private void awaken(final CSimulation game, final CUnit tavern, final int playerIndex, final CUnit hero) {
		if (!isAwakenable(hero, playerIndex)) {
			return;
		}
		final BooleanAbilityActivationReceiver receiver = new BooleanAbilityActivationReceiver();
		checkCanAwaken(game, tavern, playerIndex, hero, receiver);
		if (!receiver.isOk()) {
			return;
		}
		final CPlayer player = game.getPlayer(playerIndex);
		if (!player.charge(getGoldCost(game, hero), getLumberCost(game, hero))) {
			return;
		}
		final CGameplayConstants gameplayConstants = game.getGameplayConstants();
		final CUnitType heroType = hero.getUnitType();
		hero.reviveHeroAt(game, tavern, gameplayConstants.getHeroAwakenLifeFactor(),
				gameplayConstants.getHeroAwakenManaFactor(), gameplayConstants.getHeroAwakenManaStart());
		player.setUnitFoodUsed(hero, heroType.getFoodUsed());
		player.setUnitFoodMade(hero, heroType.getFoodMade());
		game.heroReviveEvent(tavern, hero);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public <T> T visit(final CAbilityVisitor<T> visitor) {
		return visitor.accept(this);
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
}
