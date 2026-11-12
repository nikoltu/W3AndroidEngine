package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.blademaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CIllusions;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Mirror Image as in the original game: the cast strips the caster's buffs and the caster vanishes; after the
 * animation delay image missiles fly out to spots around it, and where each lands the caster or an illusion appears,
 * the caster at a random one so the real one cannot be told apart. Measured on 1.29: the caster stays hidden for the
 * delay plus its missile's flight (0.5 to 0.7 s); the spots are about 128 from a centre about 80 off in a random
 * direction.
 */
public class CAbilityMirrorImage extends CAbilityNoTargetSpellBase {
	private static final StateModBuff HIDDEN_INVULNERABLE = new StateModBuff(StateModBuffType.INVULNERABLE, 1);
	private static final float SPREAD = 128;
	private static final float CENTRE_OFFSET = 80;
	private static final float DEFAULT_MISSILE_SPEED = 1000;
	private int numberOfImages;
	private float damageDealtFactor;
	private float damageTakenFactor;
	private float animationDelay;
	private War3ID buffId;
	private float missileSpeed;

	private int appearTick;
	private final List<AbilityPointTarget> spots = new ArrayList<>();
	// Tick each spot's missile lands, -1 once its unit appeared; empty until the missiles are launched.
	private final List<Integer> arrivalTicks = new ArrayList<>();
	private boolean casterEventPending;
	private boolean hiding;

	public CAbilityMirrorImage(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.mirrorimage;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.numberOfImages = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.damageDealtFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.damageTakenFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.animationDelay = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
		this.missileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		for (final CAbility ability : new ArrayList<>(caster.getAbilities())) {
			if (ability.getAbilityCategory() == CAbilityCategory.BUFF) {
				caster.remove(simulation, ability);
			}
		}
		this.spots.clear();
		this.arrivalTicks.clear();
		this.casterEventPending = false;
		final int count = this.numberOfImages + 1;
		final double centreAngle = simulation.getSeededRandom().nextDouble() * 2 * Math.PI;
		final double centreX = caster.getX() + (StrictMath.cos(centreAngle) * CENTRE_OFFSET);
		final double centreY = caster.getY() + (StrictMath.sin(centreAngle) * CENTRE_OFFSET);
		final double startAngle = simulation.getSeededRandom().nextDouble() * 2 * Math.PI;
		for (int i = 0; i < count; i++) {
			final double angle = startAngle + ((2 * Math.PI * i) / count);
			this.spots.add(new AbilityPointTarget((float) (centreX + (StrictMath.cos(angle) * SPREAD)),
					(float) (centreY + (StrictMath.sin(angle) * SPREAD))));
		}
		Collections.shuffle(this.spots, simulation.getSeededRandom());
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.SPECIAL);
		caster.setHidden(true);
		this.hiding = true;
		caster.addStateModBuff(HIDDEN_INVULNERABLE);
		caster.computeUnitState(simulation, StateModBuffType.INVULNERABLE);
		this.appearTick = simulation.getGameTurnTick()
				+ CTimer.ceilSteps(this.animationDelay);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if (tick < this.appearTick) {
			return true;
		}
		if (this.arrivalTicks.isEmpty()) {
			final float speed = this.missileSpeed > 0 ? this.missileSpeed : DEFAULT_MISSILE_SPEED;
			for (int i = 0; i < this.spots.size(); i++) {
				// Measured on 1.29: a missile flies to where the unit will stand, not to a blocked spot.
				final float[] free = caster.findFreePoint(this.spots.get(i).getX(), this.spots.get(i).getY(),
						simulation);
				final AbilityPointTarget spot = new AbilityPointTarget(free[0], free[1]);
				this.spots.set(i, spot);
				final float distance = (float) StrictMath.hypot(spot.getX() - caster.getX(), spot.getY() - caster.getY());
				this.arrivalTicks.add(tick
						+ CTimer.ceilSteps(distance / speed));
				simulation.createProjectile(caster, getAlias(), caster.getX(), caster.getY(), caster.getFacing(),
						speed, false, spot, CAbilityProjectileListener.DO_NOTHING);
			}
		}
		boolean pending = false;
		for (int i = 0; i < this.spots.size(); i++) {
			final int arrival = this.arrivalTicks.get(i);
			if (arrival < 0) {
				continue;
			}
			if (tick < arrival) {
				pending = true;
				continue;
			}
			this.arrivalTicks.set(i, -1);
			final AbilityPointTarget spot = this.spots.get(i);
			if (i == 0) {
				caster.setPointAndCheckUnstuck(spot.getX(), spot.getY(), simulation);
				reveal(simulation, caster);
				simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.TARGET);
				this.casterEventPending = true;
			}
			else {
				final CUnit image = CIllusions.create(simulation, caster, caster.getPlayerIndex(), spot.getX(),
						spot.getY(), caster.getFacing(), this.damageDealtFactor, this.damageTakenFactor,
						getDuration(), this.buffId);
				if (image != null) {
					simulation.createTemporarySpellEffectOnUnit(image, getAlias(), CEffectType.TARGET);
					simulation.unitSummoned(caster, image);
				}
			}
		}
		// Measured on 1.29: the caster's own reappearance raises a summon event too, with no summoned unit, after
		// the images' events.
		if (this.casterEventPending && !pending) {
			this.casterEventPending = false;
			simulation.unitSummoned(caster, null);
		}
		return pending;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		reveal(game, unit);
	}

	/**
	 * Measured on 1.29: while Mirror Image hides the caster he takes no order (move, attack and spells are refused,
	 * stop is accepted but does nothing), and the images still appear.
	 */
	public static boolean isHidingCaster(final CUnit unit) {
		if (!unit.isHidden()) {
			return false;
		}
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CAbilityMirrorImage) && ((CAbilityMirrorImage) ability).hiding) {
				return true;
			}
		}
		return false;
	}

	private void reveal(final CSimulation simulation, final CUnit caster) {
		this.hiding = false;
		if (caster.isHidden()) {
			caster.setHidden(false);
			caster.removeStateModBuff(HIDDEN_INVULNERABLE);
			caster.computeUnitState(simulation, StateModBuffType.INVULNERABLE);
		}
	}
}
