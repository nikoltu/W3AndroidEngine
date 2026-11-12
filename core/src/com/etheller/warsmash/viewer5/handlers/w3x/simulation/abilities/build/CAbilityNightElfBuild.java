package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.AbstractCBehaviorBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorNightElfBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

public class CAbilityNightElfBuild extends AbstractCAbilityRaceBuild {
	private CBehaviorNightElfBuild buildBehavior;

	public CAbilityNightElfBuild(final int handleId, final List<War3ID> structuresBuilt) {
		super(handleId, War3ID.fromString("AEbu"), structuresBuilt);
	}

	@Override
	protected AbstractCBehaviorBuild buildBehavior() {
		return this.buildBehavior;
	}

	@Override
	public <T> T visit(final CAbilityVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.buildBehavior = new CBehaviorNightElfBuild(unit);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.nightelfbuild;
	}
}
