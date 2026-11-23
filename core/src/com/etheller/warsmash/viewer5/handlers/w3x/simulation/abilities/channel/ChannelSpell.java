package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * The fields of the Channel ability and what it does once cast, shared by its four target-type variants. The spell
 * itself only runs the cast events (a map's triggers do the work) and keeps the caster channeling for the follow
 * through time, showing the caster art every art duration.
 */
public final class ChannelSpell {
	public static final int TARGET_NONE = 0;
	public static final int TARGET_UNIT = 1;
	public static final int TARGET_POINT = 2;

	private static final int OPTION_VISIBLE = 1;
	private static final int OPTION_TARGET_IMAGE = 2;
	private static final int OPTION_PHYSICAL = 4;
	private static final int OPTION_UNIVERSAL = 8;
	private static final int OPTION_UNIQUE_CAST = 16;

	private float followThroughTime;
	private int options;
	private float artDuration;
	private boolean disableOtherAbilities;
	private float areaOfEffect;
	private boolean othersDisabled;
	private int baseOrderId = OrderIds.channel;
	private int channelEndTick;
	private int nextArtTick;

	/** The target type the ability's data sets: none, a unit, a point, or either of the two. */
	public static int targetType(final GameObject abilityData) {
		return abilityData.getFieldAsInteger(AbilityFields.DATA_B + 1, 0);
	}

	public void populate(final GameObject abilityData, final int level) {
		this.followThroughTime = abilityData.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.options = abilityData.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
		this.artDuration = abilityData.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.disableOtherAbilities = abilityData.getFieldAsInteger(AbilityFields.DATA_E + level, 0) != 0;
		this.areaOfEffect = abilityData.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		int orderId = OrderIdUtils.getOrderId(abilityData.getField(AbilityFields.DATA_F + level));
		if (orderId == 0) {
			orderId = OrderIdUtils.getOrderId(abilityData.getField("Order"));
		}
		this.baseOrderId = orderId == 0 ? OrderIds.channel : orderId;
	}

	public int getBaseOrderId() {
		return this.baseOrderId;
	}

	/** Whether the ability has a button on the command card. */
	public boolean isVisible() {
		return (this.options & OPTION_VISIBLE) != 0;
	}

	/** Only one unit of a selected group casts it. */
	public boolean isUniqueCast() {
		return (this.options & OPTION_UNIQUE_CAST) != 0;
	}

	/** The radius of the area the cursor shows while a point is targeted, or NaN for none. */
	public float getTargetImageArea() {
		return (((this.options & OPTION_TARGET_IMAGE) != 0) && (this.areaOfEffect > 0)) ? this.areaOfEffect
				: Float.NaN;
	}

	public boolean isPhysical() {
		return (this.options & OPTION_PHYSICAL) != 0;
	}

	public boolean isUniversal() {
		return (this.options & OPTION_UNIVERSAL) != 0;
	}

	/** At the cast's effect: whether the caster keeps channeling. */
	public boolean begin(final CSimulation simulation, final CUnit caster, final CAbility self) {
		this.channelEndTick = simulation.getGameTurnTick() + CTimer.wholeSteps(this.followThroughTime);
		this.nextArtTick = simulation.getGameTurnTick();
		final boolean channeling = this.followThroughTime > 0;
		if (channeling && this.disableOtherAbilities) {
			setOthersDisabled(caster, self, true);
		}
		return channeling;
	}

	/** When the channel ends or is interrupted. */
	public void end(final CUnit caster, final CAbility self) {
		if (this.othersDisabled) {
			setOthersDisabled(caster, self, false);
		}
	}

	// Passive abilities such as auras keep working (measured); the abilities that can be ordered do not.
	private void setOthersDisabled(final CUnit caster, final CAbility self, final boolean disabled) {
		this.othersDisabled = disabled;
		for (final CAbility ability : caster.getAbilities()) {
			if ((ability != self) && (ability.getAbilityCategory() == CAbilityCategory.SPELL)) {
				ability.setDisabled(disabled, CAbilityDisableType.CHANNEL);
			}
		}
	}

	/** Every step of the channel: whether it goes on. */
	public boolean tick(final CSimulation simulation, final CUnit caster, final War3ID alias) {
		final int tick = simulation.getGameTurnTick();
		if (tick >= this.channelEndTick) {
			return false;
		}
		if ((this.artDuration > 0) && (tick >= this.nextArtTick)) {
			simulation.createTemporarySpellEffectOnUnit(caster, alias, CEffectType.CASTER);
			this.nextArtTick = tick + CTimer.wholeSteps(this.artDuration);
		}
		return true;
	}
}
