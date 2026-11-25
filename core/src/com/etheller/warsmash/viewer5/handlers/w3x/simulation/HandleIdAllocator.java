package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.etheller.interpreter.ast.scope.HandleIdSource;
import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.TextTag;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedAddedFields;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ScriptHandleTable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentUbersplat;

/**
 * Numbers game objects. Script handles get ids from a handle table that works like the one of 1.29: one counter for
 * every handle type from 0x100000, and the id of a destroyed handle is given again. A freed id waits until the end of
 * the simulation step, then the last one freed is the first one given out. As in 1.29, the id of a destroyed handle
 * waits while a global, a local, an array slot or a hashtable entry still points at it, and a local that still pointed
 * at it when its function returned keeps it for good (measured).
 *
 * Abilities and buffs are not handles in 1.29 and take no ids from the table; they are numbered by a counter of their
 * own.
 */
public class HandleIdAllocator implements SavedAddedFields, HandleIdSource {
	public static final int FIRST_HANDLE_ID = 0x100000;
	/** Where the single counter of saves from before the handle table started. Map destructables were made first. */
	public static final int LEGACY_FIRST_ID = 3412532;
	/** Keeps the ability counter clear of handle ids after a save from before the handle table is loaded. */
	private static final int LEGACY_ABILITY_ID_OFFSET = 0x1000000;
	private static final int TEXT_TAG_COUNT = 100;
	private static final int GIVEN_BACK = Integer.MIN_VALUE / 2;
	/** A handle of a save from before its kind had numbers of its own: it is numbered in the handle table. */
	public static final int UNNUMBERED = Integer.MIN_VALUE;
	private static final int TABLE = 0;
	private static final int TEXT_TAGS = 1;
	private static final int LIGHTNINGS = 2;
	private static final int IMAGES = 3;
	private static final int UBERSPLATS = 4;

	private int next = LEGACY_FIRST_ID;
	private int nextHandleId = FIRST_HANDLE_ID;
	private int[] freeHandleIds = new int[64];
	private int freeHandleIdCount;
	/** Freed this step, in order: ids, or destroyed handles whose id is freed unless a local variable kept it. */
	private final List<Object> releasedHandles = new ArrayList<>();
	/** Destroyed handles whose ids wait for the last script reference to them to go. */
	private final List<CAllocatedHandle> referencedHandles = new ArrayList<>();
	private int nextLightningId = 1;
	private int[] freeTextTagIds = allTextTagIds();
	private int freeTextTagIdCount = TEXT_TAG_COUNT;
	/**
	 * Handles of types without an id field of their own (and ones from saves made before they had one): numbered the
	 * first time a script asks, in that order.
	 */
	private final List<UntrackedHandle> untrackedHandles = new ArrayList<>();
	/**
	 * The handles scripts got the id of, to give them back when a script reads an integer as a handle. An entry goes
	 * when its id is freed, so it always names the handle that holds the id; by pool and id.
	 */
	private ScriptHandleTable scriptHandleTable;
	/** The map of saves from before the table above: its entries move into it when a script next asks. */
	private Map<Long, Object> scriptHandles;
	@SavedTransient
	private Consumer<Object> releaseListener;
	private int nextEarlyId;
	private int earlyIdEnd;
	private boolean earlyIds;
	/**
	 * Images and ubersplats are numbered from 0 in pools of their own, apart from the handle table; a freed number is
	 * the next one given out, at once (measured on 1.29).
	 */
	private int nextImageId;
	private final List<Integer> freeImageIds = new ArrayList<>();
	private int nextUbersplatId;
	private final List<Integer> freeUbersplatIds = new ArrayList<>();

	/** An id for an ability or buff. */
	public int createId() {
		return this.next++;
	}

	/** An id from the handle table. */
	public int createHandleId() {
		if (this.earlyIds && (this.nextEarlyId < this.earlyIdEnd)) {
			return this.nextEarlyId++;
		}
		if (this.freeHandleIdCount > 0) {
			return this.freeHandleIds[--this.freeHandleIdCount];
		}
		return this.nextHandleId++;
	}

	/** Gives the handle its id from the table, unless it has one. */
	public <T extends CAllocatedHandle> T assign(final T handle) {
		if (handle.getHandleId() == 0) {
			handle.setHandleId(createHandleId());
		}
		return handle;
	}

	/**
	 * Leaves ids out of the table for handles the original game makes before the ones made next. In 1.29 these are
	 * the handles the global initializers of the scripts make; see {@link #setEarlyIds}.
	 */
	public void skipHandleIds(final int count) {
		this.nextEarlyId = this.nextHandleId;
		this.nextHandleId += count;
		this.earlyIdEnd = this.nextHandleId;
	}

	/** While on, new handles take the ids left out by {@link #skipHandleIds} first. */
	public void setEarlyIds(final boolean earlyIds) {
		this.earlyIds = earlyIds;
	}

