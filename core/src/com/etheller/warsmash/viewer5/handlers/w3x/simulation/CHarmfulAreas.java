package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * The ground effects that hurt the units standing in them (Flame Strike, Blizzard), and the way a computer player's
 * units step out of them.
 *
 * Measured on 1.29: a unit of a computer player without an AI script that stands in such an area once it has hurt
 * for a moment walks 700 away, and does so again when it is still in it after it got there; it leaves away from the
 * caster of another player's spell, and goes on towards the centre of its own spell.
 */
public class CHarmfulAreas {
	private static final float ESCAPE_DISTANCE = 700;
	private static final int THINK_TICKS = CTimer.wholeSteps(1.0f / 3);

	private static final class Area {
		private final CUnit source;
		private final float x;
		private final float y;
		private final float radius;
		private final int hurtTick;
		private final int endTick;

		private Area(final CUnit source, final float x, final float y, final float radius, final int hurtTick,
				final int endTick) {
			this.source = source;
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.hurtTick = hurtTick;
			this.endTick = endTick;
		}
	}

	private static final class Escape {
		private int nextThinkTick;
		private float directionX;
		private float directionY;
		private boolean escaping;
	}

	private final List<Area> areas = new ArrayList<>();
	private final Map<CUnit, Escape> escapes = new HashMap<>();

	public void add(final CSimulation game, final CUnit source, final float x, final float y, final float radius,
			final float hurtDelay, final float duration) {
		final int tick = game.getGameTurnTick();
		this.areas.add(new Area(source, x, y, radius, tick + CTimer.wholeSteps(hurtDelay),
				tick + CTimer.wholeSteps(duration)));
	}

	/**
	 * Called every tick for a computer player's unit; orders it out of the area it stands in.
	 */
	public void stepOut(final CSimulation game, final CUnit unit) {
		if (this.areas.isEmpty()) {
			this.escapes.clear();
			return;
		}
		final int tick = game.getGameTurnTick();
		final Escape escape = this.escapes.get(unit);
		if ((escape != null) && (tick < escape.nextThinkTick)) {
			return;
		}
		Area inside = null;
		for (final Iterator<Area> iterator = this.areas.iterator(); iterator.hasNext();) {
			final Area area = iterator.next();
			if (tick >= area.endTick) {
				iterator.remove();
			}
			else if ((tick >= area.hurtTick) && (inside == null)
					&& ((StrictMath.hypot(unit.getX() - area.x, unit.getY() - area.y)
							- unit.getUnitType().getCollisionSize()) <= area.radius)) {
				inside = area;
			}
		}
		if (inside == null) {
			if (escape != null) {
				this.escapes.remove(unit);
			}
			return;
		}
		final CBehavior behavior = unit.getCurrentBehavior();
		if ((behavior != null) && ((behavior.getBehaviorCategory() == CBehaviorCategory.SPELL)
				|| (behavior.getBehaviorCategory() == CBehaviorCategory.BUILD))) {
			return;
		}
		final Escape current = (escape != null) ? escape : new Escape();
		this.escapes.put(unit, current);
		current.nextThinkTick = tick + THINK_TICKS;
		if (current.escaping && (behavior != null) && (behavior.getBehaviorCategory() == CBehaviorCategory.MOVEMENT)) {
			return;
		}
		if (!current.escaping) {
			current.escaping = true;
			float fromX = unit.getX() - inside.source.getX();
			float fromY = unit.getY() - inside.source.getY();
			if (inside.source == unit) {
				fromX = inside.x - unit.getX();
				fromY = inside.y - unit.getY();
			}
			final float length = (float) StrictMath.hypot(fromX, fromY);
			if (length < 1) {
				fromX = (float) StrictMath.cos(Math.toRadians(unit.getFacing()));
				fromY = (float) StrictMath.sin(Math.toRadians(unit.getFacing()));
			}
			else {
				fromX /= length;
				fromY /= length;
			}
			current.directionX = fromX;
			current.directionY = fromY;
		}
		unit.order(game, OrderIds.move, new AbilityPointTarget(unit.getX() + (current.directionX * ESCAPE_DISTANCE),
				unit.getY() + (current.directionY * ESCAPE_DISTANCE)), true);
	}
}
