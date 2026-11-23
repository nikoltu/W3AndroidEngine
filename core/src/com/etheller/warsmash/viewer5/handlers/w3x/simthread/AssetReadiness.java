package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.etheller.warsmash.util.War3ID;

/**
 * The simulation's word to the render side about which unit types are about to be needed, so that their models are
 * loaded before the first unit of the type has to be shown. The simulation thread offers types as it makes units
 * ({@link #offerUrgent}) and as production queues and builders on their way name them ({@link #offerPlanned}); the
 * render thread takes them in turn and loads them within its frame budget. Neither side waits for the other.
 */
public final class AssetReadiness {
	/** Off for measuring the render side without the simulation's offers, holding peers and the clock's wait. */
	public static final boolean ACTIVE = !"false".equals(System.getProperty("warsmash.simthread.assetfeed"));

	private final ConcurrentLinkedQueue<War3ID> urgent = new ConcurrentLinkedQueue<>();
	private final Set<War3ID> urgentQueued = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<War3ID> planned = new ConcurrentLinkedQueue<>();
	private final Set<War3ID> plannedEver = ConcurrentHashMap.newKeySet();
	private final Map<War3ID, String[]> projectileArt = new ConcurrentHashMap<>();
	private final AtomicLong urgentTaken = new AtomicLong();
	private final AtomicLong plannedTaken = new AtomicLong();
	private final Set<War3ID> coldTypes = new java.util.HashSet<>();
	private final AtomicLong coldPeers = new AtomicLong();

	/** Any thread: a unit of the type was just made, and its model may not be loaded. Cheap for a type already waiting. */
	public void offerUrgent(final War3ID typeId) {
		if (ACTIVE && (typeId != null) && this.urgentQueued.add(typeId)) {
			this.urgent.add(typeId);
		}
	}

	/** Any thread: a unit of the type will probably be made soon. Each type is taken once. */
	public void offerPlanned(final War3ID typeId) {
		if ((typeId != null) && this.plannedEver.add(typeId)) {
			this.planned.add(typeId);
		}
	}

	/** Whether the art the type's attacks launch was recorded; the render side may not read the simulation's types. */
	public boolean hasArt(final War3ID typeId) {
		return this.projectileArt.containsKey(typeId);
	}

	/** Simulation thread: the models the type's attacks launch. */
	public void setArt(final War3ID typeId, final String[] art) {
		this.projectileArt.put(typeId, art == null ? new String[0] : art);
	}

	public String[] art(final War3ID typeId) {
		final String[] art = this.projectileArt.get(typeId);
		return art == null ? new String[0] : art;
	}

	/** Render thread: the next type a unit was made of, or null. */
	public War3ID pollUrgent() {
		final War3ID typeId = this.urgent.poll();
		if (typeId != null) {
			this.urgentQueued.remove(typeId);
			this.urgentTaken.incrementAndGet();
		}
		return typeId;
	}

	/** Render thread: the next type expected soon, or null. */
	public War3ID pollPlanned() {
		final War3ID typeId = this.planned.poll();
		if (typeId != null) {
			this.plannedTaken.incrementAndGet();
		}
		return typeId;
	}

	/** Render thread: a peer was built with its model not loaded; the clock waits for the model. */
	public void noteCold(final War3ID typeId) {
		this.coldPeers.incrementAndGet();
		this.coldTypes.add(typeId);
	}

	/** Render thread: whether a unit made since is still waiting for its model, given what is loaded now. */
	public boolean anyColdTypeLoading(final java.util.function.Predicate<War3ID> ready) {
		this.coldTypes.removeIf(ready);
		return !this.coldTypes.isEmpty();
	}

	/** Render thread: whether a peer of the type was built before its model was loaded, and it is still loading. */
	public boolean isColdType(final War3ID typeId) {
		return this.coldTypes.contains(typeId);
	}

	public long coldPeers() {
		return this.coldPeers.get();
	}

	public String summary() {
		return "types urgent=" + this.urgentTaken.get() + " planned=" + this.plannedTaken.get() + " coldPeers=" + this.coldPeers.get();
	}
}
