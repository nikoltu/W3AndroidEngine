package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
import com.etheller.interpreter.ast.scope.variableevent.VariableEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

public class CPlayerStateEvent extends CPlayerEvent {
	private final CPlayerState state;
	private final VariableEvent limit;

	public CPlayerStateEvent(final GlobalScope globalScope, final CPlayerJass player, final Trigger trigger,
			final CPlayerState state, final CLimitOp limitOp, final double limitValue) {
		super(globalScope, player, trigger, JassGameEventsWar3.EVENT_PLAYER_STATE_LIMIT, null);
		this.state = state;
		this.limit = new VariableEvent(trigger, limitOp, limitValue);
	}

	public CPlayerState getState() {
		return this.state;
	}

	/**
	 * The event fires when the state starts to meet the limit, not on every change while it does.
	 */
	public boolean isCrossed(final double before, final double after) {
		return !this.limit.isMatching(before) && this.limit.isMatching(after);
	}
}
