package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.booleancallbacks.ABBooleanCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.enumcallbacks.ABAttackTypeCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.enumcallbacks.ABDamageTypeCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.locationcallbacks.ABLocationCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.unitcallbacks.ABUnitCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CGenericDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Shared implementation of the damage actions with a full radius plus reduced-damage radii. Subclasses declare the
 * JSON fields that name the base damage and each reduced radius, and how a reduced value maps to a damage amount.
 */
public abstract class ABActionDamageSplashBase implements ABAction {

	private ABUnitCallback source;
	private ABLocationCallback target;
	private ABFloatCallback fullDamageRadius;
	private ABBooleanCallback validTarget;

	private ABFloatCallback unitSpecificDamageMod;
	private ABFloatCallback maxDamage;

	private ABBooleanCallback isAttack;
	private ABBooleanCallback isRanged;
	private ABAttackTypeCallback attackType;
	private ABDamageTypeCallback damageType;

	private ABBooleanCallback ignoreLTEZero;
	private ABBooleanCallback damageInvulnerable;
	private ABBooleanCallback explodeOnDeath;
	private ABBooleanCallback onlyDamageSummons;
	private ABBooleanCallback nonlethal;

	private List<ABAction> extraActions;

	protected abstract ABFloatCallback baseDamageCallback();

	protected abstract int tierCount();

	protected abstract ABFloatCallback tierRadiusCallback(int tier);

	protected abstract ABFloatCallback tierValueCallback(int tier);

	/** Damage dealt to a unit in the tier when no maximum damage is set. */
	protected abstract float tierDamage(float tierValue, float baseDamage);

	/** Share of the full damage that a unit in the tier receives. */
	protected abstract float tierRatio(float tierValue, float baseDamage);