	/** The id goes back to the table at the end of the step. */
	public void freeHandleId(final int handleId) {
		if (handleId > 0) {
			this.releasedHandles.add(handleId);
		}
	}

	/**
	 * For a destroyed handle: its id goes back to the table once, however often a script destroys it, and not if a
	 * local variable kept it by the end of the step.
	 */
	public void release(final CAllocatedHandle handle) {
		if ((handle != null) && handle.releaseHandle()) {
			if (this.releaseListener != null) {
				this.releaseListener.accept(handle);
			}
			if (handle.getHandleId() > 0) {
				this.releasedHandles.add(handle);
			}
		}
	}

	/** Tells the listener of each handle that is destroyed, once; a script that holds handles lets go of them. */
	public void setReleaseListener(final Consumer<Object> listener) {
		this.releaseListener = listener;
	}

	/**
	 * A script let go of a destroyed handle that no other id will name: the table of handles scripts got ids of, and the
	 * numbering of handles that have no id field, let go of it too.
	 */
	public void forgetScriptHandle(final Object handle) {
		int handleId = 0;
		if (handle instanceof CHandle) {
			handleId = ((CHandle) handle).getHandleId();
		}
		if ((handleId == 0) || (handleId == UNNUMBERED)) {
			for (int i = 0; i < this.untrackedHandles.size(); i++) {
				if (this.untrackedHandles.get(i).handle == handle) {
					handleId = this.untrackedHandles.remove(i).handleId;
					break;
				}
			}
		}
		if ((handleId != 0) && (handleId != UNNUMBERED) && (scriptHandles().get(poolOf(handle), handleId) == handle)) {
			scriptHandles().remove(poolOf(handle), handleId);
		}
	}

	/** At the end of a simulation step: the ids freed during it can be given again, the last one first. */
	public void endStep() {
		for (int i = 0; i < this.referencedHandles.size(); i++) {
			final CAllocatedHandle handle = this.referencedHandles.get(i);
			if (handle.isHandleIdKept()) {
				this.referencedHandles.remove(i--);
			}
			else if (handle.getReferenceCount() <= 0) {
				this.referencedHandles.remove(i--);
				giveBack(handle);
			}
		}
		if (this.releasedHandles.isEmpty()) {
			return;
		}
		for (final Object released : this.releasedHandles) {
			if (released instanceof Integer) {
				pushFreeId((Integer) released);
			}
			else if (released instanceof CAllocatedHandle) {
				final CAllocatedHandle handle = (CAllocatedHandle) released;
				if (handle.isHandleIdKept()) {
					continue;
				}
				if (handle.getReferenceCount() > 0) {
					this.referencedHandles.add(handle);
				}
				else {
					giveBack(handle);
				}
			}
		}
		this.releasedHandles.clear();
	}

	private void giveBack(final CAllocatedHandle handle) {
		// Far below zero: references made to the destroyed handle later never bring the count back to 0.
		handle.changeReferenceCount(GIVEN_BACK - handle.getReferenceCount());
		pushFreeId(handle.getHandleId());
	}

	private void pushFreeId(final int handleId) {
		scriptHandles().remove(TABLE, handleId);
		if (this.freeHandleIdCount == this.freeHandleIds.length) {
			this.freeHandleIds = Arrays.copyOf(this.freeHandleIds, this.freeHandleIds.length * 2);
		}
		this.freeHandleIds[this.freeHandleIdCount++] = handleId;
	}

	/** Lightning ids are counted apart from the handle table (1.29 gave the first lightning 1). */
	public int createLightningId() {
		return this.nextLightningId++;
	}

	/** Text tags have 100 ids of their own, given from 99 down (measured on 1.29). */
	public int createTextTagId() {
		if (this.freeTextTagIdCount == 0) {
			// 1.29 makes no more than 100; past that they still need ids no other handle has.
			return createHandleId();
		}
		return this.freeTextTagIds[--this.freeTextTagIdCount];
	}

	public void freeTextTagId(final int textTagId) {
		if ((textTagId >= 0) && (textTagId < TEXT_TAG_COUNT)) {
			scriptHandles().remove(TEXT_TAGS, textTagId);
			for (int i = 0; i < this.freeTextTagIdCount; i++) {
				if (this.freeTextTagIds[i] == textTagId) {
					return;
				}
			}
			this.freeTextTagIds[this.freeTextTagIdCount++] = textTagId;
		}
		else if (textTagId >= TEXT_TAG_COUNT) {
			scriptHandles().remove(TEXT_TAGS, textTagId);
			freeHandleId(textTagId);
		}
	}

	private static int[] allTextTagIds() {
		final int[] ids = new int[TEXT_TAG_COUNT];
		for (int i = 0; i < TEXT_TAG_COUNT; i++) {
			ids[i] = i;
		}
		return ids;
	}

	public int createImageId() {
		if (!this.freeImageIds.isEmpty()) {
			return this.freeImageIds.remove(this.freeImageIds.size() - 1);
		}
		return this.nextImageId++;
	}

