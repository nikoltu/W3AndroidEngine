package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.EnumSet;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.BufferedAnimationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.BufferingRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PeerQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEventSink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldPeerData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetWidgetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;

/**
 * Makes units, items and destructables for the simulation without touching the render side: the simulation's object is
 * made at once and returned, what its peer is built from is announced to the {@link PeerQueue} and sent with the
 * creation event, and the object gets the listener that records its animation calls as events, so it never holds a
 * render object. Called on the simulation thread; the render thread may call it too while it runs the simulation's
 * calls on request (the scripts' natives), and then the peers are built before it returns, as they were when the
 * render controller made everything.
 */
final class MapViewerSimWorld implements BufferingRenderController.WorldFactory {
	private final War3MapViewer viewer;
	private final RenderEventSink sink;
	private final PeerQueue peers;
	private final BooleanSupplier onSimulationThread;
	private WorldPeerData.Unit lastUnit;
	private WorldPeerData.Item lastItem;
	private WorldPeerData.Destructable lastDestructable;

	MapViewerSimWorld(final War3MapViewer viewer, final RenderEventSink sink, final PeerQueue peers,
			final BooleanSupplier onSimulationThread) {
		this.viewer = viewer;
		this.sink = sink;
		this.peers = peers;
		this.onSimulationThread = onSimulationThread;
	}

	private void announced(final int handle, final Object data) {
		this.peers.announce(handle, data);
		if (!this.onSimulationThread.getAsBoolean()) {
			this.peers.flush();
		}
	}

	@Override
	public CUnit createUnit(final CSimulation simulation, final War3ID typeId, final int playerIndex, final float x,
			final float y, final float facing) {
		final WorldPeerData.Unit data = (WorldPeerData.Unit) MapViewerUnits.spawn(this.viewer,
				this.viewer.allObjectData, typeId, x, y, playerIndex, playerIndex, (float) Math.toRadians(facing), -1,
				-1, true);
		this.lastUnit = data;
		if (data == null) {
			return null;
		}
		final int handle = data.unit.getHandleId();
		final BufferedAnimationListener listener = new BufferedAnimationListener(this.sink, handle);
		listener.seedSecondaryTags(requiredAnimationTags(this.viewer.allObjectData.getUnits().get(typeId)));
		data.unit.setUnitAnimationListener(listener);
		announced(handle, data);
		return data.unit;
	}

	@Override
	public WorldPeerData.Unit unitPeerData(final CUnit unit) {
		final WorldPeerData.Unit data = this.lastUnit;
		return (data != null) && (data.unit == unit) ? data : null;
	}

	@Override
	public CItem createItem(final CSimulation simulation, final War3ID typeId, final float x, final float y) {
		final WorldPeerData.Item data = (WorldPeerData.Item) MapViewerUnits.spawn(this.viewer,
				this.viewer.allObjectData, typeId, x, y, -1, -1,
				(float) Math.toRadians(this.viewer.simulation.getGameplayConstants().getBuildingAngle()), -1, -1,
				false);
		this.lastItem = data;
		if (data == null) {
			return null;
		}
		announced(data.item.getHandleId(), data);
		return data.item;
	}

	@Override
	public WorldPeerData.Item itemPeerData(final CItem item) {
		final WorldPeerData.Item data = this.lastItem;
		return (data != null) && (data.item == item) ? data : null;
	}

	@Override
	public CDestructable createDestructable(final War3ID typeId, final float x, final float y, final float facing,
			final float scale, final int variation) {
		final GameObject row = this.viewer.allObjectData.getDestructibles().get(typeId);
		final float snappedX = row == null ? x : this.viewer.snapRunTimeDestructableX(row, x, facing);
		final float snappedY = row == null ? y : this.viewer.snapRunTimeDestructableY(row, y, facing);
		return createDestructableZ(typeId, snappedX, snappedY, Float.NaN, facing, scale, variation);
	}

