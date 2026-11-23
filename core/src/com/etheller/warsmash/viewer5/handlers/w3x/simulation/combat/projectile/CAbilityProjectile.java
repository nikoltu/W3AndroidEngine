package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;

public class CAbilityProjectile extends CProjectile {
	private final CAbilityProjectileListener projectileListener;
	private boolean spellMissile;

	public CAbilityProjectile(final float x, final float y, final float speed, final AbilityTarget target,
			boolean homingEnabled, final CUnit source, final CAbilityProjectileListener projectileListener) {
		super(x, y, speed, target, homingEnabled, source);
		this.projectileListener = projectileListener;
		final CUnit targetUnit = (target == null) ? null : target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			targetUnit.onSpellMissileLaunched(source);
		}
	}

	/** A missile of a spell (not of an attack): an enemy it lands on gets the spell's damaged event. */
	public void setSpellMissile(final boolean spellMissile) {
		this.spellMissile = spellMissile;
	}

	@Override
	protected void onHitTarget(CSimulation game) {
		final boolean targetGone = isTargetGone(game);
		if (targetGone && !projectileListener.hitsGoneTarget()) {
			return;
		}
		final CUnit targetUnit = (getTarget() == null) ? null : getTarget().visit(AbilityTargetVisitor.UNIT);
		// No event once the caster is gone: a Storm Bolt in the air when its caster is removed hurts without it.
		if (this.spellMissile && (targetUnit != null) && !targetGone && (getSource() != null)
				&& !getSource().isRemovedFromGame() && !game.isRemovalPending(getSource())
				&& (targetUnit.getPlayerIndex() != getSource().getPlayerIndex())) {
			targetUnit.fireSpellHitEvent(game, getSource());
		}
		projectileListener.onHit(game, this, getTarget());
		if ((targetUnit != null) && !targetGone) {
			targetUnit.onTargetedBySpell(game, getSource());
		}
	}
}
