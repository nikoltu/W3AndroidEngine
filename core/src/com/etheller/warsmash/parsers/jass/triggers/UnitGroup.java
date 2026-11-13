package com.etheller.warsmash.parsers.jass.triggers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.Consumer;

import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * Measured on 1.29: a removed unit leaves every group it was in, so ForGroup,
 * CountUnitsInGroup, FirstOfGroup and IsUnitInGroup no longer see it. Removed members are dropped on read.
 */
public class UnitGroup extends ArrayList<CUnit> implements CAllocatedHandle {
	private int handleId;
	private boolean handleReleased;
	private boolean handleIdKept;
	private int referenceCount;

	public UnitGroup(int handleId) {
		this.handleId = handleId;
	}

	@Override
	public int getHandleId() {
		return handleId;
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

	/**
	 * Measured on 1.29: a group keeps its units in a fixed order, whatever the order they were added in; ForGroup and
	 * FirstOfGroup follow it. Units made after the game started come first, newest first (by handle id). Then come the
	 * units made while the map was loading (the map's own units): grouped by unit type, the type of the unit made last
	 * first, and inside a type in the order they were made.
	 */
	@Override
	public boolean add(final CUnit unit) {
		int index = super.size();
		if (unit != null) {
			for (int i = 0; i < super.size(); i++) {
				final CUnit other = super.get(i);
				if ((other == null) || precedes(unit, other)) {
					index = i;
					break;
				}
			}
		}
		super.add(index, unit);
		return true;
	}

	private static boolean precedes(final CUnit unit, final CUnit other) {
		final boolean unitFromStart = unit.isMadeBeforeStart();
		final boolean otherFromStart = other.isMadeBeforeStart();
		if (unitFromStart != otherFromStart) {
			return otherFromStart;
		}
		if (!unitFromStart) {
			return other.getHandleId() < unit.getHandleId();
		}
		final int unitTypeLast = unit.getStartTypeLastIndex();
		final int otherTypeLast = other.getStartTypeLastIndex();
		if (unitTypeLast != otherTypeLast) {
			return unitTypeLast > otherTypeLast;
		}
		return unit.getStartCreationIndex() < other.getStartCreationIndex();
	}

	@Override
	public boolean addAll(final Collection<? extends CUnit> units) {
		for (final CUnit unit : units) {
			add(unit);
		}
		return !units.isEmpty();
	}

	private void dropRemovedUnits() {
		removeIf(unit -> (unit != null) && unit.isRemovedFromGame());
	}

	@Override
	public int size() {
		dropRemovedUnits();
		return super.size();
	}

	@Override
	public boolean isEmpty() {
		dropRemovedUnits();
		return super.isEmpty();
	}

	@Override
	public CUnit get(final int index) {
		dropRemovedUnits();
		return super.get(index);
	}

	@Override
	public boolean contains(final Object o) {
		dropRemovedUnits();
		return super.contains(o);
	}

	@Override
	public int indexOf(final Object o) {
		dropRemovedUnits();
		return super.indexOf(o);
	}

	@Override
	public Iterator<CUnit> iterator() {
		dropRemovedUnits();
		return super.iterator();
	}

	@Override
	public ListIterator<CUnit> listIterator() {
		dropRemovedUnits();
		return super.listIterator();
	}

	@Override
	public ListIterator<CUnit> listIterator(final int index) {
		dropRemovedUnits();
		return super.listIterator(index);
	}

	@Override
	public Spliterator<CUnit> spliterator() {
		dropRemovedUnits();
		return super.spliterator();
	}

	@Override
	public void forEach(final Consumer<? super CUnit> action) {
		dropRemovedUnits();
		super.forEach(action);
	}

	@Override
	public Object[] toArray() {
		// ForGroup walks a copy (new ArrayList<>(group)), which reads toArray.
		dropRemovedUnits();
		return super.toArray();
	}

	@Override
	public <T> T[] toArray(final T[] a) {
		dropRemovedUnits();
		return super.toArray(a);
	}

}
