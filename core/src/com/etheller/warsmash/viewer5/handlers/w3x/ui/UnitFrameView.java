package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.Extrapolator;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PresentationClock;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.UnitFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldObjectFrameState;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * What the interface needs to know of a unit or a tree that is shown on the screen (status bars, the cursor, what may
 * be selected, who controls it): taken from the frame being presented, the one the world is drawn from. Without a
 * presentation clock (the simulation runs in the frame) the unit itself is asked, as before.
 * <p>
 * Call {@link #unit} or {@link #destructable} first; it says whether there is anything to read and the other methods
 * then answer for that widget.
 */
final class UnitFrameView {
	private final War3MapViewer viewer;
	private UnitFrameState units;
	private WorldObjectFrameState trees;
	private int row;
	private RenderUnit liveUnit;
	private RenderWidget liveWidget;

	UnitFrameView(final War3MapViewer viewer) {
		this.viewer = viewer;
	}

	/** The frame the world and the interface are drawn from; null when there is none (off mode, before the first). */
	SimFrame presentedFrame() {
		final PresentationClock clock = this.viewer.getPresentation();
		return clock == null ? null : clock.frameBefore();
	}

	/** True when the interface reads frames of the game. */
	boolean isFrameMode() {
		return this.viewer.getPresentation() != null;
	}

	/** False when the frame has no such unit (it was made after it, or is gone). */
	boolean unit(final RenderUnit unit) {
		this.liveUnit = null;
		this.liveWidget = unit;
		this.trees = null;
		if (!isFrameMode()) {
			this.units = null;
			this.liveUnit = unit;
			return true;
		}
		final SimFrame frame = presentedFrame();
		if (frame == null) {
			return false;
		}
		this.units = frame.units;
		this.row = this.units.indexOf(unit.getSimulationUnit().getHandleId());
		return this.row >= 0;
	}

	/** Any widget the interface shows a bar over; an item has no life to show. */
	boolean widget(final RenderWidget widget) {
		if (widget instanceof RenderUnit) {
			return unit((RenderUnit) widget);
		}
		if (widget instanceof RenderDestructable) {
			return destructable((RenderDestructable) widget);
		}
		this.units = null;
		this.trees = null;
		this.liveUnit = null;
		this.liveWidget = widget;
		return true;
	}

	/** As {@link #unit} for a tree or other destructable. */
	boolean destructable(final RenderDestructable destructable) {
		this.liveUnit = null;
		this.liveWidget = destructable;
		this.units = null;
		if (!isFrameMode()) {
			this.trees = null;
			return true;
		}
		final SimFrame frame = presentedFrame();
		if (frame == null) {
			return false;
		}
		this.trees = frame.trees;
		this.row = Extrapolator.rowOf(this.trees, destructable.getSimulationDestructable().getHandleId(), -1);
		return this.row >= 0;
	}

	boolean isDead() {
		if (this.units != null) {
			return (this.units.flags[this.row] & UnitFrameState.DEAD) != 0;
		}
		if (this.trees != null) {
			return (this.trees.flags[this.row] & WorldObjectFrameState.DEAD) != 0;
		}
		return this.liveWidget.getSimulationWidget().isDead();
	}

	/** Hidden, or out of the local player's sight. */
	boolean isHiddenFromLocal() {
		if (this.units != null) {
			final int flags = this.units.flags[this.row];
			return ((flags & UnitFrameState.HIDDEN) != 0) || ((flags & UnitFrameState.VISIBLE_TO_LOCAL) == 0);
		}
		final CUnit unit = this.liveUnit.getSimulationUnit();
		return unit.isHidden() || !unit.isVisible(this.viewer.simulation, this.viewer.getLocalPlayerIndex());
	}

	boolean isInvulnerable() {
		if (this.units != null) {
			return (this.units.targetClass[this.row] & UnitFrameState.TARGET_INVULNERABLE) != 0;
		}
		return this.liveWidget.getSimulationWidget().isInvulnerable();
	}

	/** Life as a fraction of the maximum. */
	float lifeRatio() {
		if (this.units != null) {
			return this.units.hpFraction[this.row];
		}
		if (this.trees != null) {
			return this.trees.life[this.row];
		}
		if (isFrameMode()) {
			return 1f;
		}
		return this.liveWidget.getSimulationWidget().getLife() / this.liveWidget.getSimulationWidget().getMaxLife();
	}

	/** The unit's owner. */
	int ownerIndex() {
		if (this.units != null) {
			return this.units.owner[this.row];
		}
		return this.liveUnit.getSimulationUnit().getPlayerIndex();
	}

	/** A structure being built for the first time (not one that is upgrading). */
	boolean isRising() {
		if (this.units != null) {
			return (this.units.flags[this.row] & UnitFrameState.RISING) != 0;
		}
		return this.liveUnit.getSimulationUnit().isConstructing();
	}

	boolean isBuilding() {
		if (this.units != null) {
			return (this.units.flags[this.row] & UnitFrameState.BUILDING) != 0;
		}
		return this.liveUnit.getSimulationUnit().isBuilding();
	}

	/** The local player's ally (or the local player) as the targeting rules see it. */
	boolean isAllyOfLocal() {
		if (this.units != null) {
			return (this.units.targetClass[this.row] & UnitFrameState.TARGET_ALLY) != 0;
		}
		return this.liveUnit.getSimulationUnit().isUnitAlly(localPlayer());
	}

	/** The unit's type (static data, found by the frame's type id). */
	CUnitType unitType() {
		if (this.units != null) {
			return this.viewer.simulation.getUnitData().getUnitType(new War3ID(this.units.typeId[this.row]));
		}
		return this.liveUnit.getSimulationUnit().getUnitType();
	}

	boolean hasMana() {
		if (this.units != null) {
			return (this.units.flags[this.row] & UnitFrameState.HAS_MANA) != 0;
		}
		return (this.liveUnit != null) && (this.liveUnit.getSimulationUnit().getMaximumMana() > 0);
	}

	float manaRatio() {
		if (this.units != null) {
			return this.units.manaFraction[this.row];
		}
		return this.liveUnit.getSimulationUnit().getMana() / this.liveUnit.getSimulationUnit().getMaximumMana();
	}

	/** Seen by the local player and not marked unselectable for them. */
	boolean isSelectableByLocal() {
		if (this.units != null) {
			final int flags = this.units.flags[this.row];
			return ((flags & UnitFrameState.VISIBLE_TO_LOCAL) != 0) && ((flags & UnitFrameState.SELECTABLE) != 0);
		}
		return this.liveWidget.isSelectable(this.viewer.simulation, this.viewer.getLocalPlayerIndex());
	}

	/** The local player gives orders to the unit: it is theirs, or its owner shares control with them. */
	boolean isControlledByLocal() {
		if (this.units != null) {
			return (this.units.owner[this.row] == this.viewer.getLocalPlayerIndex())
					|| ((this.units.flags[this.row] & UnitFrameState.CONTROL_SHARED) != 0);
		}
		final CUnit unit = this.liveUnit.getSimulationUnit();
		return (unit.getPlayerIndex() == this.viewer.getLocalPlayerIndex())
				|| localPlayer().hasAlliance(unit.getPlayerIndex(), CAllianceType.SHARED_CONTROL);
	}

	/** The unit, or the player it is drawn as, shares control with the local player (the green selection cursor). */
	boolean isTeamControlShared() {
		if (this.units != null) {
			return (this.units.flags[this.row] & UnitFrameState.TEAM_CONTROL_SHARED) != 0;
		}
		final CUnit unit = this.liveUnit.getSimulationUnit();
		return localPlayer().hasAlliance(unit.getFakePlayerIndex(), CAllianceType.SHARED_CONTROL)
				|| localPlayer().hasAlliance(unit.getPlayerIndex(), CAllianceType.SHARED_CONTROL);
	}

	/** The local player is allied (passive) with the player the unit is drawn as. */
	boolean isTeamAllied() {
		if (this.units != null) {
			return (this.units.flags[this.row] & UnitFrameState.TEAM_ALLIED) != 0;
		}
		return localPlayer().hasAlliance(this.liveUnit.getSimulationUnit().getFakePlayerIndex(),
				CAllianceType.PASSIVE);
	}

	private CPlayer localPlayer() {
		return this.viewer.simulation.getPlayer(this.viewer.getLocalPlayerIndex());
	}
}
