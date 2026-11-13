package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

public class CFogModifierJassSingle extends AllocatedHandle implements CFogModifierJass {
	private final int playerIndex;
	private final CFogModifier modifier;

	public CFogModifierJassSingle(int playerIndex, CFogModifier modifier) {
		this.playerIndex = playerIndex;
		this.modifier = modifier;
	}

	@Override
	public void setEnabled(boolean flag) {
		this.modifier.setEnabled(flag);
	}

	@Override
	public void applyNow(final CSimulation game) {
		final CPlayer owner = game.getPlayer(this.playerIndex);
		this.modifier.applyNow(game, owner);
		// Measured on 1.29: the allies that share vision with the owner when it is made get its state once as well,
		// whatever the shared vision flag says.
		for (int i = 0, n = game.getGameData().getMaxPlayers(); i < n; i++) {
			if ((i != this.playerIndex) && (owner.hasAlliance(i, CAllianceType.SHARED_VISION)
					|| owner.hasAlliance(i, CAllianceType.SHARED_VISION_FORCED))) {
				this.modifier.applyNow(game, game.getPlayer(i));
			}
		}
	}

	@Override
	public void destroy(CSimulation game) {
		game.getPlayer(this.playerIndex).removeFogModifer(game, this.modifier);
	}

}
