package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.buff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.parsers.jass.JassTextGenerator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.booleancallbacks.ABBooleanCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.idcallbacks.ABIDCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.statbuffcallbacks.ABNonStackingStatBuffCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.statemodcallbacks.ABStateModBuffCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.stringcallbacks.ABStringCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;

/**
 * Shared implementation of the createTimed*Buff actions. Subclasses declare their own uniqueValues field (its key
 * type differs between the JSON variants) and create the concrete buff.
 */
public abstract class ABActionCreateTimedBuffBase implements ABAction {

	protected ABIDCallback buffId;
	protected ABFloatCallback duration;
	protected ABBooleanCallback showTimedLifeBar;
	protected List<ABAction> onAddActions;
	protected List<ABAction> onRemoveActions;
	protected List<ABAction> onExpireActions;

	private List<ABNonStackingStatBuffCallback> statBuffs;
	private List<ABStateModBuffCallback> stateMods;

	protected ABBooleanCallback showIcon;
	protected CEffectType artType;
	protected ABBooleanCallback hideArt;

	private ABBooleanCallback leveled;
	private ABBooleanCallback positive;
	private ABBooleanCallback dispellable;
	private ABBooleanCallback magic;
	private ABBooleanCallback physical;

	private ABBooleanCallback stacks;
	private ABStringCallback visibilityGroup;

	private List<ABStringCallback> uniqueFlags;

	protected abstract ABTimedBuff createBuff(CSimulation game, CUnit caster, Map<String, Object> localStore,
			int castId, boolean showTimedLife, boolean isLeveled, boolean isPositive, boolean isDispellable);

	protected abstract void addUniqueValues(CSimulation game, CUnit caster, Map<String, Object> localStore,
			int castId, ABTimedBuff buff);

	protected static void addUniqueValues(final Map<String, ABCallback> values, final CSimulation game,
			final CUnit caster, final Map<String, Object> localStore, final int castId, final ABTimedBuff buff) {
		if (values != null) {
			for (final String key : values.keySet()) {
				buff.addUniqueValue(values.get(key).callback(game, caster, localStore, castId), key);
			}
		}
	}

	/** Applies the optional showIcon flag; evaluated after the other constructor arguments, as before. */
	protected final ABTimedBuff applyShowIcon(final ABTimedBuff buff, final CSimulation game, final CUnit caster,
			final Map<String, Object> localStore, final int castId) {
		if (showIcon != null) {
			buff.setIconShowing(showIcon.callback(game, caster, localStore, castId));
		}
		return buff;
	}

