package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.buff;

import java.util.List;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.util.War3ID;

/** Timed buff actions whose buff also runs tick actions; subclasses pick the buff class. */
public abstract class ABActionCreateTimedTickingBuffBase extends ABActionCreateTimedBuffBase {

	protected List<ABAction> onTickActions;

	protected interface TickingBuffConstructor {
		ABTimedBuff create(int handleId, War3ID alias, Map<String, Object> localStore, CAbility sourceAbility,
				CUnit sourceUnit, float duration, boolean showTimedLifeBar, List<ABAction> onAddActions,
				List<ABAction> onRemoveActions, List<ABAction> onExpireActions, List<ABAction> onTickActions,
				int castId, boolean leveled, boolean positive, boolean dispellable);
	}

	protected abstract TickingBuffConstructor buffConstructor();

	@Override
	protected final ABTimedBuff createBuff(final CSimulation game, final CUnit caster,
			final Map<String, Object> localStore, final int castId, final boolean showTimedLife,
			final boolean isLeveled, final boolean isPositive, final boolean isDispellable) {
		return applyShowIcon(buffConstructor().create(game.getHandleIdAllocator().createId(),
				buffId.callback(game, caster, localStore, castId), localStore,
				(CAbility) localStore.get(ABLocalStoreKeys.ABILITY), caster,
				duration.callback(game, caster, localStore, castId), showTimedLife, onAddActions, onRemoveActions,
				onExpireActions, onTickActions, castId, isLeveled, isPositive, isDispellable), game, caster,
				localStore, castId);
	}

	protected final String generateJass(final com.etheller.warsmash.parsers.jass.JassTextGenerator jassTextGenerator,
			final String name) {
		return generateJass(jassTextGenerator, name, onTickActions, true);
	}
}