	@Override
	public CDestructable createDestructableZ(final War3ID typeId, final float x, final float y, final float z,
			final float facing, final float scale, final int variation) {
		final WorldPeerData.Destructable data = MapViewerDoodads.spawnRunTimeDestructable(this.viewer, typeId, x, y, z,
				facing, scale, variation);
		this.lastDestructable = data;
		final int handle = data.destructable.getHandleId();
		data.destructable.setSimulationAnimationListener(new BufferedAnimationListener(this.sink, handle));
		announced(handle, data);
		return data.destructable;
	}

	@Override
	public WorldPeerData.Destructable destructablePeerData(final CDestructable destructable) {
		final WorldPeerData.Destructable data = this.lastDestructable;
		return (data != null) && (data.destructable == destructable) ? data : null;
	}

	@Override
	public WorldPeerData.ProjectileLaunch projectileLaunch(final CProjectile projectile, final int projectileId,
			final CUnit source, final CUnitAttackSettings settings) {
		final CWidget targetWidget = projectile.getTarget().visit(AbilityTargetWidgetVisitor.INSTANCE);
		return new WorldPeerData.ProjectileLaunch(projectile.getX(), projectile.getY(), source.getFlyHeight(),
				this.viewer.simulation.getUnitData().getProjectileLaunchZ(source.getTypeId()),
				this.viewer.simulation.getPlayer(source.getPlayerIndex()).getColor(), projectileId,
				projectile.getTargetX(), projectile.getTargetY(),
				targetWidget == null ? 0 : targetWidget.getFlyHeight(),
				targetWidget == null ? 0 : targetWidget.getImpactZ(), this.viewer.simulation.getGameTurnTick());
	}

	@Override
	public WorldPeerData.InstantAttack instantAttack(final CUnit source, final CUnitAttackInstant attack,
			final CWidget target) {
		final War3ID typeId = source.getTypeId();
		final float projectileLaunchX = this.viewer.simulation.getUnitData().getProjectileLaunchX(typeId);
		final float projectileLaunchY = this.viewer.simulation.getUnitData().getProjectileLaunchY(typeId);
		final float facing = (float) Math.toRadians(source.getFacing());
		final float sinFacing = (float) Math.sin(facing);
		final float cosFacing = (float) Math.cos(facing);
		final float x = source.getX() + (projectileLaunchY * cosFacing) + (projectileLaunchX * sinFacing);
		final float y = (source.getY() + (projectileLaunchY * sinFacing)) - (projectileLaunchX * cosFacing);
		return new WorldPeerData.InstantAttack(attack.getProjectileArt(), x, y, target.getX(), target.getY(),
				target.getFlyHeight(), target.getImpactZ(), source.getPlayerIndex());
	}

	@Override
	public int playerColor(final int playerIndex) {
		return this.viewer.simulation.getPlayer(playerIndex).getColor();
	}

	@Override
	public WorldPeerData.TypeChange typeChangeData(final CUnit unit) {
		return new WorldPeerData.TypeChange(unit.getTeamColorOverride() != -1 ? unit.getTeamColorOverride()
				: this.viewer.simulation.getPlayer(unit.getPlayerIndex()).getColor(), unit.getX(), unit.getY(),
				unit.getFlyHeight());
	}

	/** The tags the render side puts on the listener of every unit of the type; the simulation's mirror starts from them. */
	private static EnumSet<SecondaryTag> requiredAnimationTags(final GameObject row) {
		final EnumSet<SecondaryTag> tags = EnumSet.noneOf(SecondaryTag.class);
		final String names = row == null ? null : row.getField(RenderUnitTypeData.ANIM_PROPS);
		if (names == null) {
			return tags;
		}
		tokens: for (final String name : names.split(",")) {
			final String upperCaseToken = name.toUpperCase(Locale.ROOT);
			for (final SecondaryTag tag : SecondaryTag.VALUES) {
				if (upperCaseToken.equals(tag.name())) {
					tags.add(tag);
					continue tokens;
				}
			}
		}
		return tags;
	}
}
