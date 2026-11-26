package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayDeque;

import com.badlogic.gdx.utils.IntMap;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.AssetReadiness;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PeerQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEventPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldPeerData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * The render side of creating units, items and destructables when the simulation makes them: builds the peers from
 * what the simulation announced and sent with the creation events, in the order the objects were made, spread over
 * the frames by a time budget (see {@link PeerQueue}). Render thread, except where the queue says otherwise.
 * <p>
 * A handle is given out again once its object is gone, and a peer is built before the events of the object that held
 * the handle before are played. So the handle names an object the simulation made only from its creation event on;
 * until then the object waits here, in the order of its handle's objects.
 */
final class MapViewerPeers implements RenderEventPlayer.WorldPeers, PeerQueue.Builder {
	private static final long BUDGET_NANOS = Long.getLong("warsmash.simthread.peerbudget", 2L) * 1_000_000L;
	private static final long MAX_WAIT_FRAMES = Long.getLong("warsmash.simthread.peermaxwait", 3L);
	/** With a value above 0, the summary is logged every that many frames. */
	private static final long STATS_EVERY = Long.getLong("warsmash.simthread.peerstats", 0L);

	private final War3MapViewer viewer;
	private final MapViewerSimThread simThread;
	/** Items by handle, from their creation event (or, the map's own, from the first frame) to their removal. */
	private final IntMap<CItem> items = new IntMap<>();
	/** Per handle, the objects whose peer is built and whose creation event is not played yet, earliest first. */
	private final IntMap<ArrayDeque<Object>> unplayed = new IntMap<>();
	private boolean placedItemsNoted;
	private long frames;
	private final PeerQueue queue = new PeerQueue(this);

	MapViewerPeers(final War3MapViewer viewer, final MapViewerSimThread simThread) {
		this.viewer = viewer;
		this.simThread = simThread;
	}

	PeerQueue queue() {
		return this.queue;
	}

	/** Start of a render frame: peers of objects the simulation made since get built, within the budget. */
	void beginFrame() {
		this.queue.beginFrame(BUDGET_NANOS, MAX_WAIT_FRAMES);
		if ((STATS_EVERY > 0) && ((++this.frames % STATS_EVERY) == 0)) {
			Log.info(summary());
		}
	}

	/** Whether the units made since wait for models that are loading. */
	boolean waitingForModels() {
		return AssetReadiness.ACTIVE && (this.queue.waitingForFiles() || this.viewer.typePrewarm.feed()
				.anyColdTypeLoading(typeId -> this.viewer.renderUnitTypeData.isModelReady(typeId)));
	}

	/** Before the render thread does something that needs the peers of everything made so far. */
	void flush() {
		this.queue.flush();
	}

	/**
	 * The object an event names by a handle that no played creation event gave it: the earliest object made with the
	 * handle whose creation event is still to come (its peer is built now if need be), or null. Events are played in
	 * the order they were made, so an event that comes before that creation event and after the removal of the
	 * handle's previous object is about this one.
	 */
	Object beforeCreation(final int handle) {
		final ArrayDeque<Object> built = this.unplayed.get(handle);
		if ((built == null) && this.queue.isWaiting(handle)) {
			present(handle, null);
			return first(this.unplayed.get(handle));
		}
		return first(built);
	}

	private static Object first(final ArrayDeque<Object> objects) {
		return objects == null ? null : objects.peekFirst();
	}

	CItem item(final int handle) {
		return this.items.get(handle);
	}

	/**
	 * Render thread, before the first frame is played: the map's own items, whose peers were built with the map and
	 * not from creation events, are named by their handles too.
	 */
	void notePlacedItems() {
		if (this.placedItemsNoted) {
			return;
		}
		this.placedItemsNoted = true;
		for (final CItem item : this.viewer.itemToRenderPeer.keySet()) {
			final int handle = item.getHandleId();
			final ArrayDeque<Object> built = this.unplayed.get(handle);
			if (((built == null) || !built.contains(item)) && !this.items.containsKey(handle)) {
				this.items.put(handle, item);
			}
		}
	}

	/** A saved game was loaded into the map: its items got new peers, which name their handles from the next frame. */
	void itemPeersRebuilt() {
		this.items.clear();
		this.placedItemsNoted = false;
	}

	/** The item's removal was played: its handle names no item until the next one's creation event. */
	void itemRemoved(final int handle, final CItem item) {
		if (this.items.get(handle) == item) {
			this.items.remove(handle);
		}
	}

