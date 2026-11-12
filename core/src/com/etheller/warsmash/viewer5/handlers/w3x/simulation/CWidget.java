package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Set;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.interpreter.ast.util.CExtensibleHandleAbstract;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * As in 1.29, the id of a removed widget waits while a script variable or hashtable entry still points at it, and a
 * local that pointed at it when its function returned keeps it for good (measured).
 */
public abstract class CWidget extends CExtensibleHandleAbstract implements AbilityTarget, CAllocatedHandle {
	protected static final Rectangle tempRect = new Rectangle();
	private final int handleId;
	private boolean handleReleased;
	private boolean handleIdKept;
	private int referenceCount;
	private int[] poolImageIds;
	private int[] poolUbersplatIds;
	private float x;
	private float y;
	protected float life;
	private final EnumMap<JassGameEventsWar3, List<CWidgetEvent>> eventTypeToEvents = new EnumMap<>(
			JassGameEventsWar3.class);

	public CWidget(final int handleId, final float x, final float y, final float life) {
		this.handleId = handleId;
		this.x = x;
		this.y = y;
		this.life = life;
	}

	@Override
	public int getHandleId() {
		return this.handleId;
	}

	/** Widgets are numbered when they are made. */
	@Override
	public void setHandleId(final int handleId) {
	}

	@Override
	public boolean releaseHandle() {
		if (this.handleReleased) {
			return false;
		}
		this.handleReleased = true;
		return true;
	}

	@Override
	public void keepHandleId() {
		this.handleIdKept = true;
	}

	@Override
	public boolean isHandleIdKept() {
		return this.handleIdKept;
	}

	@Override
	public int changeReferenceCount(final int delta) {
		this.referenceCount += delta;
		return this.referenceCount;
	}

	@Override
	public int getReferenceCount() {
		return this.referenceCount;
	}

	/** Image pool numbers of its shadow while it lives. */
	public void setShadowPoolIds(final int[] imageIds) {
		this.poolImageIds = imageIds;
	}

	public boolean hasShadowPoolIds() {
		return this.poolImageIds != null;
	}

	/** Image and ubersplat pool numbers of its ground texture while it exists. */
	public void setUbersplatPoolIds(final int imageId, final int ubersplatId) {
		this.poolUbersplatIds = new int[] { imageId, ubersplatId };
	}

	/** The first number taken comes back last, so the next image takes it first (measured on 1.29). */
	public void freeShadowPoolIds(final HandleIdAllocator allocator) {
		if (this.poolImageIds != null) {
			for (int i = this.poolImageIds.length - 1; i >= 0; i--) {
				allocator.freeImageId(this.poolImageIds[i]);
			}
			this.poolImageIds = null;
		}
	}

	public void freePoolIds(final HandleIdAllocator allocator) {
		freeShadowPoolIds(allocator);
		if (this.poolUbersplatIds != null) {
			allocator.freeImageId(this.poolUbersplatIds[0]);
			allocator.freeUbersplatId(this.poolUbersplatIds[1]);
			this.poolUbersplatIds = null;
		}
	}

	@Override
	public float getX() {
		return this.x;
	}

	@Override
	public float getY() {
		return this.y;
	}

	public float getLife() {
		return this.life;
	}

	public abstract float getMaxLife();

	protected void setX(final float x) {
		this.x = x;
	}

	protected void setY(final float y) {
		this.y = y;
	}

	public void setLife(final CSimulation simulation, final float life) {
		this.life = life;
	}

	public abstract float damage(final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage);

	public abstract float damage(final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage, final float bonusDamage);

	public abstract float getFlyHeight();

	public abstract float getImpactZ();

	public abstract <T> T visit(CWidgetVisitor<T> visitor);

	public boolean isDead() {
		return this.life <= 0;
	}

	public abstract boolean canBeTargetedBy(CSimulation simulation, CUnit source,
			final Set<CTargetType> targetsAllowed, AbilityTargetCheckReceiver<CWidget> receiver);

	public boolean canBeTargetedBy(final CSimulation simulation, final CUnit source,
			final Set<CTargetType> targetsAllowed) {
		return canBeTargetedBy(simulation, source, targetsAllowed,
				BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset());
	}

	public double distanceSquaredNoCollision(final AbilityTarget target) {
		return distanceSquaredNoCollision(target.getX(), target.getY());
	}

	public double distanceSquaredNoCollision(final float targetX, final float targetY) {
		final double dx = targetX - getX();
		final double dy = targetY - getY();
		return (dx * dx) + (dy * dy);
	}

	public abstract boolean isInvulnerable();

	public void fireDeathEvents(final CSimulation simulation) {
		fireEvents(CommonTriggerExecutionScope::widgetTriggerScope, JassGameEventsWar3.EVENT_WIDGET_DEATH);
	}

	private List<CWidgetEvent> getOrCreateEventList(final JassGameEventsWar3 eventType) {
		List<CWidgetEvent> playerEvents = this.eventTypeToEvents.get(eventType);
		if (playerEvents == null) {
			playerEvents = new ArrayList<>();
			this.eventTypeToEvents.put(eventType, playerEvents);
		}
		return playerEvents;
	}

	protected List<CWidgetEvent> getEventList(final JassGameEventsWar3 eventType) {
		return this.eventTypeToEvents.get(eventType);
	}

	public RemovableTriggerEvent addEvent(final GlobalScope globalScope, final Trigger whichTrigger,
			final JassGameEventsWar3 eventType) {
		final CWidgetEvent playerEvent = new CWidgetEvent(globalScope, this, whichTrigger, eventType, null);
		getOrCreateEventList(eventType).add(playerEvent);
		return playerEvent;
	}

	public void addEvent(final CWidgetEvent playerEvent) {
		getOrCreateEventList(playerEvent.getEventType()).add(playerEvent);
	}

	public void removeEvent(final CWidgetEvent playerEvent) {
		final List<CWidgetEvent> eventList = getEventList(playerEvent.getEventType());
		if (eventList != null) {
			eventList.remove(playerEvent);
		}
	}

	private void fireEvents(final CommonTriggerExecutionScope.WidgetEventScopeBuilder eventScopeBuilder,
			final JassGameEventsWar3 eventType) {
		final List<CWidgetEvent> eventList = getEventList(eventType);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				// okay if it removes self from this during this because of reverse
				// iteration order
				final CWidgetEvent event = eventList.get(i);
				event.fire(this, eventScopeBuilder.create(eventType, event.getTrigger(), this));
			}
		}
	}

	public RemovableTriggerEvent addDeathEvent(final GlobalScope globalScope, final Trigger whichTrigger) {
		return addEvent(globalScope, whichTrigger, JassGameEventsWar3.EVENT_WIDGET_DEATH);
	}

	public abstract double distance(float x, float y);
}
