package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.blademaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Mirror Image as in the original game: the cast strips the caster's buffs, the caster vanishes for the animation
 * delay while image missiles fly out, then it reappears at a random one of the spots with illusions at the others,
 * so the real one cannot be told apart.
 */
public class CAbilityMirrorImage extends CAbilityNoTargetSpellBase {
	private static final float SPREAD = 150;
	private int numberOfImages;
	private float damageDealtFactor;
	private float damageTakenFactor;
	private float animationDelay;
	private War3ID buffId;

	private int appearTick;
	private final List<AbilityPointTarget> spots = new ArrayList<>();

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
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		for (final CAbility ability : new ArrayList<>(caster.getAbilities())) {
			if (ability.getAbilityCategory() == CAbilityCategory.BUFF) {
				caster.remove(simulation, ability);
			}
		}
		this.spots.clear();
		final int count = this.numberOfImages + 1;
		final double startAngle = simulation.getSeededRandom().nextDouble() * 2 * Math.PI;
		for (int i = 0; i < count; i++) {
			final double angle = startAngle + ((2 * Math.PI * i) / count);
			this.spots.add(new AbilityPointTarget(caster.getX() + (float) (Math.cos(angle) * SPREAD),
					caster.getY() + (float) (Math.sin(angle) * SPREAD)));
		}
		Collections.shuffle(this.spots, simulation.getSeededRandom());
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.SPECIAL);
		for (final AbilityPointTarget spot : this.spots) {
			simulation.createProjectile(caster, getAlias(), caster.getX(), caster.getY(), caster.getFacing(), null,
					false, spot, CAbilityProjectileListener.DO_NOTHING);
		}
		caster.setHidden(true);
		caster.setInvulnerable(true);
		this.appearTick = simulation.getGameTurnTick()
				+ (int) StrictMath.ceil(this.animationDelay / WarsmashConstants.SIMULATION_STEP_TIME);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		if (simulation.getGameTurnTick() < this.appearTick) {
			return true;
		}
		final AbilityPointTarget casterSpot = this.spots.get(0);
		caster.setPointAndCheckUnstuck(casterSpot.getX(), casterSpot.getY(), simulation);
		reveal(caster);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.TARGET);
		for (int i = 1; i < this.spots.size(); i++) {
			final AbilityPointTarget spot = this.spots.get(i);
			final CUnit image = CIllusions.create(simulation, caster, caster.getPlayerIndex(), spot.getX(),
					spot.getY(), caster.getFacing(), this.damageDealtFactor, this.damageTakenFactor, getDuration(),
					this.buffId);
			if (image != null) {
				simulation.createTemporarySpellEffectOnUnit(image, getAlias(), CEffectType.TARGET);
			}
		}
		return false;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		reveal(unit);
	}

	private static void reveal(final CUnit caster) {
		if (caster.isHidden()) {
			caster.setHidden(false);
			caster.setInvulnerable(false);
		}
	}
}
