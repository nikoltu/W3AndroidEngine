package com.etheller.warsmash.parsers.jass.triggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.warsmash.util.War3ID;

/**
 * A script unit or item pool: type ids with weights, kept in the order they were added so that a pick from the
 * game's random stream gives the same type on every machine.
 */
public abstract class CTypePool implements CAllocatedHandle {
	private final List<War3ID> typeIds = new ArrayList<>();
	private final List<Float> weights = new ArrayList<>();
	private int handleId;
	private boolean handleReleased;
	private boolean handleIdKept;
	private int referenceCount;

	/** Adding a type the pool already holds gives it the new weight (measured on 1.29), it does not add a second entry. */
	public void add(final War3ID typeId, final float weight) {
		final int index = this.typeIds.indexOf(typeId);
		if (index >= 0) {
			this.weights.set(index, weight);
			return;
		}
		this.typeIds.add(typeId);
		this.weights.add(weight);
	}

	public void remove(final War3ID typeId) {
		for (int i = this.typeIds.size() - 1; i >= 0; i--) {
			if (this.typeIds.get(i).equals(typeId)) {
				this.typeIds.remove(i);
				this.weights.remove(i);
			}
		}
	}

	/** The type picked with chance proportional to its weight, null when no type has a positive weight. */
	public War3ID pick(final Random random) {
		float total = 0;
		for (final float weight : this.weights) {
			if (weight > 0) {
				total += weight;
			}
		}
		if (!(total > 0)) {
			return null;
		}
		float roll = random.nextFloat() * total;
		War3ID last = null;
		for (int i = 0; i < this.typeIds.size(); i++) {
			final float weight = this.weights.get(i);
			if (weight > 0) {
				last = this.typeIds.get(i);
				roll -= weight;
				if (roll < 0) {
					return last;
				}
			}
		}
		return last;
	}

	@Override
	public int getHandleId() {
		return this.handleId;
	}

	@Override
	public void setHandleId(final int handleId) {
		this.handleId = handleId;
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
}