	@Override
	public void runAction(CSimulation game, CUnit caster, Map<String, Object> localStore, final int castId) {
		CAttackType theAttackType = CAttackType.SPELLS;
		CDamageType theDamageType = CDamageType.MAGIC;
		final CDamageFlags flags = new CGenericDamageFlags(false, true);

		float theDamage = baseDamageCallback().callback(game, caster, localStore, castId);

		if (isAttack != null) {
			flags.setAttack(isAttack.callback(game, caster, localStore, castId));
		}
		if (isRanged != null) {
			flags.setRanged(isRanged.callback(game, caster, localStore, castId));
		}
		if (damageInvulnerable != null) {
			flags.setIgnoreInvulnerable(damageInvulnerable.callback(game, caster, localStore, castId));
		}
		if (explodeOnDeath != null) {
			flags.setExplode(explodeOnDeath.callback(game, caster, localStore, castId));
		}
		if (onlyDamageSummons != null) {
			flags.setOnlyDamageSummons(onlyDamageSummons.callback(game, caster, localStore, castId));
		}
		if (nonlethal != null) {
			flags.setNonlethal(nonlethal.callback(game, caster, localStore, castId));
		}
		if (this.attackType != null) {
			theAttackType = this.attackType.callback(game, caster, localStore, castId);
		}
		if (this.damageType != null) {
			theDamageType = this.damageType.callback(game, caster, localStore, castId);
		}
		if (theDamage > 0 || ignoreLTEZero == null || !ignoreLTEZero.callback(game, caster, localStore, castId)) {
			final AbilityPointTarget loc = target.callback(game, caster, localStore, castId);
			final int tiers = tierCount();
			float pfullRad = 0;
			final float[] tierRad = new float[tiers];
			final float[] tierValue = new float[tiers];
			float theMaxDamage = 0;
			CUnit theSource = caster;
			if (this.fullDamageRadius != null) {
				pfullRad = this.fullDamageRadius.callback(game, caster, localStore, castId);
			}
			for (int i = 0; i < tiers; i++) {
				final ABFloatCallback radius = tierRadiusCallback(i);
				if (radius != null) {
					tierRad[i] = radius.callback(game, caster, localStore, castId);
				}
			}
			for (int i = 0; i < tiers; i++) {
				final ABFloatCallback value = tierValueCallback(i);
				if (value != null) {
					tierValue[i] = value.callback(game, caster, localStore, castId);
				}
			}
			if (this.maxDamage != null) {
				theMaxDamage = this.maxDamage.callback(game, caster, localStore, castId);
			}
			if (this.source != null) {
				theSource = this.source.callback(game, caster, localStore, castId);
			}

			final float fullRad = pfullRad;
			final CAttackType ftheAttackType = theAttackType;
			final CDamageType ftheDamageType = theDamageType;
			final CUnit ftheSource = theSource;
			final float baseDamage = baseDamageCallback().callback(game, caster, localStore, castId);
			float maxRad = pfullRad;
			for (int i = 0; i < tiers; i++) {
				maxRad = Math.max(maxRad, tierRad[i]);
			}

			if (theMaxDamage > 0) {
				final List<CUnit> fullhits = new ArrayList<>();
				final List<List<CUnit>> tierhits = new ArrayList<>();
				final float[] ratio = new float[tiers];
				for (int i = 0; i < tiers; i++) {
					tierhits.add(new ArrayList<>());
					ratio[i] = tierRatio(tierValue[i], baseDamage);
				}
				final List<Float> counts = new ArrayList<>();
				game.getWorldCollision().enumUnitsInRange(loc.getX(), loc.getY(), maxRad, new CUnitEnumFunction() {
					@Override
					public boolean call(final CUnit enumUnit) {
						localStore.put(ABLocalStoreKeys.ENUMUNIT + castId, enumUnit);
						if (validTarget == null || validTarget.callback(game, caster, localStore, castId)) {
							if (enumUnit.canReach(loc, fullRad)) {
								fullhits.add(enumUnit);
								if (unitSpecificDamageMod != null) {
									counts.add(unitSpecificDamageMod.callback(game, caster, localStore, castId));
								} else {
									counts.add(1f);
								}
							} else {
								for (int i = 0; i < tiers; i++) {
									if (enumUnit.canReach(loc, tierRad[i])) {
										tierhits.get(i).add(enumUnit);
										if (unitSpecificDamageMod != null) {
											counts.add(ratio[i]
													* unitSpecificDamageMod.callback(game, caster, localStore, castId));
										} else {
											counts.add(ratio[i]);
										}
										break;
									}
								}
							}
						}
						localStore.remove(ABLocalStoreKeys.ENUMUNIT + castId);
						return false;
					}
				});

				float count = 0;
				for (float c : counts) {
					count += c;
				}
				float hitWeight = fullhits.size();
				for (int i = 0; i < tiers; i++) {
					hitWeight += ratio[i] * tierhits.get(i).size();
				}
				final float damPerTar = baseDamage * count > theMaxDamage ? theMaxDamage / hitWeight : baseDamage;

				for (CUnit hit : fullhits) {
					localStore.put(ABLocalStoreKeys.ENUMUNIT + castId, hit);
					runExtraActions(game, caster, localStore, castId);
					if (unitSpecificDamageMod != null) {
						hit.damage(game, ftheSource, flags, ftheAttackType, ftheDamageType,
								CWeaponSoundTypeJass.WHOKNOWS.name(),
								damPerTar * unitSpecificDamageMod.callback(game, caster, localStore, castId));
					} else {
						hit.damage(game, ftheSource, flags, ftheAttackType, ftheDamageType,
								CWeaponSoundTypeJass.WHOKNOWS.name(), damPerTar);
					}
				}
				for (int i = 0; i < tiers; i++) {
					for (CUnit hit : tierhits.get(i)) {
						localStore.put(ABLocalStoreKeys.ENUMUNIT + castId, hit);
						runExtraActions(game, caster, localStore, castId);
						if (unitSpecificDamageMod != null) {
							hit.damage(game, ftheSource, flags, ftheAttackType, ftheDamageType,
									CWeaponSoundTypeJass.WHOKNOWS.name(), damPerTar * ratio[i]
											* unitSpecificDamageMod.callback(game, caster, localStore, castId));
						} else {
							hit.damage(game, ftheSource, flags, ftheAttackType, ftheDamageType,
									CWeaponSoundTypeJass.WHOKNOWS.name(), damPerTar * ratio[i]);
						}
					}
				}
				localStore.remove(ABLocalStoreKeys.ENUMUNIT + castId);

			} else {
				game.getWorldCollision().enumUnitsInRange(loc.getX(), loc.getY(), maxRad, new CUnitEnumFunction() {
					@Override
					public boolean call(final CUnit enumUnit) {
						localStore.put(ABLocalStoreKeys.ENUMUNIT + castId, enumUnit);
						if (validTarget == null || validTarget.callback(game, caster, localStore, castId)) {
							float amount = 0;
							boolean reached = false;
							if (enumUnit.canReach(loc, fullRad)) {
								amount = baseDamage;
								reached = true;
							} else {
								for (int i = 0; i < tiers; i++) {
									if (enumUnit.canReach(loc, tierRad[i])) {
										amount = tierDamage(tierValue[i], baseDamage);
										reached = true;
										break;
									}
								}
							}
							if (reached) {
								runExtraActions(game, caster, localStore, castId);
								if (unitSpecificDamageMod != null) {
									enumUnit.damage(game, ftheSource, flags, ftheAttackType, ftheDamageType,
											CWeaponSoundTypeJass.WHOKNOWS.name(),
											amount * unitSpecificDamageMod.callback(game, caster, localStore, castId));
								} else {
									enumUnit.damage(game, ftheSource, flags, ftheAttackType, ftheDamageType,
											CWeaponSoundTypeJass.WHOKNOWS.name(), amount);
								}
							}
						}
						localStore.remove(ABLocalStoreKeys.ENUMUNIT + castId);
						return false;
					}
				});
			}
		}
	}

	private void runExtraActions(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId) {
		if (extraActions != null) {
			for (ABAction action : extraActions) {
				action.runAction(game, caster, localStore, castId);
			}
		}
	}
}
