package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.alchemist;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Healing Spray: a bottle flies to the target point and sprays waves of healing mist there. Measured (1.29): the first
 * wave falls where the bottle lands and one more every interval; a wave heals each friendly unit in the area by the
 * data's amount, or by the wave's total shared by the units in the area (healed or not) when that is less, and marks
 * them with the spray's buff for one interval, all but the last wave.
 */
public class CAbilityHealingSpray extends CAbilityPointTargetSpellBase {
	private final Rectangle recycleRect = new Rectangle();

	private float healingPerWave;
	private float maximumHealingPerWave;
	private float waveInterval;
	private int waveCount;
	private float areaOfEffect;
	private War3ID effectId;
	private War3ID buffId;

	public CAbilityHealingSpray(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.healingspray;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.healingPerWave = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.waveInterval = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.maximumHealingPerWave = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.waveCount = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_F + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.effectId = AbstractCAbilityTypeDefinition.getEffectId(worldEditorAbility, level);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public float getUIAreaOfEffect() {
		return this.areaOfEffect;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final AbilityPointTarget point = new AbilityPointTarget(target.getX(), target.getY());
		simulation.createProjectile(caster, getAlias(), caster.getX(), caster.getY(),
				(float) caster.angleTo(point), null, null, point, new CAbilityProjectileListener() {
					@Override
					public void onLaunch(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
					}

					@Override
					public void onHit(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
						new Waves(caster, point).begin(game);
					}
				});
		return false;
	}

	/**
	 * The waves of one cast, which go on whatever else its caster does.
	 */
	private final class Waves extends CTimer {
		private final CUnit caster;
		private final AbilityPointTarget point;
		private int wavesDone;

		private Waves(final CUnit caster, final AbilityPointTarget point) {
			this.caster = caster;
			this.point = point;
			setTimeoutTime(CAbilityHealingSpray.this.waveInterval);
		}

		private void begin(final CSimulation simulation) {
			this.wavesDone++;
			wave(simulation, this.caster, this.point, this.wavesDone < CAbilityHealingSpray.this.waveCount);
			if (this.wavesDone < CAbilityHealingSpray.this.waveCount) {
				startRepeatingTimerWithDelay(simulation, CAbilityHealingSpray.this.waveInterval);
			}
		}

		@Override
		public void onFire(final CSimulation simulation) {
			this.wavesDone++;
			wave(simulation, this.caster, this.point, this.wavesDone < CAbilityHealingSpray.this.waveCount);
			if (this.wavesDone >= CAbilityHealingSpray.this.waveCount) {
				kill(simulation);
			}
		}
	}

	private void wave(final CSimulation simulation, final CUnit caster, final AbilityPointTarget point,
			final boolean marks) {
		final List<CUnit> healed = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRect(this.recycleRect.set(point.getX() - this.areaOfEffect,
				point.getY() - this.areaOfEffect, this.areaOfEffect * 2, this.areaOfEffect * 2),
				new CUnitEnumFunction() {
					@Override
					public boolean call(final CUnit possibleTarget) {
						if (possibleTarget.canReach(point, CAbilityHealingSpray.this.areaOfEffect)
								&& possibleTarget.canBeAreaTargetedBy(simulation, caster, CAreaTargetVisibility.ANY,
										getTargetsAllowed(),
										BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
							healed.add(possibleTarget);
						}
						return false;
					}
				});
		float healing = this.healingPerWave;
		if ((this.maximumHealingPerWave > 0) && !healed.isEmpty()
				&& ((healing * healed.size()) > this.maximumHealingPerWave)) {
			healing = this.maximumHealingPerWave / healed.size();
		}
		for (final CUnit unit : healed) {
			if (unit.getLife() < unit.getMaximumLife()) {
				unit.heal(simulation, healing);
				simulation.createTemporarySpellEffectOnUnit(unit, this.effectId, CEffectType.SPECIAL);
			}
			if (marks) {
				markWithBuff(simulation, unit);
			}
		}
	}

	private void markWithBuff(final CSimulation simulation, final CUnit unit) {
		if ((this.buffId == null) || War3ID.NONE.equals(this.buffId)) {
			return;
		}
		final CAbility previous = unit.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(this.buffId));
		if (previous instanceof CBuffHealingSpray) {
			unit.remove(simulation, (CBuffHealingSpray) previous);
		}
		unit.add(simulation, new CBuffHealingSpray(simulation.getHandleIdAllocator().createId(), this.buffId,
				this.waveInterval));
	}
}