	private void built(final int handle, final Object object) {
		ArrayDeque<Object> objects = this.unplayed.get(handle);
		if (objects == null) {
			objects = new ArrayDeque<>(2);
			this.unplayed.put(handle, objects);
		}
		objects.addLast(object);
	}

	/**
	 * A creation event was played: its object, the earliest built for the handle, leaves the waiting ones.
	 *
	 * @param named the object the event carried, or null when it had none
	 */
	private Object created(final int handle, final Object named) {
		final ArrayDeque<Object> objects = this.unplayed.get(handle);
		final Object object = objects == null ? null : objects.pollFirst();
		if ((objects != null) && objects.isEmpty()) {
			this.unplayed.remove(handle);
		}
		if ((named != null) && (object != named)) {
			throw new IllegalStateException("Creation event of handle " + handle + " for " + named
					+ " found " + object + " first among the objects made with it");
		}
		return object;
	}

	@Override
	public void build(final int handle, final Object data) {
		if (data instanceof WorldPeerData.Unit) {
			final WorldPeerData.Unit unit = (WorldPeerData.Unit) data;
			MapViewerUnits.buildUnitPeer(this.viewer, unit);
			built(handle, unit.unit);
			if (unit.creepLevel != WorldPeerData.NO_CREEP) {
				this.viewer.addInitialCreep(unit.requestedX, unit.requestedY, unit.creepLevel);
			}
		}
		else if (data instanceof WorldPeerData.Item) {
			final WorldPeerData.Item item = (WorldPeerData.Item) data;
			MapViewerUnits.buildItemPeer(this.viewer, item);
			built(handle, item.item);
		}
		else if (data instanceof WorldPeerData.Destructable) {
			final WorldPeerData.Destructable destructable = (WorldPeerData.Destructable) data;
			MapViewerDoodads.buildRunTimeDestructablePeer(this.viewer, destructable);
			built(handle, destructable.destructable);
		}
	}

	@Override
	public boolean ready(final Object data) {
		return !(data instanceof WorldPeerData.Unit) || (this.viewer.renderUnitTypeData == null)
				|| this.viewer.renderUnitTypeData.isModelReady(((WorldPeerData.Unit) data).typeId);
	}

	@Override
	public void builtCold(final Object data) {
		if (data instanceof WorldPeerData.Unit) {
			this.viewer.typePrewarm.feed().noteCold(((WorldPeerData.Unit) data).typeId);
		}
	}

	private void present(final int handle, final War3ID typeId) {
		// built ahead with its model still loading counts as late as one built now
		final boolean late = this.queue.present(handle) || ((typeId != null) && (this.viewer.renderUnitTypeData != null)
				&& this.viewer.typePrewarm.feed().isColdType(typeId)
				&& !this.viewer.renderUnitTypeData.isModelReady(typeId));
		// the opening steps play behind the loading screen, where no unit is shown yet
		if (late && (this.viewer.getPresentedTick() > 0)) {
			PerfStats.latePeer(typeId == null ? "handle " + handle : typeId.toString());
		}
	}

	@Override
	public void unitCreated(final int handle, final War3ID typeId, final int playerIndex, final float x, final float y,
			final float facing, final WorldPeerData.Unit data) {
		if (handle == 0) {
			return;
		}
		present(handle, typeId);
		final Object unit = created(handle, data == null ? null : data.unit);
		if (unit instanceof CUnit) {
			this.simThread.unitMade(handle, (CUnit) unit);
		}
	}

	@Override
	public void itemCreated(final int handle, final War3ID typeId, final float x, final float y,
			final WorldPeerData.Item data) {
		if (handle == 0) {
			return;
		}
		present(handle, typeId);
		final Object item = created(handle, data == null ? null : data.item);
		if (item instanceof CItem) {
			this.items.put(handle, (CItem) item);
		}
	}

	@Override
	public void destructableCreated(final int handle, final War3ID typeId, final float x, final float y, final float z,
			final float facing, final float scale, final int variation, final WorldPeerData.Destructable data) {
		if (handle == 0) {
			return;
		}
		present(handle, typeId);
		final Object destructable = created(handle, data == null ? null : data.destructable);
		if (destructable instanceof CDestructable) {
			this.simThread.destructableMade(handle, (CDestructable) destructable);
		}
	}

	String summary() {
		return this.queue.summary() + " items=" + this.items.size + " unplayed=" + this.unplayed.size + " "
				+ this.viewer.typePrewarm.feed().summary();
	}
}
