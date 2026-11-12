package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetStillAliveVisitor;

public class CBehaviorFollow extends CAbstractRangedBehavior {

	// Measured on 1.29: a smart order on an ally closes to this distance between the two centres, much nearer than
	// a move order onto it (FollowRange).
	private static final float SMART_ALLY_FOLLOW_RANGE = 136f;

	private int higlightOrderId;
	private boolean smart;

	public CBehaviorFollow(final CUnit unit) {
		super(unit);
	}

	public CBehavior reset(CSimulation game, final int higlightOrderId, final CWidget target) {
		return reset(game, higlightOrderId, target, false);
	}

	public CBehavior reset(CSimulation game, final int higlightOrderId, final CWidget target, final boolean smart) {
		this.higlightOrderId = higlightOrderId;
		this.smart = smart;
		return innerReset(game, target);
	}

	@Override
	public int getHighlightOrderId() {
		return this.higlightOrderId;
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		// Measured on 1.29: a unit following another never looks for targets on the way.
		// Measured on 1.29: a unit following one of its own or an ally stops FollowRange away; any other unit is
		// followed up to touching it.
		final boolean ally = (this.target instanceof CUnit)
				&& ((CUnit) this.target).isUnitAlly(simulation.getPlayer(this.unit.getPlayerIndex()));
		if (ally && this.smart) {
			return StrictMath.hypot(this.target.getX() - this.unit.getX(),
					this.target.getY() - this.unit.getY()) <= SMART_ALLY_FOLLOW_RANGE;
		}
		return this.unit.canReach(this.target, ally ? simulation.getGameplayConstants().getFollowRange() : 0f);
	}

	@Override
	protected CBehavior update(final CSimulation simulation, final boolean withinFacingWindow) {

		this.unit.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f, false);
		return this;
	}

	@Override
	protected CBehavior updateOnInvalidTarget(final CSimulation simulation) {
		this.unit.setDefaultBehavior(this.unit.getStopBehavior());
		return this.unit.pollNextOrderBehavior(simulation);
	}

	@Override
	protected boolean checkTargetStillValid(final CSimulation simulation) {
		return this.target.visit(AbilityTargetStillAliveVisitor.INSTANCE);
	}

	@Override
	protected void resetBeforeMoving(final CSimulation simulation) {

	}

	@Override
	public void begin(final CSimulation game) {

	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {

	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {

	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.MOVEMENT;
	}

}
