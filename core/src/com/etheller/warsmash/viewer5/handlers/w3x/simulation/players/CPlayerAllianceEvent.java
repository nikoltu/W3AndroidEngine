package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

public class CPlayerAllianceEvent extends CPlayerEvent {
	private final CAllianceType allianceType;

	public CPlayerAllianceEvent(final GlobalScope globalScope, final CPlayerJass player, final Trigger trigger,
			final CAllianceType allianceType) {
		super(globalScope, player, trigger, JassGameEventsWar3.EVENT_PLAYER_ALLIANCE_CHANGED, null);
		this.allianceType = allianceType;
	}

	public CAllianceType getAllianceType() {
		return this.allianceType;
	}
}
