package com.etheller.warsmash.viewer5;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.etheller.warsmash.viewer5.handlers.ModelHandler;

public abstract class Model<HANDLER extends ModelHandler> extends HandlerResource<HANDLER> {
	public final Bounds bounds;
	public List<ModelInstance> preloadedInstances;
	private boolean pending;
	/** Where the file of a pending model is read from, until a helper is started on it. */
	String lazySource;
	/** Whether the build of a pending model is done and its waiting instances are being loaded. */
	boolean built;
	int waitingLoaded;
	/** Whether a step of the model's build is running now. */
	boolean stepping;
	long buildNanos;
	/** The size of the file its helper has read, 0 until then. */
	private volatile long preparedFileBytes;
	/** How many times the viewer was asked for this model, and how many of those asked on behalf of a releasable holder. */
	private int loadRequests;
	private int holderLoads;
	/** The releasable holders (see {@link #claim}) that have it now. */
	private int holders;
	/** Instances made before the model's instances were followed; such a model is never let go. */
	private int untrackedInstances;
	/** The instances made since they are followed, which stop counting once nothing refers to them. */
	private List<WeakReference<ModelInstance>> trackedInstances;
	private boolean instanceMade;
	private int pruneAt = 64;

	public Model(final HANDLER handler, final ModelViewer viewer, final String extension, final PathSolver pathSolver,
			final String fetchUrl) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
		this.bounds = new Bounds();
		this.preloadedInstances = new ArrayList<>();
	}

	protected abstract ModelInstance createInstance(int type);

	public ModelInstance addInstance() {
		return addInstance(0);
	}

	/** Finishes a model whose file is still being read first, see {@link #addWaitingInstance()}. */
	public ModelInstance addInstance(final int type) {
		finishPending();
		return addUnfinishedInstance(type);
	}

	/**
	 * An instance that may be made before the model's file is read: until then it shows nothing, and it fills in when
	 * the model is built (see {@link ModelViewer#setDeferModelLoads}). Only an owner that keeps no part of the model's
	 * contents from before then may use it.
	 */
	public ModelInstance addWaitingInstance() {
		return addUnfinishedInstance(0);
	}

	/**
	 * An instance of a built model that is made empty and only loads (takes its nodes, matrices and emitters) once the
	 * scene first has it in view, so what stands out of sight all game costs little more than its place. Whoever makes
	 * one handles it as a waiting instance (see {@link ModelInstance#isWaiting()}); that holds for one of a model that
	 * is still being built too, which is not loaded when the model is built either.
	 */
	public ModelInstance addLazyInstance() {
		if (!this.ok && !this.pending) {
			return addUnfinishedInstance(0);
		}
		final ModelInstance instance = createInstance(0);
		followInstance(instance);
		instance.setLazyLoad();
		if (!this.ok) {
			this.preloadedInstances.add(instance);
		}
		return instance;
	}

	private ModelInstance addUnfinishedInstance(final int type) {
		final ModelInstance instance = createInstance(type);
		followInstance(instance);

		if (this.ok) {
			instance.load();
		}
		else {
			this.preloadedInstances.add(instance);
			if (this.pending) {
				// also one asked for by what the model's own build makes, which cannot wait for the build: it loads
				// with the others, and no scene may show it before
				instance.setWaiting(true);
			}
		}

		return instance;
	}

	private synchronized void followInstance(final ModelInstance instance) {
		if (this.trackedInstances == null) {
			this.untrackedInstances++;
			return;
		}
		if (this.trackedInstances.size() >= this.pruneAt) {
			pruneInstances();
			this.pruneAt = Math.max(64, this.trackedInstances.size() * 2);
		}
		this.trackedInstances.add(new WeakReference<>(instance));
		this.instanceMade = true;
	}

	private void pruneInstances() {
		for (final Iterator<WeakReference<ModelInstance>> it = this.trackedInstances.iterator(); it.hasNext();) {
			if (it.next().get() == null) {
				it.remove();
			}
		}
	}

	/** Counts a request of the viewer for this model; one that comes from a holder is then {@link #claim}ed. */
	synchronized void requested() {
		this.loadRequests++;
	}

	/**
	 * Marks the model as held by something that can let go of it (see {@link #release}) and answers for the load it
	 * just asked of the viewer. From the first claim on, the instances made of the model are followed.
	 */
	public synchronized void claim() {
		this.holderLoads++;
		this.holders++;
		if (this.trackedInstances == null) {
			this.trackedInstances = new ArrayList<>();
		}
	}

	/** Lets go of one claim; true when nothing else holds the model and it may be evicted if it is idle. */
	public synchronized boolean release() {
		this.holders--;
		return this.holders <= 0;
	}

	/**
	 * Whether the model is built and nothing but claims ever asked for it or made an instance of it, so that when no
	 * instance of it is alive it can be rebuilt from its file by whoever claimed it.
	 */
	public synchronized boolean isReleasable() {
		return this.ok && !this.pending && !this.stepping && (this.trackedInstances != null)
				&& (this.untrackedInstances == 0) && (this.loadRequests == this.holderLoads)
				&& !"".equals(this.fetchUrl);
	}

	/** Whether an instance of the model was made since it was claimed: a model only loaded ahead of its first unit was not shown yet. */
	public synchronized boolean hasBeenShown() {
		return this.instanceMade;
	}

	/** Whether an instance of the model that is still referred to exists, or one made when it was not followed. */
	public synchronized boolean hasLiveInstances() {
		if ((this.trackedInstances == null) || (this.untrackedInstances > 0)) {
			return true;
		}
		pruneInstances();
		return !this.trackedInstances.isEmpty() || !this.preloadedInstances.isEmpty();
	}

	/**
	 * Whether the model's file is being read and parsed on a helper thread, or the model is built a few units at a time
	 * or has instances still to load: {@link #finishPending()} completes all of that.
	 */
	public boolean isPending() {
		return this.pending;
	}

	/** Builds a pending model now, as far as it is not built, waiting for its helper if need be; nothing for any other model. */
	public final void finishPending() {
		if (this.pending) {
			this.viewer.finishPendingModel(this);
		}
	}

	void setPending(final boolean pending) {
		this.pending = pending;
	}

	/**
	 * Starts reading and parsing the file on a helper thread instead of loading it now.
	 *
	 * @return false when the model is to be loaded from the stream now as usual; the stream is then untouched
	 */
	protected boolean prepareLater(final InputStream src, final ExecutorService executor) {
		return false;
	}

	/** Called by the helper once it has read the file, which tells the viewer how much its result holds. */
	protected void setPreparedFileBytes(final long bytes) {
		this.preparedFileBytes = Math.max(1, bytes);
	}

	long getPreparedFileBytes() {
		return this.preparedFileBytes;
	}

	/** Whether the helper of a pending model is done, so that building it does not wait. */
	protected boolean isPrepared() {
		return true;
	}

	/**
	 * Builds the model from what its helper made, on the game thread, a unit of work at a time until the time is up
	 * (at least one unit); sets ok as loading the file would when it is done.
	 *
	 * @return true when the model is built or failed, false when the next call goes on
	 */
	protected boolean buildPrepared(final long endNanos) {
		return true;
	}

	/**
	 * A model that is still being built and that this one's instances need as they load, or null. A pending model's
	 * waiting instances are loaded once it has none.
	 */
	protected Model<?> firstPendingRequired() {
		return null;
	}

	/** Whether this model's build, or the loading of its waiting instances, is running now. */
	protected final boolean isStepping() {
		return this.stepping;
	}

	/** Frees what was made ahead for a model that is not built after all. */
	protected void discardPrepared() {
	}
}
