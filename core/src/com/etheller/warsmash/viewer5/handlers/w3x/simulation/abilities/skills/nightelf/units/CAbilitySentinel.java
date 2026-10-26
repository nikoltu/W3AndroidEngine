package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CProjectileVisionFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Sentinel: an owl flies to a tree and perches above it, revealing the area around it and the invisible units in it
 * until its time runs out or the tree falls.
 */
public class CAbilitySentinel extends CAbilityTargetSpellBase {
	private float inFlightSightRadius;
	private float hoveringSightRadius;
	private float hoveringHeight;
	private int numberOfOwls;
	private float owlDuration;
	private float projectileSpeed;
	private War3ID effectId;
	private final List<CSentinelOwl> owls = new ArrayList<>();

	public CAbilitySentinel(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.sentinel;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.inFlightSightRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.hoveringSightRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.hoveringHeight = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.numberOfOwls = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_D + level, 0);
		this.owlDuration = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		this.effectId = AbstractCAbilityTypeDefinition.getEffectId(worldEditorAbility, level);
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (target.visit(AbilityTargetVisitor.DESTRUCTABLE) == null) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_TREE);
		}
		else {
			super.innerCheckCanTarget(game, unit, orderId, target, receiver);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CDestructable tree = target.visit(AbilityTargetVisitor.DESTRUCTABLE);
		if (tree == null) {
			return false;
		}
		final CPlayer player = simulation.getPlayer(caster.getPlayerIndex());
		final CProjectileVisionFogModifier[] flightVision = new CProjectileVisionFogModifier[1];
		final CProjectile projectile = simulation.createProjectile(caster, getAlias(), caster.getX(), caster.getY(),
				(float) caster.angleTo(tree), this.projectileSpeed, true, tree, new CAbilityProjectileListener() {
					@Override
					public void onLaunch(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
					}

					@Override
					public void onHit(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
						if (flightVision[0] != null) {
							player.removeFogModifer(game, flightVision[0]);
						}
						if (!tree.isDead()) {
							perch(game, caster, player, tree);
						}
					}
				});
		flightVision[0] = new CProjectileVisionFogModifier(projectile, this.inFlightSightRadius);
		player.addFogModifer(simulation, flightVision[0], false);
		return false;
	}

	private void perch(final CSimulation game, final CUnit caster, final CPlayer player, final CDestructable tree) {
		this.owls.removeIf(CSentinelOwl::isGone);
		if ((this.numberOfOwls > 0) && (this.owls.size() >= this.numberOfOwls)) {
			this.owls.remove(0).leave(game);
		}
		final CSentinelOwl owl = new CSentinelOwl(game.getHandleIdAllocator().createId(), getAlias(), tree, player,
				this.hoveringSightRadius, this.owlDuration);
		owl.perch(game, this.effectId, (float) Math.toDegrees(caster.angleTo(tree)), this.hoveringHeight);
		this.owls.add(owl);
	}

	public int getOwlCount() {
		this.owls.removeIf(CSentinelOwl::isGone);
		return this.owls.size();
	}
}
