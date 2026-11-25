package com.etheller.warsmash.viewer5.handlers.w3x;

import com.badlogic.gdx.utils.IntMap;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.AssetReadiness;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PeerQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEventPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldPeerData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;

/**
 * The render side of creating units, items and destructables when the simulation makes them: builds the peers from
 * what the simulation announced and sent with the creation events, in the order the objects were made, spread over
 * the frames by a time budget (see {@link PeerQueue}). Render thread, except where the queue says otherwise.
 */
final class MapViewerPeers implements RenderEventPlayer.WorldPeers, PeerQueue.Builder {
	private static final long BUDGET_NANOS = Long.getLong("warsmash.simthread.peerbudget", 2L) * 1_000_000L;
	private static final long MAX_WAIT_FRAMES = Long.getLong("warsmash.simthread.peermaxwait", 3L);
	/** With a value above 0, the summary is logged every that many frames. */
	private static final long STATS_EVERY = Long.getLong("warsmash.simthread.peerstats", 0L);

	private final War3MapViewer viewer;
	private final IntMap<CItem> items = new IntMap<>();
	private long frames;
	private final PeerQueue queue = new PeerQueue(this);

	MapViewerPeers(final War3MapViewer viewer) {
		this.viewer = viewer;
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

	/** The peer of the object with this handle exists when this returns, if the simulation announced the object. */
	void ensure(final int handle) {
		if (this.queue.isWaiting(handle)) {
			present(handle, null);
		}
	}

	CItem item(final int handle) {
		return this.items.get(handle);
	}

	@Override
	public void build(final int handle, final Object data) {
		if (data instanceof WorldPeerData.Unit) {
			final WorldPeerData.Unit unit = (WorldPeerData.Unit) data;
			MapViewerUnits.buildUnitPeer(this.viewer, unit);
			if (unit.creepLevel != WorldPeerData.NO_CREEP) {
				this.viewer.addInitialCreep(unit.requestedX, unit.requestedY, unit.creepLevel);
			}
		}
		else if (data instanceof WorldPeerData.Item) {
			final WorldPeerData.Item item = (WorldPeerData.Item) data;
			MapViewerUnits.buildItemPeer(this.viewer, item);
			this.items.put(handle, item.item);
		}
		else if (data instanceof WorldPeerData.Destructable) {
			MapViewerDoodads.buildRunTimeDestructablePeer(this.viewer, (WorldPeerData.Destructable) data);
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
		present(handle, typeId);
	}

	@Override
	public void itemCreated(final int handle, final War3ID typeId, final float x, final float y,
			final WorldPeerData.Item data) {
		present(handle, typeId);
	}

	@Override
	public void destructableCreated(final int handle, final War3ID typeId, final float x, final float y, final float z,
			final float facing, final float scale, final int variation, final WorldPeerData.Destructable data) {
		present(handle, typeId);
	}

	String summary() {
		return this.queue.summary() + " " + this.viewer.typePrewarm.feed().summary();
	}
}