	public void freeImageId(final int imageId) {
		if (imageId >= 0) {
			scriptHandles().remove(IMAGES, imageId);
			this.freeImageIds.add(imageId);
		}
	}

	public int createUbersplatId() {
		if (!this.freeUbersplatIds.isEmpty()) {
			return this.freeUbersplatIds.remove(this.freeUbersplatIds.size() - 1);
		}
		return this.nextUbersplatId++;
	}

	public void freeUbersplatId(final int ubersplatId) {
		if (ubersplatId >= 0) {
			scriptHandles().remove(UBERSPLATS, ubersplatId);
			this.freeUbersplatIds.add(ubersplatId);
		}
	}

	/** The id GetHandleId gives a script. */
	@Override
	public int handleIdOf(final Object handle) {
		final int handleId = numberOf(handle);
		scriptHandles().put(poolOf(handle), handleId, handle);
		return handleId;
	}

	/**
	 * The handle a script got the id of that holds it now; null when the id was freed since, or the handle holding it
	 * was never asked for its id (see {@code CSimulation.handleOfId} for the ones found without).
	 */
	@Override
	public Object handleOfId(final int handleId, final String typeName) {
		final int pool = poolOf(typeName);
		final Object handle = scriptHandles().get(pool, handleId);
		if ((handle instanceof CHandle) && (((CHandle) handle).getHandleId() != handleId)) {
			return null;
		}
		if ((pool == LIGHTNINGS) && (handle instanceof SimulationRenderComponentLightning)
				&& ((SimulationRenderComponentLightning) handle).isRemoved()) {
			// Lightning ids are never given again, so a destroyed one has nothing to free its entry.
			scriptHandles().remove(pool, handleId);
			return null;
		}
		return handle;
	}

	private ScriptHandleTable scriptHandles() {
		if (this.scriptHandleTable == null) {
			this.scriptHandleTable = new ScriptHandleTable(FIRST_HANDLE_ID);
		}
		if (this.scriptHandles != null) {
			for (final Map.Entry<Long, Object> entry : this.scriptHandles.entrySet()) {
				final long key = entry.getKey();
				this.scriptHandleTable.put((int) (key >>> 32), (int) key, entry.getValue());
			}
			this.scriptHandles = null;
		}
		return this.scriptHandleTable;
	}

	private static int poolOf(final Object handle) {
		if (handle instanceof TextTag) {
			return TEXT_TAGS;
		}
		if (handle instanceof SimulationRenderComponentLightning) {
			return LIGHTNINGS;
		}
		if (handle instanceof CImage) {
			return IMAGES;
		}
		if (handle instanceof SimulationRenderComponentUbersplat) {
			return UBERSPLATS;
		}
		return TABLE;
	}

	private static int poolOf(final String typeName) {
		if (typeName == null) {
			return TABLE;
		}
		switch (typeName) {
		case "texttag":
			return TEXT_TAGS;
		case "lightning":
			return LIGHTNINGS;
		case "image":
			return IMAGES;
		case "ubersplat":
			return UBERSPLATS;
		default:
			return TABLE;
		}
	}

	private int numberOf(final Object handle) {
		if (handle instanceof CHandle) {
			if ((handle instanceof CAllocatedHandle) && (((CHandle) handle).getHandleId() == 0)) {
				((CAllocatedHandle) handle).setHandleId(createHandleId());
			}
			final int handleId = ((CHandle) handle).getHandleId();
			if (handleId != UNNUMBERED) {
				return handleId;
			}
		}
		for (final UntrackedHandle untracked : this.untrackedHandles) {
			if (untracked.handle == handle) {
				return untracked.handleId;
			}
		}
		final UntrackedHandle untracked = new UntrackedHandle(handle, createHandleId());
		this.untrackedHandles.add(untracked);
		return untracked.handleId;
	}

	public int getNextId() {
		return this.next;
	}

	public int getNextHandleId() {
		return this.nextHandleId;
	}

	public int getFreeHandleIdCount() {
		return this.freeHandleIdCount + this.releasedHandles.size();
	}

	private static final class UntrackedHandle {
		private final Object handle;
		private final int handleId;

		private UntrackedHandle(final Object handle, final int handleId) {
			this.handle = handle;
			this.handleId = handleId;
		}
	}

	@Override
	public void initAddedFields(final Set<String> fieldNames) {
		if (fieldNames.contains("nextHandleId")) {
			// Ids of the save are all below the old counter; new handles come after them.
			this.nextHandleId = this.next;
			this.next += LEGACY_ABILITY_ID_OFFSET;
		}
		if (fieldNames.contains("freeHandleIds")) {
			this.freeHandleIds = new int[64];
		}
		if (fieldNames.contains("nextLightningId")) {
			this.nextLightningId = 1;
		}
		if (fieldNames.contains("freeTextTagIds")) {
			this.freeTextTagIds = allTextTagIds();
			this.freeTextTagIdCount = TEXT_TAG_COUNT;
		}
	}
}
