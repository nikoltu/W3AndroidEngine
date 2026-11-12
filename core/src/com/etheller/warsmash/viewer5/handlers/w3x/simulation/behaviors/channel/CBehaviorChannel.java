package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.channel;

import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedFormerly;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel.CAbilityChannel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

public class CBehaviorChannel implements CBehavior {
	private final CUnit unit;
	private int nextArtTick;
	@SavedFormerly("abilityChannelTest")
	private final CAbilityChannel abilityChannel;

	public CBehaviorChannel(final CUnit unit, final CAbilityChannel abilityChannel) {
		this.unit = unit;
		this.abilityChannel = abilityChannel;
	}

	public CBehaviorChannel reset() {
		this.nextArtTick = 0;
		return this;
	}

	@Override
	public CBehavior update(final CSimulation game) {
		this.unit.getUnitAnimationListener().playAnimation(false, null, SequenceUtils.SPELL, 1.0f, true);
		final int gameTurnTick = game.getGameTurnTick();
		if (gameTurnTick >= this.nextArtTick) {
			game.createTemporarySpellEffectOnUnit(this.unit, this.abilityChannel.getAlias(), CEffectType.CASTER);
			this.nextArtTick = gameTurnTick
					+ CTimer.wholeSteps(this.abilityChannel.getArtDuration());
		}
		return this;
	}

	@Override
	public void begin(final CSimulation game) {
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public int getHighlightOrderId() {
		return OrderIds.channel;
	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.SPELL;
	}

}
