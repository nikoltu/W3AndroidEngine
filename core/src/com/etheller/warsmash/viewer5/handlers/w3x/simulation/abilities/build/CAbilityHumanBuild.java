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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.CBehaviorHumanBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

public class CAbilityHumanBuild extends AbstractCAbilityRaceBuild {

	private CBehaviorHumanBuild buildBehavior;

	public CAbilityHumanBuild(final int handleId, final List<War3ID> structuresBuilt) {
		super(handleId, War3ID.fromString("AHbu"), structuresBuilt);
	}

	@Override
	protected AbstractCBehaviorBuild buildBehavior() {
		return this.buildBehavior;
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.humanbuild;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.buildBehavior = new CBehaviorHumanBuild(unit);
	}

	@Override
	public <T> T visit(final CAbilityVisitor<T> visitor) {
		return visitor.accept(this);
	}

}
