package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import java.util.EnumSet;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.listeners.CUnitSpellEffectListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;

/**
 * Mana Flare: the Faerie Dragon channels in place; while it does, every enemy within the area that casts a spell is
 * struck for damage based on the mana cost of that spell, and the armor of the dragon is raised.
 */
public class CAbilityManaFlare extends CAbilityNoTargetSpellBase implements CUnitSpellEffectListener {
	private static final float LIGHTNING_DURATION = 0.5f;

	private float unitDamagePerMana;
	private float heroDamagePerMana;
	private float unitMaximumDamage;
	private float heroMaximumDamage;
	private float armorBonus;
	private float areaOfEffect;
	private War3ID casterBuffId;
	private War3ID targetBuffId;

	private boolean channeling;
	private int flareStartTick;
	private int flareEndTick;
	private CUnit caster;
	private CBuffManaFlare buff;
	private NonStackingStatBuff armorBuff;

	public CAbilityManaFlare(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return this.channeling ? OrderIds.manaflareoff : OrderIds.manaflareon;
	}

	@Override
	public boolean isToggleOn() {
		return this.channeling;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.unitDamagePerMana = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.heroDamagePerMana = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.unitMaximumDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.heroMaximumDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.armorBonus = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.casterBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		final War3ID targetBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
		this.targetBuffId = War3ID.NONE.equals(targetBuffId) ? this.casterBuffId : targetBuffId;
		setCastingPrimaryTag(PrimaryTag.STAND);
		setCastingSecondaryTags(EnumSet.of(SecondaryTag.CHANNEL));
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (this.channeling && (orderId == OrderIds.manaflareoff)) {
			caster.order(game, OrderIds.stop, null);
			return false;
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final AbilityActivationReceiver receiver) {
		if (this.channeling && (orderId == OrderIds.manaflareoff)) {
			receiver.useOk();
			return;
		}
		super.innerCheckCanUse(game, unit, playerIndex, orderId, receiver);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.channeling = true;
		this.caster = caster;
		this.flareStartTick = simulation.getGameTurnTick()
				+ (int) (getCastingTime() / WarsmashConstants.SIMULATION_STEP_TIME);
		this.flareEndTick = this.flareStartTick + (int) (getDuration() / WarsmashConstants.SIMULATION_STEP_TIME);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if ((this.buff == null) && (tick >= this.flareStartTick)) {
			this.buff = new CBuffManaFlare(simulation.getHandleIdAllocator().createId(), this.casterBuffId,
					getDuration());
			caster.add(simulation, this.buff);
			this.armorBuff = new NonStackingStatBuff(NonStackingStatBuffType.DEF, getAlias().asStringValue(),
					this.armorBonus);
			caster.addNonStackingStatBuff(simulation, this.armorBuff);
			simulation.addSpellEffectListener(this);
		}
		return tick < this.flareEndTick;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		this.channeling = false;
		if (this.buff != null) {
			game.removeSpellEffectListener(this);
			unit.removeNonStackingStatBuff(game, this.armorBuff);
			unit.remove(game, this.buff);
			this.buff = null;
			this.armorBuff = null;
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
		doChannelEnd(game, cUnit, null, true);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		doChannelEnd(game, unit, null, true);
	}

	@Override
	public void onSpellEffect(final CSimulation simulation, final CUnit spellCaster, final CAbility ability) {
		if ((this.caster == null) || (spellCaster == this.caster) || spellCaster.isDead()
				|| !(ability instanceof GenericSingleIconActiveAbility)) {
			return;
		}
		final int manaCost = ((GenericSingleIconActiveAbility) ability).getUIManaCost();
		if ((manaCost <= 0) || !this.caster.canReach(spellCaster, this.areaOfEffect)
				|| !spellCaster.canBeTargetedBy(simulation, this.caster, getTargetsAllowed())) {
			return;
		}
		final boolean hero = spellCaster.isHero();
		final float damage = Math.min(manaCost * (hero ? this.heroDamagePerMana : this.unitDamagePerMana),
				hero ? this.heroMaximumDamage : this.unitMaximumDamage);
		simulation.createAbilityLightning(this.caster, this.casterBuffId, 0, spellCaster, LIGHTNING_DURATION);
		simulation.createTemporarySpellEffectOnUnit(spellCaster, this.targetBuffId, CEffectType.SPECIAL);
		spellCaster.damage(simulation, this.caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.MAGIC,
				CWeaponSoundTypeJass.WHOKNOWS.name(), damage);
	}
}
