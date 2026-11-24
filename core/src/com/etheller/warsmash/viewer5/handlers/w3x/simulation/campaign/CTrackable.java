package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/**
 * A model a script puts on the map that the player can point at (track) and click (hit). It is never destroyed and
 * cannot be moved; it takes the next handle id.
 */
public final class CTrackable extends AllocatedHandle {
	private final String modelPath;
	private final float x;
	private final float y;
	private final float facing;
	/** Null, one trigger, or a list of them: a script may make millions of trackables, most with one trigger. */
	private Object hitTriggers;
	private Object trackTriggers;

	public CTrackable(final String modelPath, final float x, final float y, final float facing) {
		this.modelPath = modelPath == null ? "" : modelPath.intern();
		this.x = x;
		this.y = y;
		this.facing = facing;
	}

	public String getModelPath() {
		return this.modelPath;
	}

	public float getX() {
		return this.x;
	}

	public float getY() {
		return this.y;
	}

	/** In degrees, as the script gave it. */
	public float getFacing() {
		return this.facing;
	}

	public RemovableTriggerEvent addHitEvent(final Trigger trigger) {
		this.hitTriggers = with(this.hitTriggers, trigger);
		return new EventRegistration(trigger, this, true);
	}

	public RemovableTriggerEvent addTrackEvent(final Trigger trigger) {
		this.trackTriggers = with(this.trackTriggers, trigger);
		return new EventRegistration(trigger, this, false);
	}

	@SuppressWarnings("unchecked")
	private static Object with(final Object triggers, final Trigger trigger) {
		if (triggers == null) {
			return trigger;
		}
		if (triggers instanceof Trigger) {
			final List<Trigger> list = new ArrayList<>(2);
			list.add((Trigger) triggers);
			list.add(trigger);
			return list;
		}
		((List<Trigger>) triggers).add(trigger);
		return triggers;
	}

	@SuppressWarnings("unchecked")
	private static Object without(final Object triggers, final Trigger trigger) {
		if (triggers == trigger) {
			return null;
		}
		if (triggers instanceof List) {
			final List<Trigger> list = (List<Trigger>) triggers;
			list.remove(trigger);
			return list.isEmpty() ? null : triggers;
		}
		return triggers;
	}

	private static final class EventRegistration extends RemovableTriggerEvent {
		private final Trigger trigger;
		private final CTrackable trackable;
		private final boolean hit;

		private EventRegistration(final Trigger trigger, final CTrackable trackable, final boolean hit) {
			super(trigger);
			this.trigger = trigger;
			this.trackable = trackable;
			this.hit = hit;
		}

		@Override
		public void remove() {
			if (this.hit) {
				this.trackable.hitTriggers = without(this.trackable.hitTriggers, this.trigger);
			}
			else {
				this.trackable.trackTriggers = without(this.trackable.trackTriggers, this.trigger);
			}
		}
	}

	/** A player clicked it, through that player's commands. */
	public void hit(final GlobalScope globalScope) {
		fire(globalScope, this.hitTriggers, JassGameEventsWar3.EVENT_GAME_TRACKABLE_HIT);
	}

	/** A player's pointer came onto it, through that player's commands. */
	public void track(final GlobalScope globalScope) {
		fire(globalScope, this.trackTriggers, JassGameEventsWar3.EVENT_GAME_TRACKABLE_TRACK);
	}

	@SuppressWarnings("unchecked")
	private void fire(final GlobalScope globalScope, final Object triggers, final JassGameEventsWar3 event) {
		if (triggers == null) {
			return;
		}
		final List<Trigger> copy = triggers instanceof Trigger ? Collections.singletonList((Trigger) triggers)
				: new ArrayList<>((List<Trigger>) triggers);
		for (final Trigger trigger : copy) {
			final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.trackableScope(event, trigger, this);
			globalScope.queueTrigger(null, null, trigger, scope, scope);
		}
	}

}
