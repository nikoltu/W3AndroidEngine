package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitStateListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityPassiveNoOrders;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitAppliedUpgrade;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade.CUpgradeEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade.CUpgradeEffectSpikedBarricades;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Spiked Barricades: returns damage to enemy melee attackers. The ability has no data of its own; the amount comes
 * from the rspi upgrades applied to the unit, and the spikes model shows while that amount is above zero.
 */
public class CAbilitySpikedBarricades extends AbstractCAbilityPassiveNoOrders implements CUnitAttackDamageTakenListener {
	private static final CDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();

	private final CUnitStateListener upgradesListener = new UpgradesListener();
	private CSimulation game;
	private CUnit unit;
	private SimulationRenderComponentModel spikesFx;

	public CAbilitySpikedBarricades(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
	}

	public static float getDamage(final CUnit unit) {
		float damage = 0;
		for (final CUnitAppliedUpgrade upgrade : unit.getAppliedUpgrades()) {
			for (final CUpgradeEffect effect : upgrade.getType().getUpgradeEffects()) {
				if (effect instanceof CUpgradeEffectSpikedBarricades) {
					damage += ((CUpgradeEffectSpikedBarricades) effect).getDamage(upgrade.getLevel());
				}
			}
		}
		return damage;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.game = game;
		this.unit = unit;
		unit.addDamageTakenListener(this);
		unit.addStateListener(this.upgradesListener);
		updateSpikes();
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.removeDamageTakenListener(this);
		unit.removeStateListener(this.upgradesListener);
		if (this.spikesFx != null) {
			this.spikesFx.remove();
			this.spikesFx = null;
		}
		this.unit = null;
	}

	private void updateSpikes() {
		if (this.unit == null) {
			return;
		}
		final boolean spiked = getDamage(this.unit) > 0;
		if (spiked && (this.spikesFx == null)) {
			this.spikesFx = this.game.createPersistentSpellEffectOnUnit(this.unit, getAlias(), CEffectType.TARGET);
		}
		else if (!spiked && (this.spikesFx != null)) {
			this.spikesFx.remove();
			this.spikesFx = null;
		}
	}

	@Override
	public void onDamage(final CSimulation simulation, final CUnit attacker, final CUnit target,
			final CDamageFlags flags, final CDamageType damageType, final float damage, final float bonusDamage,
			final float trueDamage) {
		if ((damageType != CDamageType.NORMAL) || flags.isRanged()
				|| !target.canBeTargetedBy(simulation, attacker, ENEMY_TARGET)
				|| (simulation.getGameplayConstants().isMagicImmuneResistsThorns()
						&& attacker.isUnitType(CUnitTypeJass.MAGIC_IMMUNE))) {
			return;
		}
		final float returned = getDamage(target);
		if (returned > 0) {
			attacker.damage(simulation, target, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.DEFENSIVE,
					CWeaponSoundTypeJass.WHOKNOWS.name(), returned);
		}
	}

	private final class UpgradesListener implements CUnitStateListener {
		@Override
		public void upgradesChanged() {
			updateSpikes();
		}

		@Override
		public void lifeChanged() {
		}

		@Override
		public void manaChanged() {
		}

		@Override
		public void ordersChanged() {
		}

		@Override
		public void queueChanged() {
		}

		@Override
		public void rallyPointChanged() {
		}

		@Override
		public void waypointsChanged() {
		}

		@Override
		public void heroStatsChanged() {
		}

		@Override
		public void inventoryChanged() {
		}

		@Override
		public void attacksChanged() {
		}

		@Override
		public void abilitiesChanged() {
		}

		@Override
		public void hideStateChanged() {
		}
	}
}