	@Override
	public void runAction(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId) {
		boolean showTimedLife = false;
		if (this.showTimedLifeBar != null) {
			showTimedLife = this.showTimedLifeBar.callback(game, caster, localStore, castId);
		}
		boolean isLeveled = false;
		if (leveled != null) {
			isLeveled = leveled.callback(game, caster, localStore, castId);
		} else {
			isLeveled = (boolean) localStore.getOrDefault(ABLocalStoreKeys.ISABILITYLEVELED, false);
		}
		boolean isPositive = true;
		if (positive != null) {
			isPositive = positive.callback(game, caster, localStore, castId);
		}
		boolean isDispellable = true;
		if (dispellable != null) {
			isDispellable = dispellable.callback(game, caster, localStore, castId);
		} else {
			isDispellable = ((boolean) localStore.getOrDefault(ABLocalStoreKeys.ISABILITYMAGIC, true));
		}

		boolean isMagic = ((boolean) localStore.getOrDefault(ABLocalStoreKeys.ISABILITYMAGIC, true));
		boolean isPhysical = ((boolean) localStore.getOrDefault(ABLocalStoreKeys.ISABILITYPHYSICAL, false));
		if (magic != null) {
			isMagic = magic.callback(game, caster, localStore, castId);
		}
		if (physical != null) {
			isPhysical = physical.callback(game, caster, localStore, castId);
		}

		final ABTimedBuff ability = createBuff(game, caster, localStore, castId, showTimedLife, isLeveled, isPositive,
				isDispellable);

		if (stateMods != null) {
			final List<StateModBuff> buffMods = new ArrayList<>();
			for (final ABStateModBuffCallback mod : stateMods) {
				buffMods.add(mod.callback(game, caster, localStore, castId));
			}
			ability.setStateMods(buffMods);
		}
		if (statBuffs != null) {
			final List<NonStackingStatBuff> buffStats = new ArrayList<>();
			for (final ABNonStackingStatBuffCallback mod : statBuffs) {
				buffStats.add(mod.callback(game, caster, localStore, castId));
			}
			ability.setStatBuffs(buffStats);
		}

		if (artType != null) {
			ability.setArtType(artType);
		}
		if ((this.hideArt != null) && this.hideArt.callback(game, caster, localStore, castId)) {
			ability.setArtType(null);
		}
		ability.setMagic(isMagic);
		ability.setPhysical(isPhysical);
		boolean isStacks = false;
		if (stacks != null) {
			isStacks = stacks.callback(game, caster, localStore, castId);
		}
		ability.setStacks(isStacks);
		if (visibilityGroup != null) {
			ability.setVisibilityGroup(visibilityGroup.callback(game, caster, localStore, castId));
		}

		localStore.put(ABLocalStoreKeys.LASTCREATEDBUFF, ability);
		if (uniqueFlags != null) {
			for (final ABStringCallback flag : uniqueFlags) {
				ability.addUniqueFlag(flag.callback(game, caster, localStore, castId));
			}
		}
		addUniqueValues(game, caster, localStore, castId, ability);
		if (!localStore.containsKey(ABLocalStoreKeys.BUFFCASTINGUNIT)) {
			localStore.put(ABLocalStoreKeys.BUFFCASTINGUNIT, caster);
		}
	}

	protected final String generateJass(final JassTextGenerator jassTextGenerator, final String name,
			final List<ABAction> onTickActions, final boolean withTick) {
		final String addFunctionName = jassTextGenerator.createAnonymousFunction(this.onAddActions,
				name + "_OnAddActions");
		final String removeFunctionName = jassTextGenerator.createAnonymousFunction(this.onRemoveActions,
				name + "_OnRemoveActions");
		final String expireFunctionName = jassTextGenerator.createAnonymousFunction(this.onExpireActions,
				name + "_OnExpireActions");
		String tickFunctionName = null;
		if (withTick) {
			tickFunctionName = jassTextGenerator.createAnonymousFunction(onTickActions, name + "_OnTickActions");
		}

		String showTimedLife = "false";
		if (this.showTimedLifeBar != null) {
			showTimedLife = this.showTimedLifeBar.generateJassEquivalent(jassTextGenerator);
		}

		String showIconExpression;
		if (this.showIcon != null) {
			showIconExpression = this.showIcon.generateJassEquivalent(jassTextGenerator);
		} else {
			showIconExpression = "true";
		}

		CEffectType artTypeUsed = CEffectType.TARGET;
		if (this.artType != null) {
			artTypeUsed = this.artType;
		}
		String artTypeExpression;
		if (this.hideArt != null) {
			artTypeExpression = "null";
		} else {
			artTypeExpression = "EFFECT_TYPE_" + artTypeUsed.name();
		}

		return name + "(" + jassTextGenerator.getCaster() + ", " + jassTextGenerator.getTriggerLocalStore() + ", "
				+ this.buffId.generateJassEquivalent(jassTextGenerator) + ", "
				+ this.duration.generateJassEquivalent(jassTextGenerator) + ", " + showTimedLife + ", "
				+ jassTextGenerator.functionPointerByName(addFunctionName) + ", "
				+ jassTextGenerator.functionPointerByName(removeFunctionName) + ", "
				+ jassTextGenerator.functionPointerByName(expireFunctionName) + ", "
				+ (withTick ? jassTextGenerator.functionPointerByName(tickFunctionName) + ", " : "")
				+ showIconExpression + ", " + artTypeExpression
				+ (withTick ? ", " + jassTextGenerator.getCastId() : "") + ")";
	}
}
