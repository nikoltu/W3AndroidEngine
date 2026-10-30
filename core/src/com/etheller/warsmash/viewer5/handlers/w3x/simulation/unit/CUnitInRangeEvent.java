package com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit;

import java.util.HashSet;
import java.util.Set;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/**
 * TriggerRegisterUnitInRange. Measured on 1.29: fires once when a unit comes within range and again only after it has
 * left; GetTriggerUnit is the coming unit and GetEventTargetUnit stays null.
 */
public final class CUnitInRangeEvent extends RemovableTriggerEvent {
	private final GlobalScope globalScope;
	private final CUnit center;
	private final float range;
	private final Trigger trigger;
	private final TriggerBooleanExpression filter;
	private final Set<CUnit> inRange = new HashSet<>();
	private final Set<CUnit> swap = new HashSet<>();
	private final CSimulation simulation;

	public CUnitInRangeEvent(final GlobalScope globalScope, final CSimulation simulation, final CUnit center,
			final float range, final Trigger trigger, final TriggerBooleanExpression filter) {
		super(trigger);
		this.globalScope = globalScope;
		this.simulation = simulation;
		this.center = center;
		this.range = range;
		this.trigger = trigger;
		this.filter = filter;
	}

	@Override
	public void remove() {
		this.simulation.removeUnitInRangeEvent(this);
	}

	public void update(final CSimulation game) {
		if (this.center.isDead() || this.center.isRemovedFromGame()) {
			this.inRange.clear();
			return;
		}
		this.swap.clear();
		game.getWorldCollision().enumUnitsInRange(this.center.getX(), this.center.getY(), this.range, (unit) -> {
			if ((unit != this.center) && !unit.isDead()) {
				this.swap.add(unit);
			}
			return false;
		});
		for (final CUnit unit : this.swap) {
			if (!this.inRange.contains(unit)) {
				final CommonTriggerExecutionScope filterScope = CommonTriggerExecutionScope
						.filterScope(com.etheller.interpreter.ast.scope.TriggerExecutionScope.EMPTY, unit);
				final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.simpleUnitScope(
						JassGameEventsWar3.EVENT_UNIT_TARGET_IN_RANGE, this.trigger, unit,
						game.getPlayer(unit.getPlayerIndex()));
				this.globalScope.queueTrigger(this.filter, filterScope, this.trigger, scope, scope);
			}
		}
		this.inRange.clear();
		this.inRange.addAll(this.swap);
	}
}
